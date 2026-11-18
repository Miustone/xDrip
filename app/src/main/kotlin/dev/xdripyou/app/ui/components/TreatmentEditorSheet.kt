package dev.xdripyou.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.TreatmentEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TreatmentEditorSheet(
    useMgdl: Boolean,
    initialEntry: TreatmentEntry? = null,
    onDismiss: () -> Unit,
    onSave: (TreatmentEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val eventTypes = listOf("Meal Bolus", "Correction Bolus", "Carb Correction", "Note")

    var eventType by remember(initialEntry) {
        mutableStateOf(initialEntry?.eventType?.takeIf { it in eventTypes } ?: "Meal Bolus")
    }
    var insulinText by remember(initialEntry) {
        mutableStateOf(initialEntry?.insulinUnits?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var carbsText by remember(initialEntry) {
        mutableStateOf(
            initialEntry?.carbsGrams?.takeIf { it > 0.0 }?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            } ?: ""
        )
    }
    var bgText by remember(initialEntry) {
        mutableStateOf(
            initialEntry?.bloodGlucoseMgdl?.takeIf { it > 0.0 }?.let {
                GlucoseUnit.formatValue(it, useMgdl)
            } ?: ""
        )
    }
    var noteText by remember(initialEntry) { mutableStateOf(initialEntry?.note ?: "") }
    var offsetMinutes by remember(initialEntry) { mutableIntStateOf(0) }

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
                text = if (initialEntry == null) "Therapie-Eintrag hinzufügen" else "Therapie-Eintrag bearbeiten",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                eventTypes.forEach { type ->
                    val label = when (type) {
                        "Meal Bolus" -> "Mahlzeit"
                        "Correction Bolus" -> "Korrektur"
                        "Carb Correction" -> "Hypo-KH"
                        else -> "Notiz"
                    }
                    FilterChip(
                        selected = eventType == type,
                        onClick = { eventType = type },
                        label = { Text(label) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = insulinText,
                    onValueChange = { insulinText = it },
                    label = { Text("Insulin (IE)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = it },
                    label = { Text("Kohlenhydrate (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = bgText,
                onValueChange = { bgText = it },
                label = { Text("Optional: Blutzucker-Messung (${GlucoseUnit.unitLabel(useMgdl)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Notiz") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (initialEntry == null) {
                Text(
                    text = "Zeitpunkt",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Jetzt", 10 to "vor 10m", 20 to "vor 20m", 30 to "vor 30m").forEach { (mins, label) ->
                        FilterChip(
                            selected = offsetMinutes == mins,
                            onClick = { offsetMinutes = mins },
                            label = { Text(label) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
                Button(
                    onClick = {
                        val insulin = insulinText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val carbs = carbsText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val bgRaw = bgText.replace(',', '.').toDoubleOrNull()
                        val bgMgdl = bgRaw?.let { GlucoseUnit.toMgdl(it, fromMgdl = useMgdl) }
                        if (insulin <= 0.0 && carbs <= 0.0 && bgMgdl == null && noteText.isBlank()) {
                            return@Button
                        }
                        onSave(
                            TreatmentEntry(
                                id = initialEntry?.id ?: 0L,
                                timestampMs = initialEntry?.timestampMs ?: (System.currentTimeMillis() - offsetMinutes * 60_000L),
                                insulinUnits = insulin,
                                carbsGrams = carbs,
                                bloodGlucoseMgdl = bgMgdl,
                                note = noteText.trim(),
                                eventType = eventType,
                                syncedNightscout = false,
                            )
                        )
                    },
                ) { Text("Speichern") }
            }
        }
    }
}
