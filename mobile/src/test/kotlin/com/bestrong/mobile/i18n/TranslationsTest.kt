package com.bestrong.mobile.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Las 7 traducciones (es base + en, ca, gl, pt, fr, de) del móvil y del reloj tienen las mismas claves y los mismos
 * argumentos (%1$s, %2$d…) que el español: una que falte sale en español y un argumento distinto cuelga la app.
 */
class TranslationsTest {
    /** Cualquier argumento de formato (%s, %1$s, %d, %.1f…); «%%» es un % escrito y se quita antes. */
    private val arg = Regex("%(\\d+\\$)?[-#+0,(]*\\d*(\\.\\d+)?[sdf]")

    /** Carpeta res del módulo, tanto si los tests corren desde el módulo como desde la raíz. */
    private fun resDir(module: String): File =
        listOf(File("../$module/src/main/res"), File("$module/src/main/res")).first { it.isDirectory }

    /** clave → argumentos ordenados; los plurales como «plurals:nombre» con los argumentos de todas sus formas juntos. */
    private fun load(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            val args = arg.findAll(e.textContent.replace("%%", "")).map { it.value }.toList()
            when (e.tagName) {
                "string" -> out[e.getAttribute("name")] = args.sorted().joinToString(",")
                // Cada lengua tiene sus formas (one, many…): se comparan los argumentos sin repetir
                "plurals" -> out["plurals:" + e.getAttribute("name")] = args.distinct().sorted().joinToString(",")
            }
        }
        return out
    }

    private fun check(module: String) {
        val res = resDir(module)
        val base = load(File(res, "values/strings.xml"))
        val problems = res.listFiles().orEmpty().filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }.flatMap { dir ->
            val t = load(File(dir, "strings.xml"))
            (base.keys - t.keys).map { "${dir.name}: falta $it" } +
                (t.keys - base.keys).map { "${dir.name}: sobra $it" } +
                base.filter { (k, a) -> k in t && t[k] != a }.keys.map { "${dir.name}: argumentos distintos en $it" }
        }
        assertTrue("$module:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun phoneTranslationsMatchSpanish() = check("shared")

    @Test fun watchTranslationsMatchSpanish() = check("wear")

    @Test fun watchFaceTranslationsMatchSpanish() = check("watchface")
}
