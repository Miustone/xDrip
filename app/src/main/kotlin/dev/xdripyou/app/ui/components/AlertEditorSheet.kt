package dev.xdripyou.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.AlertRule
import dev.xdripyou.app.domain.GlucoseUnit
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertEditorSheet(
    existing: AlertRule?,
    useMgdl: Boolean,
    onDismiss: () -> Unit,
    onSave: (AlertRule) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(existing?.name ?: "Neuer Alarm") }
    var isHigh by remember { mutableStateOf(existing?.isHigh ?: false) }
    var thresholdText by remember {
        val initialMgdl = existing?.thresholdMgdl ?: 70.0
        mutableStateOf(
            if (useMgdl) initialMgdl.toInt().toString()
            else String.format(java.util.Locale.US, "%.1f", GlucoseUnit.toDisplayValue(initialMgdl, false)),
        )
    }
    var minutesBetweenText by remember { mutableStateOf((existing?.minutesBetween ?: 10).toString()) }
    var snoozeText by remember { mutableStateOf((existing?.defaultSnoozeMinutes ?: 20).toString()) }
    var allDay by remember { mutableStateOf(existing?.allDay ?: true) }
    var startHour by remember { mutableIntStateOf((existing?.startMinutes ?: 1320) / 60) }
    var endHour by remember { mutableIntStateOf((existing?.endMinutes ?: 420) / 60) }
    var overrideSilent by remember { mutableStateOf(existing?.overrideSilent ?: false) }
    var vibrate by remember { mutableStateOf(existing?.vibrate ?: true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existing == null) "Alarm erstellen" else "Alarm bearbeiten",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isHigh,
                    onClick = { isHigh = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Niedrig (≤)") }
                SegmentedButton(
                    selected = isHigh,
                    onClick = { isHigh = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Hoch (≥)") }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bezeichnung") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                label = { Text("Schwellwert (${GlucoseUnit.unitLabel(useMgdl)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minutesBetweenText,
                    onValueChange = { minutesBetweenText = it },
                    label = { Text("Wiederholen (Min.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = snoozeText,
                    onValueChange = { snoozeText = it },
                    label = { Text("Schlummern (Min.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ganztägig aktiv", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }

            if (!allDay) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = startHour.toString(),
                        onValueChange = { v -> startHour = (v.toIntOrNull() ?: 0).coerceIn(0, 23) },
                        label = { Text("Start (Std. 0–23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endHour.toString(),
                        onValueChange = { v -> endHour = (v.toIntOrNull() ?: 0).coerceIn(0, 23) },
                        label = { Text("Ende (Std. 0–23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lautlos-Modus umgehen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Nutzt Android-Alarmkanal mit voller Priorität",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = overrideSilent, onCheckedChange = { overrideSilent = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vibration", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (existing != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
                Button(
                    onClick = {
                        val rawThreshold = thresholdText.replace(',', '.').toDoubleOrNull() ?: 70.0
                        val mgdl = GlucoseUnit.toMgdl(rawThreshold, fromMgdl = useMgdl).coerceIn(40.0, 400.0)
                        onSave(
                            AlertRule(
                                uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                                name = name.trim().ifEmpty { if (isHigh) "Hoch" else "Niedrig" },
                                isHigh = isHigh,
                                thresholdMgdl = mgdl,
                                enabled = existing?.enabled ?: true,
                                allDay = allDay,
                                startMinutes = startHour * 60,
                                endMinutes = endHour * 60,
                                minutesBetween = (minutesBetweenText.toIntOrNull() ?: 10).coerceIn(1, 180),
                                defaultSnoozeMinutes = (snoozeText.toIntOrNull() ?: 20).coerceIn(1, 360),
                                overrideSilent = overrideSilent,
                                vibrate = vibrate,
                                soundUri = existing?.soundUri,
                            ),
                        )
                    },
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}
