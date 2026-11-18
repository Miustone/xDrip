package dev.xdripyou.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.ui.MainUiState
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: MainUiState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    val settings = state.settings
    val stats = state.stats24h
    val readings = state.readings24h
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "24-Stunden Time in Range",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (stats.count == 0) {
                        Text(
                            "Noch keine Messwerte in den letzten 24 Stunden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        TirBar(
                            veryLow = stats.veryLowPercent,
                            low = stats.lowPercent,
                            inRange = stats.inRangePercent,
                            high = stats.highPercent,
                            veryHigh = stats.veryHighPercent,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Tief: ${stats.veryLowPercent.toInt() + stats.lowPercent.toInt()} %",
                                style = MaterialTheme.typography.labelMedium,
                                color = glucoseColors.low,
                            )
                            Text(
                                "Im Ziel: ${stats.inRangePercent.toInt()} %",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = glucoseColors.inRange,
                            )
                            Text(
                                "Hoch: ${stats.highPercent.toInt() + stats.veryHighPercent.toInt()} %",
                                style = MaterialTheme.typography.labelMedium,
                                color = glucoseColors.high,
                            )
                        }
                        Text(
                            "Min: ${GlucoseUnit.formatValue(stats.minMgdl, settings.useMgdl)} · Max: ${GlucoseUnit.formatValue(stats.maxMgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)} · ${stats.count} Werte",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        itemsIndexed(readings, key = { _, r -> r.id }) { index, reading ->
            val prev = readings.getOrNull(index + 1)
            val deltaStr = if (prev != null && (reading.timestampMs - prev.timestampMs) <= 1_200_000L) {
                GlucoseUnit.formatDelta(reading.mgdl - prev.mgdl, settings.useMgdl)
            } else {
                null
            }
            val color = glucoseColors.colorForMgdl(
                reading.mgdl,
                targetLow = settings.targetLowMgdl,
                targetHigh = settings.targetHighMgdl,
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${GlucoseUnit.formatValue(reading.mgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)} ${reading.trend.symbol}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (deltaStr != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "($deltaStr)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = buildString {
                                append(reading.source)
                                reading.rawText?.takeIf { it.isNotBlank() }?.let {
                                    append(" · \"")
                                    append(it.take(36))
                                    append("\"")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = timeFmt.format(Date(reading.timestampMs)),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TirBar(
    veryLow: Double,
    low: Double,
    inRange: Double,
    high: Double,
    veryHigh: Double,
) {
    val glucoseColors = LocalGlucoseColors.current
    val segments = listOf(
        veryLow to glucoseColors.low.copy(alpha = 0.85f),
        low to glucoseColors.low,
        inRange to glucoseColors.inRange,
        high to glucoseColors.high,
        veryHigh to glucoseColors.urgentHigh,
    ).filter { it.first > 0.5 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(999.dp)),
    ) {
        if (segments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = 0.2f)),
            )
        } else {
            for ((pct, color) in segments) {
                Box(
                    modifier = Modifier
                        .weight(pct.toFloat().coerceAtLeast(0.5f))
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
    }
}
