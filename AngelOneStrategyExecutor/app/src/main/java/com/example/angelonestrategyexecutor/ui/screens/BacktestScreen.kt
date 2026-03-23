package com.example.angelonestrategyexecutor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.angelonestrategyexecutor.data.model.BacktestResultEntry
import com.example.angelonestrategyexecutor.data.repository.WatchListRepository
import com.example.angelonestrategyexecutor.service.BacktestForegroundService
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val progress by WatchListRepository.backtestProgress.collectAsStateWithLifecycle()

    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH) }
    val todayUtcMillis = remember { LocalDate.now(ZoneOffset.UTC).toEpochDay() * 86_400_000L }
    val weekdayPastDates = remember {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                if (utcTimeMillis > todayUtcMillis) return false
                val dow = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).dayOfWeek
                return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
            }
            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        }
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fromDateMillis ?: System.currentTimeMillis(),
            selectableDates = weekdayPastDates,
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDateMillis = state.selectedDateMillis
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = toDateMillis ?: System.currentTimeMillis(),
            selectableDates = weekdayPastDates,
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toDateMillis = state.selectedDateMillis
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = {
            TopAppBar(
                title = { Text("Backtest", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (progress.isRunning) {
                        TextButton(
                            onClick = { WatchListRepository.stopBacktest() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("Stop") }
                    }
                    if (progress.results.isNotEmpty()) {
                        IconButton(onClick = {
                            val text = progress.results.joinToString("\n") { "${it.candleDateTime}  ${it.symbol}" }
                            clipboardManager.setText(AnnotatedString(text))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy all results")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // From / To date selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { if (!progress.isRunning) showFromPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = fromDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(dateFormatter)
                        } ?: "From date",
                        maxLines = 1,
                    )
                }
                OutlinedButton(
                    onClick = { if (!progress.isRunning) showToPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = toDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(dateFormatter)
                        } ?: "To date",
                        maxLines = 1,
                    )
                }
            }

            // Run button
            Button(
                enabled = !progress.isRunning && fromDateMillis != null && toDateMillis != null,
                onClick = {
                    val from = Instant.ofEpochMilli(fromDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val to = Instant.ofEpochMilli(toDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    if (from.isAfter(to)) {
                        validationError = "From date must be before or equal to To date"
                        return@Button
                    }
                    validationError = null
                    BacktestForegroundService.start(context, from, to)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (progress.isRunning) "Running…" else "Run Backtest")
            }

            // Progress bar with percentage
            if (progress.isRunning && progress.total > 0) {
                val pct = (progress.scanned.toFloat() / progress.total * 100).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { progress.scanned.toFloat() / progress.total },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Status / validation message
            val displayMessage = validationError ?: progress.message.takeIf { it.isNotBlank() }
            if (displayMessage != null) {
                Text(displayMessage, style = MaterialTheme.typography.bodySmall)
            }

            // Results list
            if (progress.results.isNotEmpty()) {
                HorizontalDivider()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(progress.results) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = entry.candleDateTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = entry.symbol,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
