package code.editor.mon

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

object SyntaxHighlighter {

    private val COLOR_KEYWORD    = Color.parseColor("#569CD6") // blue
    private val COLOR_STRING     = Color.parseColor("#CE9178") // orange
    private val COLOR_COMMENT    = Color.parseColor("#6A9955") // green
    private val COLOR_NUMBER     = Color.parseColor("#B5CEA8") // light green
    private val COLOR_ANNOTATION = Color.parseColor("#DCDCAA") // yellow
    private val COLOR_XML_TAG    = Color.parseColor("#4EC9B0") // cyan
    private val COLOR_XML_ATTR   = Color.parseColor("#9CDCFE") // light blue
    private val COLOR_SYMBOL     = Color.parseColor("#D4D4D4")

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

    fun highlight(text: String, fileExt: String): SpannableString {
        val sp = SpannableString(text)
        return when (fileExt.lowercase()) {
            "xml" -> highlightXml(text, sp)
            "kt"  -> highlightCode(text, sp, KOTLIN_KEYWORDS)
            "java" -> highlightCode(text, sp, JAVA_KEYWORDS)
            "gradle", "kts", "groovy" -> highlightCode(text, sp, GRADLE_KEYWORDS)
            "py"  -> highlightPython(text, sp)
            "json" -> highlightJson(text, sp)
            "md"  -> sp
            else  -> highlightCode(text, sp, KOTLIN_KEYWORDS)
        }
    }

    private fun color(sp: SpannableString, start: Int, end: Int, color: Int) {
        if (start >= 0 && end <= sp.length && start < end)
            sp.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun highlightCode(text: String, sp: SpannableString, keywords: Set<String>): SpannableString {
        val len = text.length
        var i = 0
        while (i < len) {
            when {
                // Single-line comment
                i + 1 < len && text[i] == '/' && text[i+1] == '/' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    color(sp, i, end, COLOR_COMMENT); i = end
                }
                // Multi-line comment
                i + 1 < len && text[i] == '/' && text[i+1] == '*' -> {
                    val end = text.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                    color(sp, i, end, COLOR_COMMENT); i = end
                }
                // String double quote
                text[i] == '"' -> {
                    var j = i + 1
                    while (j < len && text[j] != '"') { if (text[j] == '\\') j++; j++ }
                    val end = if (j < len) j + 1 else len
                    color(sp, i, end, COLOR_STRING); i = end
                }
                // String single quote
                text[i] == '\'' -> {
                    var j = i + 1
                    while (j < len && text[j] != '\'') { if (text[j] == '\\') j++; j++ }
                    val end = if (j < len) j + 1 else len
                    color(sp, i, end, COLOR_STRING); i = end
                }
                // Annotation
                text[i] == '@' -> {
                    var j = i + 1
                    while (j < len && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    color(sp, i, j, COLOR_ANNOTATION); i = j
                }
                // Number
                text[i].isDigit() && (i == 0 || !text[i-1].isLetter()) -> {
                    var j = i + 1
                    while (j < len && (text[j].isDigit() || text[j] == '.' || text[j] == 'L' || text[j] == 'f')) j++
                    color(sp, i, j, COLOR_NUMBER); i = j
                }
                // Word (keyword check)
                text[i].isLetter() || text[i] == '_' -> {
                    var j = i + 1
                    while (j < len && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    val word = text.substring(i, j)
                    if (word in keywords) color(sp, i, j, COLOR_KEYWORD)
                    i = j
                }
                else -> i++
            }
        }
        return sp
    }

    private fun highlightXml(text: String, sp: SpannableString): SpannableString {
        // Comments
        var idx = 0
        while (true) {
            val s = text.indexOf("<!--", idx); if (s == -1) break
            val e = text.indexOf("-->", s + 4).let { if (it == -1) text.length else it + 3 }
            color(sp, s, e, COLOR_COMMENT); idx = e
        }
        // Tags
        val tagRegex = Regex("</?[a-zA-Z][a-zA-Z0-9._:]*")
        tagRegex.findAll(text).forEach { color(sp, it.range.first, it.range.last + 1, COLOR_XML_TAG) }
        // Attributes
        val attrRegex = Regex("[a-zA-Z][a-zA-Z0-9:_]*(?=\s*=)")
        attrRegex.findAll(text).forEach { color(sp, it.range.first, it.range.last + 1, COLOR_XML_ATTR) }
        // Strings
        val strRegex = Regex(""[^"]*"|'[^']*'")
        strRegex.findAll(text).forEach { color(sp, it.range.first, it.range.last + 1, COLOR_STRING) }
        return sp
    }

    private fun highlightPython(text: String, sp: SpannableString): SpannableString {
        val keywords = setOf("def","class","import","from","return","if","elif","else",
            "for","while","in","is","not","and","or","True","False","None","try",
            "except","finally","with","as","pass","break","continue","lambda","yield")
        return highlightCode(text, sp, keywords)
    }

    private fun highlightJson(text: String, sp: SpannableString): SpannableString {
        val keyRegex = Regex(""[^"]+"\s*:")
        keyRegex.findAll(text).forEach {
            val end = it.value.lastIndexOf('"') + it.range.first + 1
            color(sp, it.range.first, end, COLOR_XML_ATTR)
        }
        val strRegex = Regex(":\s*"[^"]*"")
        strRegex.findAll(text).forEach {
            val s = it.value.indexOf('"') + it.range.first
            color(sp, s, it.range.last + 1, COLOR_STRING)
        }
        val numRegex = Regex(":\s*-?\d+\.?\d*")
        numRegex.findAll(text).forEach {
            val s = it.value.indexOfFirst { c -> c.isDigit() || c == '-' } + it.range.first
            color(sp, s, it.range.last + 1, COLOR_NUMBER)
        }
        return sp
    }
}
