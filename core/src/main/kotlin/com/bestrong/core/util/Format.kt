package com.bestrong.core.util

import kotlin.math.roundToLong

/** 80.0 → "80", 82.5 → "82,5" */
fun formatWeight(kg: Double): String {
    val rounded = (kg * 100).roundToLong() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
    else rounded.toString().trimEnd('0').replace('.', ',')
}

/** 12_345.0 → "12.345" */
fun formatVolume(kg: Double): String {
    val n = kg.roundToLong()
    return n.toString().reversed().chunked(3).joinToString(".").reversed()
}

/** 3725 s → "1h 02m", 125 s → "2:05" */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%d:%02d".format(m, s)
}
