package dev.xdripyou.app.domain

/**
 * Trend arrow definitions matching xDrip's `Dex_Constants$TREND_ARROW_VALUES`.
 */
enum class TrendArrow(
    val wireName: String,
    val symbol: String,
    val labelDe: String,
) {
    DOUBLE_UP("DoubleUp", "⇈", "Stark steigend"),
    SINGLE_UP("SingleUp", "↑", "Steigend"),
    FORTY_FIVE_UP("FortyFiveUp", "↗", "Leicht steigend"),
    FLAT("Flat", "→", "Stabil"),
    FORTY_FIVE_DOWN("FortyFiveDown", "↘", "Leicht fallend"),
    SINGLE_DOWN("SingleDown", "↓", "Fallend"),
    DOUBLE_DOWN("DoubleDown", "⇊", "Stark fallend"),
    NOT_COMPUTABLE("NotComputable", "↔", "Trend ausstehend");

    companion object {
        fun fromWireName(name: String?): TrendArrow {
            return entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
                ?: NOT_COMPUTABLE
        }

        fun fromSlopePerMin(slopeMgdlPerMin: Double): TrendArrow {
            if (slopeMgdlPerMin.isNaN()) return NOT_COMPUTABLE
            return when {
                slopeMgdlPerMin > 3.5 -> DOUBLE_UP
                slopeMgdlPerMin > 2.0 -> SINGLE_UP
                slopeMgdlPerMin > 1.0 -> FORTY_FIVE_UP
                slopeMgdlPerMin >= -1.0 -> FLAT
                slopeMgdlPerMin >= -2.0 -> FORTY_FIVE_DOWN
                slopeMgdlPerMin >= -3.5 -> SINGLE_DOWN
                else -> DOUBLE_DOWN
            }
        }
    }
}
