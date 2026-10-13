package com.bestrong.core.io

import com.bestrong.core.model.Workout
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exportación a Excel (.xlsx) sin librerías: un libro mínimo (SpreadsheetML) con una hoja
 * «Series» que también abren Google Sheets, Numbers y LibreOffice.
 */
object Xlsx {
    fun workouts(workouts: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): ByteArray {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val rows = mutableListOf<List<Any>>(listOf("Fecha", "Entreno", "Ejercicio", "Serie", "Tipo", "Peso (kg)", "Reps", "Segundos", "Metros", "RPE", "1RM est.", "Notas"))
        workouts.sortedBy { it.startedAtEpochMs }.forEach { w ->
            val date = fmt.format(Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone))
            w.exercises.forEach { we ->
                we.sets.filter { it.completed }.forEachIndexed { i, s ->
                    rows += listOf(date, w.name, we.exercise.name, i + 1, s.type.name, s.weightKg, s.reps, s.durationSec, s.distanceM, s.rpe ?: "", Math.round(s.estimatedOneRepMax * 10) / 10.0, s.note)
                }
            }
        }
        return book("Series", rows)
    }

    fun book(sheetName: String, rows: List<List<Any>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${esc(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            )
            val sheet = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { r, row ->
                sheet.append("<row r=\"${r + 1}\">")
                row.forEachIndexed { c, v ->
                    val ref = column(c) + (r + 1)
                    when (v) {
                        is Number -> sheet.append("<c r=\"$ref\"><v>$v</v></c>")
                        else -> sheet.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${esc(v.toString())}</t></is></c>")
                    }
                }
                sheet.append("</row>")
            }
            sheet.append("</sheetData></worksheet>")
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
        return out.toByteArray()
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    fun column(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val m = (n - 1) % 26
            sb.insert(0, 'A' + m)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** Fuera los caracteres que XML 1.0 no admite (un nombre con \u0001 dejaría el .xlsx corrupto). */
    private fun esc(s: String) = s.filter { it == '\t' || it == '\n' || it == '\r' || (it >= ' ' && it != '\uFFFE' && it != '\uFFFF') }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
