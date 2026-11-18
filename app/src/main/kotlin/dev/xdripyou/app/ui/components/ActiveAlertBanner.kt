package dev.xdripyou.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.ActiveAlertState
import dev.xdripyou.app.domain.GlucoseUnit

@Composable
fun ActiveAlertBanner(
    alert: ActiveAlertState,
    useMgdl: Boolean,
    nowMs: Long,
    onSnooze: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (alert.isSnoozed) scheme.secondaryContainer else scheme.errorContainer
    val contentColor = if (alert.isSnoozed) scheme.onSecondaryContainer else scheme.onErrorContainer
    val remainingMins = ((alert.nextAlertAtMs - nowMs) / 60_000L).coerceAtLeast(0L)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (alert.isSnoozed) Icons.Rounded.Snooze else Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (alert.isSnoozed) "Alarm pausiert: ${alert.ruleName}" else "Aktiver Alarm: ${alert.ruleName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (alert.isSnoozed) {
                            "Erneut in ca. $remainingMins Min."
                        } else {
                            "Ausgelöst bei ${GlucoseUnit.formatValue(alert.triggerMgdl, useMgdl)} ${GlucoseUnit.unitLabel(useMgdl)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { onSnooze(15) }) { Text("15m") }
                FilledTonalButton(onClick = { onSnooze(30) }) { Text("30m") }
                FilledTonalButton(onClick = { onSnooze(60) }) { Text("60m") }
                Spacer(Modifier.weight(1f))
                if (alert.isSnoozed) {
                    OutlinedButton(onClick = onStop) { Text("Aufheben") }
                } else {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.error,
                            contentColor = scheme.onError,
                        ),
                    ) {
                        Text("Stopp")
                    }
                }
            }
        }
    }
}
