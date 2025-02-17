package com.example.daterangeexporter.calendarExport

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daterangeexporter.calendarExport.models.CalendarMonthYear
import com.example.daterangeexporter.calendarExport.models.CalendarSelectedDate
import com.example.daterangeexporter.calendarExport.models.RangeSelectionLabel
import com.example.daterangeexporter.calendarExport.utils.interfaces.CalendarExportUtils
import com.example.daterangeexporter.core.application.contentProviders.interfaces.AppFileProviderHandler
import com.example.daterangeexporter.core.domain.repositories.CalendarsRepository
import com.example.daterangeexporter.core.domain.utils.onError
import com.example.daterangeexporter.core.domain.utils.onSuccess
import com.example.daterangeexporter.core.presentation.utils.toUiMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

class CalendarExportViewModel(
    private val calendar: Calendar,
    private val appContext: Context,
    private val calendarsRepository: CalendarsRepository,
    private val calendarExportUtils: CalendarExportUtils,
    private val appFileProviderHandler: AppFileProviderHandler,
) : ViewModel() {
    private val _uiEvents = Channel<UiEvents>()
    val uiEvents = _uiEvents.receiveAsFlow()

    val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val initialCalendar = CalendarMonthYear.fromCalendar(calendar)

    private val _rangeSelectionCount = MutableStateFlow(RangeSelectionLabel.First.count)
    val rangeSelectionCount = _rangeSelectionCount.asStateFlow()

    private val _selectedDates =
        MutableStateFlow<ImmutableMap<CalendarMonthYear, ImmutableList<CalendarSelectedDate>>>(
            persistentMapOf()
        )
    val selectedDates = _selectedDates.asStateFlow()

    private val _calendarLabelInput = MutableStateFlow<String?>(null)
    val calendarLabelInput = _calendarLabelInput.asStateFlow()

    private val _calendarsBitmaps = MutableStateFlow<ImmutableMap<CalendarMonthYear, Bitmap?>>(
        persistentMapOf()
    )
    val calendarsBitmaps = _calendarsBitmaps.asStateFlow()

    fun onDateRangeSelected(
        startDateTimeMillis: Long,
        endDateTimeMillis: Long,
    ) {
        _selectedDates.update {
            calendarExportUtils.getSelectedDates(
                startDateTimeMillis = startDateTimeMillis,
                endDateTimeMillis = endDateTimeMillis,
                currentRangeCount = rangeSelectionCount.value,
                currentSelectedDates = selectedDates.value,
            )
        }

        _rangeSelectionCount.update { it + 1 }
    }

    fun onClearDateRangeSelection() {
        _rangeSelectionCount.update { RangeSelectionLabel.First.count }
        _selectedDates.update { persistentMapOf() }
        _calendarLabelInput.update { null }
    }

    fun onCalendarLabelAssign(label: String) {
        _calendarLabelInput.update { label }
    }

    fun onStartCalendarsExport() {
        _calendarsBitmaps.update {
            _selectedDates.value
                .mapValues { null }
                .toImmutableMap()
        }

        viewModelScope.launch {
            _calendarsBitmaps
                .filter { it.isNotEmpty() && it.values.any { bitmap -> bitmap != null } }
                .collect { checkMissingCalendarsBitmaps() }
        }
    }

    private suspend fun checkMissingCalendarsBitmaps() {
        val isThereAnyCalendarBitmapMissing = calendarsBitmaps.value.values.any { it == null }

        if (isThereAnyCalendarBitmapMissing) {
            val firstMissingCalendarIndex =
                selectedDates.value.keys.indexOfFirst { calendar ->
                    val firstMissingBitmapCalendar =
                        calendarsBitmaps.value.entries.find { (_, a) -> a == null }?.key
                    calendar == firstMissingBitmapCalendar
                }

            delay(150.milliseconds)
            _uiEvents.send(
                UiEvents.MissingCalendarBitmap(firstMissingBitmapIndex = firstMissingCalendarIndex),
            )
        } else {
            saveCalendarsBitmaps()
        }
    }

    private fun saveCalendarsBitmaps() {
        viewModelScope.launch {
            calendarsRepository.clearCacheDir()
                .onError { error ->
                    _uiEvents.send(UiEvents.DataSourceError(messageId = error.toUiMessage()))
                    return@launch
                }

            val contentUris = arrayListOf<Uri>()

            _calendarsBitmaps.value.forEach { (calendarMonthYear, calendarBitmap) ->
                val currentTimestamp = calendar.timeInMillis
                val monthYearString = "${calendarMonthYear.month}${calendarMonthYear.year}"

                calendarsRepository.saveCalendarBitmap(
                    bitmap = calendarBitmap!!,
                    fileName = "calendar-$monthYearString-$currentTimestamp.png",
                    parentFolder = appContext.cacheDir,
                )
                    .onError { error ->
                        _calendarsBitmaps.update { persistentMapOf() }

                        _uiEvents.send(UiEvents.DataSourceError(messageId = error.toUiMessage()))
                        return@launch
                    }
                    .onSuccess { file ->
                        val contentUri = appFileProviderHandler.getUriForInternalAppFile(file)
                        contentUris.add(contentUri)
                    }
            }

            _calendarsBitmaps.update { persistentMapOf() }

            _uiEvents.send(
                UiEvents.SaveCalendarsBitmapsSuccess(calendarsContentUris = contentUris),
            )
        }
    }

    fun onConvertedCalendarToBitmap(
        calendarMonthYear: CalendarMonthYear,
        bitmap: Bitmap,
    ) {
        _calendarsBitmaps.update {
            it
                .toMutableMap()
                .apply { put(calendarMonthYear, bitmap) }
                .toImmutableMap()
        }
    }

    sealed interface UiEvents {
        data class DataSourceError(@StringRes val messageId: Int) : UiEvents

        data class SaveCalendarsBitmapsSuccess(val calendarsContentUris: ArrayList<Uri>) : UiEvents

        data class MissingCalendarBitmap(val firstMissingBitmapIndex: Int) : UiEvents
    }
}
