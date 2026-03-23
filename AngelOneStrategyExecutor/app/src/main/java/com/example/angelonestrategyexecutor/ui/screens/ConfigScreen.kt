package com.example.angelonestrategyexecutor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.angelonestrategyexecutor.data.config.AppConfig
import com.example.angelonestrategyexecutor.receiver.LoginAlarmScheduler
import com.example.angelonestrategyexecutor.receiver.WatchListScanScheduler

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(contentPadding: PaddingValues = PaddingValues()) {

    val context = LocalContext.current

    // Observe config values reactively
    val apiKey by AppConfig.apiKey.collectAsStateWithLifecycle()
    val userId by AppConfig.userId.collectAsStateWithLifecycle()
    val pin by AppConfig.pin.collectAsStateWithLifecycle()
    val placeOrders by AppConfig.placeOrders.collectAsStateWithLifecycle()
    val alerts by AppConfig.alerts.collectAsStateWithLifecycle()
    val numLots by AppConfig.numLots.collectAsStateWithLifecycle()
    val productType by AppConfig.productType.collectAsStateWithLifecycle()
    val reminderHour by AppConfig.reminderHour.collectAsStateWithLifecycle()
    val reminderMinute by AppConfig.reminderMinute.collectAsStateWithLifecycle()
    val reminderDays by AppConfig.reminderDays.collectAsStateWithLifecycle()
    val watchListScanMode by AppConfig.watchListScanMode.collectAsStateWithLifecycle()

    // Local visibility toggles
    var pinVisible by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var productTypeExpanded by remember { mutableStateOf(false) }
    var watchListModeExpanded by remember { mutableStateOf(false) }

    val productTypes = listOf("CARRYFORWARD", "INTRADAY", "DELIVERY")
    val watchListModes = listOf(
        AppConfig.WATCHLIST_SCAN_MODE_ONLY_0930,
        AppConfig.WATCHLIST_SCAN_MODE_EVERY_15_MIN,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // ══════════════════════════════════════════════════════════════════
        // LOGIN CREDENTIALS
        // ══════════════════════════════════════════════════════════════════
        SectionHeader("Login Credentials")
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { AppConfig.setApiKey(it) },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                imageVector = if (apiKeyVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                contentDescription = if (apiKeyVisible) "Hide" else "Show",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                // User ID
                OutlinedTextField(
                    value = userId,
                    onValueChange = { AppConfig.setUserId(it.uppercase()) },
                    label = { Text("User ID (Client Code)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                // PIN
                OutlinedTextField(
                    value = pin,
                    onValueChange = { AppConfig.setPin(it) },
                    label = { Text("PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (pinVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                contentDescription = if (pinVisible) "Hide" else "Show",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // TRADING
        // ══════════════════════════════════════════════════════════════════
        SectionHeader("Trading")
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Place Orders toggle
                SwitchRow(
                    label = "Place Orders",
                    description = "Automatically place buy/sell orders when triggers hit",
                    checked = placeOrders,
                    onCheckedChange = { AppConfig.setPlaceOrders(it) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Number of Lots
                OutlinedTextField(
                    value = numLots.toString(),
                    onValueChange = { text ->
                        val parsed = text.filter { it.isDigit() }.toIntOrNull()
                        if (parsed != null) AppConfig.setNumLots(parsed)
                    },
                    label = { Text("Number of Lots") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Quantity = lots × lot size") },
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Product Type dropdown
                ExposedDropdownMenuBox(
                    expanded = productTypeExpanded,
                    onExpandedChange = { productTypeExpanded = !productTypeExpanded },
                ) {
                    OutlinedTextField(
                        value = productType,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Product Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productTypeExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = productTypeExpanded,
                        onDismissRequest = { productTypeExpanded = false },
                    ) {
                        productTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    AppConfig.setProductType(type)
                                    productTypeExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // NOTIFICATIONS
        // ══════════════════════════════════════════════════════════════════
        SectionHeader("Notifications")
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                SwitchRow(
                    label = "Alerts",
                    description = "Show notifications for order triggers and status changes",
                    checked = alerts,
                    onCheckedChange = { AppConfig.setAlerts(it) },
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // WATCHLIST SCANNER
        // ══════════════════════════════════════════════════════════════════
        SectionHeader("WatchList Scanner")
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Scan Schedule",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )

                ExposedDropdownMenuBox(
                    expanded = watchListModeExpanded,
                    onExpandedChange = { watchListModeExpanded = !watchListModeExpanded },
                ) {
                    OutlinedTextField(
                        value = if (watchListScanMode == AppConfig.WATCHLIST_SCAN_MODE_EVERY_15_MIN)
                            "Every 15 min"
                        else "Only at 9:30 AM",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("WatchList Scan Mode") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = watchListModeExpanded)
                        },
                    )

                    ExposedDropdownMenu(
                        expanded = watchListModeExpanded,
                        onDismissRequest = { watchListModeExpanded = false },
                    ) {
                        watchListModes.forEach { mode ->
                            val label = if (mode == AppConfig.WATCHLIST_SCAN_MODE_EVERY_15_MIN)
                                "Every 15 min"
                            else "Only at 9:30 AM"

                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    AppConfig.setWatchListScanMode(mode)
                                    WatchListScanScheduler.schedule(context)
                                    watchListModeExpanded = false
                                },
                            )
                        }
                    }
                }

                Text(
                    text = "Scanner runs in background and updates WatchList using your candle criteria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // LOGIN REMINDER
        // ══════════════════════════════════════════════════════════════════
        SectionHeader("Login Reminder")
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Time display + change button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reminder Time",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = String.format("%02d:%02d", reminderHour, reminderMinute),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    var showTimePicker by remember { mutableStateOf(false) }

                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Change time",
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp),
                        )
                        Text("Change")
                    }

                    if (showTimePicker) {
                        val timePickerState = rememberTimePickerState(
                            initialHour = reminderHour,
                            initialMinute = reminderMinute,
                            is24Hour = true,
                        )
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showTimePicker = false },
                            title = { Text("Set Reminder Time") },
                            text = { TimeInput(state = timePickerState) },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    AppConfig.setReminderHour(timePickerState.hour)
                                    AppConfig.setReminderMinute(timePickerState.minute)
                                    LoginAlarmScheduler.schedule(context)
                                    showTimePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    showTimePicker = false
                                }) { Text("Cancel") }
                            },
                        )
                    }
                }

                HorizontalDivider()

                // Day-of-week toggles
                Text(
                    text = "Reminder Days",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )

                val dayLabels = listOf(
                    2 to "Mon", 3 to "Tue", 4 to "Wed",
                    5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun",
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    dayLabels.forEach { (calendarDay, label) ->
                        val selected = calendarDay in reminderDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val updated = if (selected)
                                    reminderDays - calendarDay
                                else
                                    reminderDays + calendarDay
                                AppConfig.setReminderDays(updated)
                                LoginAlarmScheduler.schedule(context)
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Reusable composables ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
