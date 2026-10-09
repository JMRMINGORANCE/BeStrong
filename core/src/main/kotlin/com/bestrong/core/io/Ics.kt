package com.bestrong.core.io

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Evento del calendario: una sesión planificada. */
data class CalendarEvent(val date: LocalDate, val title: String, val minutes: Int, val description: String = "")

/**
 * Archivo iCalendar (.ics) para añadir la semana planificada al calendario del sistema
 * (Samsung Calendar, Google Calendar…) sin pedir permisos de calendario.
 */
object Ics {
    private val UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun generate(events: List<CalendarEvent>, hour: LocalTime, zone: ZoneId, nowUtc: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//BeStrong//ES\r\nCALSCALE:GREGORIAN\r\n")
        events.forEach { e ->
            val start = e.date.atTime(hour).atZone(zone).withZoneSameInstant(ZoneId.of("UTC"))
            val end = start.plusMinutes(e.minutes.toLong())
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:bestrong-${e.date}-${e.title.hashCode()}@bestrong\r\n")
            sb.append("DTSTAMP:${UTC.format(nowUtc)}\r\n")
            sb.append("DTSTART:${UTC.format(start)}\r\n")
            sb.append("DTEND:${UTC.format(end)}\r\n")
            sb.append("SUMMARY:${escape(e.title)}\r\n")
            if (e.description.isNotBlank()) sb.append("DESCRIPTION:${escape(e.description)}\r\n")
            sb.append("END:VEVENT\r\n")
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
