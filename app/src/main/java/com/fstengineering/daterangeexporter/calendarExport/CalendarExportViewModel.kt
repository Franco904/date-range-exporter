package com.fstengineering.daterangeexporter.calendarExport

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fstengineering.daterangeexporter.calendarExport.models.CalendarFormUiState
import com.fstengineering.daterangeexporter.calendarExport.models.CalendarMonthYear
import com.fstengineering.daterangeexporter.calendarExport.models.RangeSelectionLabel
import com.fstengineering.daterangeexporter.calendarExport.utils.interfaces.CalendarExportUtils
import com.fstengineering.daterangeexporter.calendarExport.utils.interfaces.ImmutableSelectedDates
import com.fstengineering.daterangeexporter.core.application.contentProviders.interfaces.AppFileProviderHandler
import com.fstengineering.daterangeexporter.core.domain.repositories.CalendarsRepository
import com.fstengineering.daterangeexporter.core.domain.utils.DataSourceError
import com.fstengineering.daterangeexporter.core.domain.utils.fold
import com.fstengineering.daterangeexporter.core.domain.utils.onError
import com.fstengineering.daterangeexporter.core.domain.validators.interfaces.CalendarsValidator
import com.fstengineering.daterangeexporter.core.presentation.utils.uiConverters.toUiMessage
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

class CalendarExportViewModel(
    private val calendar: Calendar,
    private val appContext: Context,
    private val calendarsRepository: CalendarsRepository,
    private val calendarsValidator: CalendarsValidator,
    private val calendarExportUtils: CalendarExportUtils,
    private val appFileProviderHandler: AppFileProviderHandler,
) : ViewModel() {
    private val _uiEvents = Channel<UiEvents>()
    val uiEvents = _uiEvents.receiveAsFlow()

    val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val initialCalendar = CalendarMonthYear.fromCalendar(calendar)

    private val _rangeSelectionCount = MutableStateFlow(RangeSelectionLabel.First.count)
    val rangeSelectionCount = _rangeSelectionCount.asStateFlow()

    private val _selectedDates = MutableStateFlow<ImmutableSelectedDates>(persistentMapOf())
    val selectedDates = _selectedDates.asStateFlow()

    private val _calendarFormUiState = MutableStateFlow(CalendarFormUiState())
    val calendarFormUiState = _calendarFormUiState.asStateFlow()

    private val _calendarsBitmaps = MutableStateFlow<ImmutableMap<CalendarMonthYear, Bitmap?>>(
        persistentMapOf()
    )
    val calendarsBitmaps = _calendarsBitmaps.asStateFlow()

    fun onDateRangeSelected(
        startDateTimeMillis: Long,
        endDateTimeMillis: Long,
    ) {
        _selectedDates.update {
            calendarExportUtils.getNewSelectedDates(
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
        _calendarFormUiState.update { CalendarFormUiState() }
    }

    fun onCalendarLabelChange() {
        _calendarFormUiState.update {
            it.copy(labelError = null)
        }
    }

    fun onCalendarLabelInputCancel() {
        _calendarFormUiState.update {
            it.copy(labelError = null)
        }
    }

    fun onCalendarLabelAssign(label: String?) {
        calendarsValidator.validateLabel(label = label)
            .onError { error ->
                _calendarFormUiState.update { it.copy(labelError = error.toUiMessage()) }
                return
            }

        _calendarFormUiState.update {
            it.copy(label = label)
        }

        _uiEvents.trySend(UiEvents.CalendarLabelAssigned)
    }

    fun onStartCalendarsExport() {
        _calendarsBitmaps.update {
            _selectedDates.value
                .mapValues { null }
                .toImmutableMap()
        }

        viewModelScope.launch {
            _calendarsBitmaps
                .takeWhile { it.isNotEmpty() }
                .collect { checkMissingCalendarsBitmaps() }
        }
    }

    private suspend fun checkMissingCalendarsBitmaps() {
        val firstMissingBitmapCalendar =
            calendarsBitmaps.value.entries.find { (_, a) -> a == null }?.key

        if (firstMissingBitmapCalendar != null) {
            val firstMissingCalendarIndex =
                selectedDates.value.keys.indexOfFirst { calendar -> calendar == firstMissingBitmapCalendar }

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
                    _uiEvents.send(UiEvents.DataSourceErrorEvent(error = error))
                    return@launch
                }

            val contentUris = _calendarsBitmaps.value.map { (calendarMonthYear, calendarBitmap) ->
                val uri = saveCalendarBitmap(
                    calendarMonthYear = calendarMonthYear,
                    calendarBitmap = calendarBitmap,
                )

                uri ?: return@launch
            }

            _uiEvents.send(
                UiEvents.SaveCalendarsBitmapsSuccess(
                    calendarsContentUris = arrayListOf<Uri>().apply { addAll(contentUris) },
                ),
            )

            _calendarsBitmaps.update { persistentMapOf() }
        }
    }

    private suspend fun saveCalendarBitmap(
        calendarMonthYear: CalendarMonthYear,
        calendarBitmap: Bitmap?,
    ): Uri? {
        val currentTimestamp = calendar.timeInMillis
        val monthYearString = "${calendarMonthYear.month}${calendarMonthYear.year}"

        calendarsRepository.saveCalendarBitmap(
            bitmap = calendarBitmap!!,
            fileName = "calendar-$monthYearString-$currentTimestamp.png",
            parentFolder = appContext.cacheDir,
        )
            .fold(
                onError = { error ->
                    _calendarsBitmaps.update { persistentMapOf() }

                    _uiEvents.send(UiEvents.DataSourceErrorEvent(error = error))
                    return null
                },
                onSuccess = { file ->
                    val contentUri = appFileProviderHandler.getUriForInternalAppFile(file)
                    return contentUri
                }
            )
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

    fun getDeviceFreeStoragePercent(): Int {
        val freeSpaceLeft = calendarsRepository.getDeviceFreeStorageBytes()
        val totalSpace = calendarsRepository.getDeviceTotalStorageBytes()

        return (freeSpaceLeft.toDouble() / totalSpace.toDouble() * 100).toInt()
    }

    sealed interface UiEvents {
        data class DataSourceErrorEvent(val error: DataSourceError) : UiEvents

        data object CalendarLabelAssigned : UiEvents

        data class SaveCalendarsBitmapsSuccess(val calendarsContentUris: ArrayList<Uri>) : UiEvents

        data class MissingCalendarBitmap(val firstMissingBitmapIndex: Int) : UiEvents
    }
}
