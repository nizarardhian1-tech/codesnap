package code.editor.mon

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

object SyntaxHighlighter {

    // Fungsi baru yang menerima Editable agar tidak boros memori
    fun applyHighlighting(s: Editable) {
        // 1. Bersihkan warna lama
        val oldSpans = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
        for (span in oldSpans) s.removeSpan(span)

        // 2. Daftar pola (Regex) dan warnanya
        val patterns = mapOf(
            // Keywords (Ungu)
            "\\b(class|fun|val|var|if|else|return|import|package|while|for|try|catch|new|private|public|protected|override)\\b" to "#BB86FC",
            // Komentar (Abu-abu)
            "//.*|/\\*[\\s\\S]*?\\*/" to "#6272A4",
            // String (Kuning)
            "\".*?\"" to "#F1FA8C",
            // Angka (Biru Muda)
            "\\b[0-9]+\\b" to "#BD93F9"
        )

        // 3. Terapkan warna
        for ((pattern, color) in patterns) {
            val p = Pattern.compile(pattern)
            val m = p.matcher(s)
            while (m.find()) {
                s.setSpan(
                    ForegroundColorSpan(Color.parseColor(color)),
                    m.start(),
                    m.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    // Tetap sediakan fungsi lama untuk inisialisasi awal jika diperlukan
    fun highlight(text: String, ext: String): Spannable {
        val spannable = Spannable.Factory.getInstance().newSpannable(text)
        // Anda bisa memanggil applyHighlighting(spannable as Editable) di sini
        return spannable
    }
}