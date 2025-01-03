package com.example.daterangeexporter.calendarExport.localComposables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.daterangeexporter.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarExportTopBar(
    onUpNavigation: () -> Boolean,
    onEditCalendar: () -> Unit,
    onClearSelectedDates: () -> Unit,
    onLabelAssign: () -> Unit,
    isSelectedDatesEmpty: Boolean,
    calendarHasLabelAssigned: Boolean,
    modifier: Modifier = Modifier,
) {
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    var isMenuDropDownVisible by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.export_calendar_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        },
        navigationIcon = {
            IconButton(onClick = { onUpNavigation() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            Column {
                IconButton(
                    onClick = { isMenuDropDownVisible = true },
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options_action_content_description),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                CalendarExportTopBarDropDownMenu(
                    isVisible = isMenuDropDownVisible,
                    hasDatesSelected = !isSelectedDatesEmpty,
                    hasLabelAssigned = calendarHasLabelAssigned,
                    onDatesSelect = {
                        if (isSelectedDatesEmpty) {
                            onEditCalendar()
                        } else {
                            onClearSelectedDates()
                        }

                        isMenuDropDownVisible = false
                    },
                    onLabelAssign = {
                        onLabelAssign()

                        isMenuDropDownVisible = false
                    },
                    onDismiss = { isMenuDropDownVisible = false },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        modifier = modifier
            .drawBehind {
                drawLine(
                    color = outlineVariantColor,
                    start = Offset(x = 0f, y = size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
    )
}
