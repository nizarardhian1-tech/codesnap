package code.editor.mon

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

object SyntaxHighlighter {

    private val COLOR_KEYWORD    = Color.parseColor("#569CD6")
    private val COLOR_STRING     = Color.parseColor("#CE9178")
    private val COLOR_COMMENT    = Color.parseColor("#6A9955")
    private val COLOR_NUMBER     = Color.parseColor("#B5CEA8")
    private val COLOR_ANNOTATION = Color.parseColor("#DCDCAA")
    private val COLOR_XML_TAG    = Color.parseColor("#4EC9B0")
    private val COLOR_XML_ATTR   = Color.parseColor("#9CDCFE")

    private val KOTLIN_KEYWORDS = setOf(
        "fun", "val", "var", "class", "object", "interface", "if", "else", "when",
        "for", "while", "do", "return", "import", "package", "true", "false", "null",
        "this", "super", "override", "private", "public", "protected", "internal",
        "companion", "data", "sealed", "abstract", "open", "final", "suspend",
        "inline", "by", "in", "is", "as", "throw", "try", "catch", "finally",
        "constructor", "init", "get", "set", "lateinit", "const", "lazy"
    )

    private val JAVA_KEYWORDS = setOf(
        "public", "private", "protected", "class", "interface", "extends", "implements",
        "import", "package", "return", "void", "if", "else", "for", "while", "do",
        "new", "this", "super", "static", "final", "abstract", "try", "catch",
        "finally", "throw", "throws", "instanceof", "true", "false", "null",
        "int", "long", "float", "double", "boolean", "char", "byte", "short"
    )

    private val GRADLE_KEYWORDS = setOf(
        "plugins", "android", "dependencies", "implementation", "apply", "id",
        "version", "defaultConfig", "buildTypes", "release", "debug", "minSdk",
        "targetSdk", "compileSdk", "versionCode", "versionName", "minifyEnabled",
        "classpath", "repositories", "google", "mavenCentral", "include"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "import", "from", "return", "if", "elif", "else",
        "for", "while", "in", "is", "not", "and", "or", "True", "False", "None",
        "try", "except", "finally", "with", "as", "pass", "break", "continue",
        "lambda", "yield"
    )

    fun highlight(text: String, fileExt: String): SpannableString {
        val sp = SpannableString(text)
        return when (fileExt.lowercase()) {
            "xml"                     -> highlightXml(text, sp)
            "kt"                      -> highlightCode(text, sp, KOTLIN_KEYWORDS)
            "java"                    -> highlightCode(text, sp, JAVA_KEYWORDS)
            "gradle", "kts", "groovy" -> highlightCode(text, sp, GRADLE_KEYWORDS)
            "py"                      -> highlightCode(text, sp, PYTHON_KEYWORDS)
            "json"                    -> highlightJson(text, sp)
            else                      -> highlightCode(text, sp, KOTLIN_KEYWORDS)
        }
    }

    private fun color(sp: SpannableString, start: Int, end: Int, col: Int) {
        if (start >= 0 && end <= sp.length && start < end)
            sp.setSpan(ForegroundColorSpan(col), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun highlightCode(text: String, sp: SpannableString, keywords: Set<String>): SpannableString {
        val len = text.length
        var i = 0
        while (i < len) {
            val ch = text[i]
            when {
                ch == '/' && i + 1 < len && text[i + 1] == '/' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    color(sp, i, end, COLOR_COMMENT); i = end
                }
                ch == '/' && i + 1 < len && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                    color(sp, i, end, COLOR_COMMENT); i = end
                }
                ch == '#' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    color(sp, i, end, COLOR_COMMENT); i = end
                }
                ch == '"' -> {
                    var j = i + 1
                    while (j < len && text[j] != '"') { if (text[j] == '\\') j++; j++ }
                    val end = if (j < len) j + 1 else len
                    color(sp, i, end, COLOR_STRING); i = end
                }
                ch == '\'' -> {
                    var j = i + 1
                    while (j < len && text[j] != '\'') { if (text[j] == '\\') j++; j++ }
                    val end = if (j < len) j + 1 else len
                    color(sp, i, end, COLOR_STRING); i = end
                }
                ch == '@' -> {
                    var j = i + 1
                    while (j < len && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    color(sp, i, j, COLOR_ANNOTATION); i = j
                }
                ch.isDigit() && (i == 0 || !text[i - 1].isLetter()) -> {
                    var j = i + 1
                    while (j < len && (text[j].isDigit() || text[j] == '.' || text[j] == 'L' || text[j] == 'f')) j++
                    color(sp, i, j, COLOR_NUMBER); i = j
                }
                ch.isLetter() || ch == '_' -> {
                    var j = i + 1
                    while (j < len && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    if (text.substring(i, j) in keywords) color(sp, i, j, COLOR_KEYWORD)
                    i = j
                }
                else -> i++
            }
        }
        return sp
    }

    private fun highlightXml(text: String, sp: SpannableString): SpannableString {
        var idx = 0
        while (true) {
            val s = text.indexOf("<!--", idx); if (s == -1) break
            val e = text.indexOf("-->", s + 4).let { if (it == -1) text.length else it + 3 }
            color(sp, s, e, COLOR_COMMENT); idx = e
        }
        Regex("</?[a-zA-Z][a-zA-Z0-9._:]*").findAll(text).forEach { m ->
            color(sp, m.range.first, m.range.last + 1, COLOR_XML_TAG)
        }
        Regex("[a-zA-Z][a-zA-Z0-9:_]*(?=\\s*=)").findAll(text).forEach { m ->
            color(sp, m.range.first, m.range.last + 1, COLOR_XML_ATTR)
        }
        Regex("\"[^\"]*\"|'[^']*'").findAll(text).forEach { m ->
            color(sp, m.range.first, m.range.last + 1, COLOR_STRING)
        }
        return sp
    }

    private fun highlightJson(text: String, sp: SpannableString): SpannableString {
        Regex("\"[^\"]+\"\\s*:").findAll(text).forEach { m ->
            val end = m.value.lastIndexOf('"') + m.range.first + 1
            color(sp, m.range.first, end, COLOR_XML_ATTR)
        }
        Regex(":\\s*\"[^\"]*\"").findAll(text).forEach { m ->
            val s = m.value.indexOf('"') + m.range.first
            color(sp, s, m.range.last + 1, COLOR_STRING)
        }
        Regex(":\\s*-?\\d+\\.?\\d*").findAll(text).forEach { m ->
            val s = m.value.indexOfFirst { c -> c.isDigit() || c == '-' } + m.range.first
            color(sp, s, m.range.last + 1, COLOR_NUMBER)
        }
        return sp
    }
}
