package dev.cheroliv.codex.tasks

object FontStyleDetector {

    fun detect(fontName: String): FontStyle {
        val name = fontName.lowercase()

        val monospace = name.contains("courier") || name.contains("mono") ||
            name.contains("consolas") || name.contains("typewriter") ||
            name.contains("menlo") || name.contains("monaco") ||
            name.contains("source code") || name.contains("fira code") ||
            name.contains("jetbrains") || name.contains("droid sans mono") ||
            name.contains("dejavu sans mono") || name.contains("liberation mono")

        val bold = !monospace && (name.contains("bold") || name.contains("bd ") ||
            name.contains("heavy") || name.contains("black") ||
            name.matches(Regex(".*bold(mt)?$")))

        val italic = (name.contains("italic") || name.contains("oblique") ||
            name.contains("slanted") || name.contains("it ") ||
            name.matches(Regex(".*italic(mt)?$")))

        return when {
            monospace -> FontStyle.MONOSPACE
            bold && italic -> FontStyle.BOLD_ITALIC
            bold -> FontStyle.BOLD
            italic -> FontStyle.ITALIC
            else -> FontStyle.NORMAL
        }
    }
}

enum class FontStyle {
    NORMAL, BOLD, ITALIC, BOLD_ITALIC, MONOSPACE;

    fun isMonospace() = this == MONOSPACE
    fun isBold() = this == BOLD || this == BOLD_ITALIC
    fun isItalic() = this == ITALIC || this == BOLD_ITALIC
}
