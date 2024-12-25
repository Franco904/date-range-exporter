package com.example.daterangeexporter.calendarExport.localComposables

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daterangeexporter.R
import com.example.daterangeexporter.core.theme.AppTheme

@Composable
fun CalendarLabelAssignDialog(
    input: String?,
    onSave: (String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var labelInput by rememberSaveable { mutableStateOf(input) }

    val titleResId = if (!input.isNullOrBlank()) {
        R.string.calendar_label_assign_dialog_title_rename
    } else R.string.calendar_label_assign_dialog_title_assign

    LaunchedEffect(input) {
        labelInput = input
    }

    AlertDialog(
        title = {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            OutlinedTextField(
                value = labelInput ?: "",
                onValueChange = { input -> labelInput = input },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.calendar_label_assign_dialog_field_placeholder),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Label,
                        contentDescription = stringResource(titleResId),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                singleLine = true,
                keyboardActions = KeyboardActions(
                    onDone = {
                        onSave(labelInput)
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(labelInput)
                    labelInput = null
                },
            ) {
                Text(
                    text = stringResource(id = R.string.calendar_label_assign_dialog_primary_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onCancel()
                labelInput = null
            }) {
                Text(
                    text = stringResource(id = R.string.calendar_label_assign_dialog_secondary_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        onDismissRequest = {},
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .width(450.dp)
    )
}

@Preview
@Composable
fun CalendarLabelAssignDialogPreview(
    modifier: Modifier = Modifier,
) {
    AppTheme {
        CalendarLabelAssignDialog(
            input = "Franco Saravia Tavares",
            onSave = { _ -> },
            onCancel = {},
            modifier = modifier,
        )
    }
}
