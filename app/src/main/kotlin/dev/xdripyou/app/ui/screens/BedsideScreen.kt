package dev.xdripyou.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xdripyou.app.domain.GlucoseUnit
import dev.xdripyou.app.ui.MainUiState
import dev.xdripyou.app.ui.theme.LocalGlucoseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen OLED bedside clock + large glucose display (inspired by xDrip's Nightstand / AOD).
 */
@Composable
fun BedsideScreen(
    state: MainUiState,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (!state.settings.keepScreenOn) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val glucoseColors = LocalGlucoseColors.current
    val settings = state.settings
    val latest = state.latestReading
    val deltaMgdl = state.latestDeltaMgdl
    val staleLimitMs = settings.staleThresholdMinutes * 60_000L
    val isStale = latest?.let { (state.nowMs - it.timestampMs) > staleLimitMs } ?: false
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val valueColor = when {
        latest == null -> Color(0xFF9E9E9E)
        isStale -> Color(0xFF757575)
        else -> glucoseColors.colorForMgdl(latest.mgdl, settings.targetLowMgdl, settings.targetHighMgdl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExit,
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = timeFmt.format(Date(state.nowMs)),
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(16.dp))
            if (latest == null) {
                Text(
                    text = "---",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = GlucoseUnit.formatValue(latest.mgdl, settings.useMgdl),
                        fontSize = 104.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp,
                        color = valueColor,
                        textDecoration = if (isStale) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = latest.trend.symbol,
                            fontSize = 48.sp,
                            color = valueColor,
                        )
                        if (deltaMgdl != null) {
                            Text(
                                text = GlucoseUnit.formatDelta(deltaMgdl, settings.useMgdl),
                                style = MaterialTheme.typography.titleLarge,
                                color = valueColor.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val meta = buildList {
                    val mins = latest.minutesAgoAt(state.nowMs)
                    add(if (mins == 0L) "gerade eben" else "vor ${mins} Min.")
                    if (state.iobCob.hasActiveIob) {
                        add("IOB ${String.format(Locale.US, "%.2f", state.iobCob.iobUnits)} IE")
                    }
                    if (state.iobCob.hasActiveCob) {
                        add("COB ${state.iobCob.cobGrams.toInt()} g")
                    }
                }
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Tippen zum Beenden",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.35f),
            )
        }
    }
}
