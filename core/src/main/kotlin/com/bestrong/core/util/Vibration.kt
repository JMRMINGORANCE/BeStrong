package com.bestrong.core.util

import kotlinx.serialization.Serializable

/**
 * Patrón de vibración al acabar el descanso (móvil y reloj). Distintos ritmos para
 * reconocerlo sin mirar y para quien percibe peor las vibraciones cortas.
 */
@Serializable
enum class VibrationStyle(val pattern: LongArray) {
    SHORT(longArrayOf(0, 150)),
    DOUBLE(longArrayOf(0, 250, 150, 250)),
    TRIPLE(longArrayOf(0, 300, 150, 300, 150, 300)),
    LONG(longArrayOf(0, 900)),
    HEARTBEAT(longArrayOf(0, 120, 90, 260, 500, 120, 90, 260)),
    STRONG(longArrayOf(0, 600, 200, 600, 200, 600)),
}
