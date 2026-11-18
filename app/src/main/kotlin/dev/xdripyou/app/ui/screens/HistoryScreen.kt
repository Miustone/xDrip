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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.domain.TreatmentEntry
import dev.xdripyou.app.ui.MainUiState
import dev.xdripyou.app.ui.components.TreatmentEditorSheet
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class HistoryTimelineItem(val timestampMs: Long, val key: String) {
    class ReadingItem(
        val reading: GlucoseReading,
        val deltaStr: String?,
    ) : HistoryTimelineItem(reading.timestampMs, "r_${reading.id}")

    class TreatmentItem(
        val treatment: TreatmentEntry,
    ) : HistoryTimelineItem(treatment.timestampMs, "t_${treatment.id}")
}

@Composable
fun HistoryScreen(
    state: MainUiState,
    onAddTreatment: (TreatmentEntry) -> Unit,
    onDeleteTreatment: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glucoseColors = LocalGlucoseColors.current
    val settings = state.settings
    val stats = state.stats24h
    val readings = state.readings24h
    val treatments = state.treatments24h
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var filterIndex by remember { mutableIntStateOf(0) } // 0 = Alle, 1 = Glukose, 2 = Einträge
    var showTreatmentSheet by remember { mutableStateOf(false) }

    val timelineItems = remember(readings, treatments, filterIndex, settings.useMgdl) {
        val list = mutableListOf<HistoryTimelineItem>()
        if (filterIndex == 0 || filterIndex == 1) {
            readings.forEachIndexed { index, r ->
                val prev = readings.getOrNull(index + 1)
                val delta = if (prev != null && (r.timestampMs - prev.timestampMs) <= 1_200_000L) {
                    GlucoseUnit.formatDelta(r.mgdl - prev.mgdl, settings.useMgdl)
                } else {
                    null
                }
                list.add(HistoryTimelineItem.ReadingItem(r, delta))
            }
        }
        if (filterIndex == 0 || filterIndex == 2) {
            treatments.forEach { t ->
                list.add(HistoryTimelineItem.TreatmentItem(t))
            }
        }
        list.sortByDescending { it.timestampMs }
        list
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showTreatmentSheet = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Eintrag hinzufügen")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
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
                                "Min: ${GlucoseUnit.formatValue(stats.minMgdl, settings.useMgdl)} · Max: ${GlucoseUnit.formatValue(stats.maxMgdl, settings.useMgdl)} ${GlucoseUnit.unitLabel(settings.useMgdl)} · ${stats.count} Werte · ${treatments.size} Einträge",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                val labels = listOf("Alle", "Glukose", "Einträge")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = filterIndex == idx,
                            onClick = { filterIndex = idx },
                            shape = SegmentedButtonDefaults.itemShape(idx, labels.size),
                        ) { Text(label) }
                    }
                }
            }

            items(timelineItems, key = { it.key }) { item ->
                when (item) {
                    is HistoryTimelineItem.ReadingItem -> {
                        val reading = item.reading
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
                                        if (item.deltaStr != null) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "(${item.deltaStr})",
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

                    is HistoryTimelineItem.TreatmentItem -> {
                        val t = item.treatment
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer.copy(alpha = 0.55f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Medication,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val parts = buildList {
                                        if (t.hasInsulin) add("${String.format(Locale.US, "%.1f", t.insulinUnits)} IE")
                                        if (t.hasCarbs) add("${t.carbsGrams.toInt()} g KH")
                                        if (t.hasBloodGlucose) {
                                            add("BZ ${GlucoseUnit.formatValue(t.bloodGlucoseMgdl!!, settings.useMgdl)}")
                                        }
                                    }
                                    Text(
                                        text = if (parts.isEmpty()) t.eventType else parts.joinToString(" · "),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (t.note.isNotBlank()) {
                                        Text(
                                            text = t.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = timeFmt.format(Date(t.timestampMs)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { onDeleteTreatment(t.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Löschen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTreatmentSheet) {
        TreatmentEditorSheet(
            useMgdl = settings.useMgdl,
            onDismiss = { showTreatmentSheet = false },
            onSave = { entry ->
                onAddTreatment(entry)
                showTreatmentSheet = false
            },
        )
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
