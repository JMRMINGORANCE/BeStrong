package com.bestrong.core.util

import java.time.LocalDate

/**
 * Avisos descartados: los de un día concreto llevan la fecha al final («year_ago:2026-10-14», «missed:2026-10-12:2»)
 * y, pasado un tiempo, ya no sirven; se quitan para que la lista no crezca sin fin (viaja en la copia de seguridad).
 */
object DismissedTips {
    private const val KEEP_DAYS = 60L

    /** Fecha al final, o seguida de un número («missed:2026-10-12:2»); o un mes de las claves antiguas («year_ago:2026-10»). */
    private val DATED = Regex(":(\\d{4}-\\d{2}(?:-\\d{2})?)(?::\\d+)?$")

    /** [dismissed] sin los avisos con fecha de hace más de [KEEP_DAYS] días; los que no llevan fecha se quedan. */
    fun prune(dismissed: Set<String>, today: LocalDate): Set<String> = dismissed.filterTo(mutableSetOf()) { key ->
        val date = DATED.find(key)?.groupValues?.get(1)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull() ?: runCatching { java.time.YearMonth.parse(it).atEndOfMonth() }.getOrNull()
        }
        date == null || !date.isBefore(today.minusDays(KEEP_DAYS))
    }
}
