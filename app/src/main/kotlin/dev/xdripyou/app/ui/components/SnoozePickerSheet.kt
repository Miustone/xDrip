package dev.xdripyou.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Port of xDrip's `SnoozeActivity`: lets the user pick exact snooze duration and scope
 * (`active`, `low`, `high`, `all`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SnoozePickerSheet(
    onDismiss: () -> Unit,
    onSnoozeActive: (Int) -> Unit,
    onSilenceScope: (String, Int) -> Unit,
) {
    var selectedMins by remember { mutableIntStateOf(30) }
    var scope by remember { mutableStateOf("active") }
    val presets = listOf(10, 15, 20, 30, 45, 60, 120, 240, 480, 720)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Alarm schlummern",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("Bereich", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = scope == "active", onClick = { scope = "active" }, label = { Text("Aktiver Alarm") })
                FilterChip(selected = scope == "low", onClick = { scope = "low" }, label = { Text("Alle Niedrig") })
                FilterChip(selected = scope == "high", onClick = { scope = "high" }, label = { Text("Alle Hoch") })
                FilterChip(selected = scope == "all", onClick = { scope = "all" }, label = { Text("Alle Alarme") })
            }

            Text("Dauer", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEach { mins ->
                    val label = if (mins >= 60) "${mins / 60} Std." else "${mins}m"
                    FilterChip(
                        selected = selectedMins == mins,
                        onClick = { selectedMins = mins },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Abbrechen")
                }
                Button(
                    onClick = {
                        when (scope) {
                            "active" -> onSnoozeActive(selectedMins)
                            else -> onSilenceScope(scope, selectedMins)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Übernehmen")
                }
            }
        }
    }
}
