package dev.cheroliv.codex.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ConvertToMarkdownTask : DefaultTask() {

    @get:InputFile
    abstract val adocFile: RegularFileProperty

    @get:OutputFile
    abstract val markdownFile: RegularFileProperty

    @TaskAction
    fun convert() {
        val input = adocFile.asFile.get()
        val output = markdownFile.asFile.get()

        logger.lifecycle("[codex] convertToMarkdown : ${input.name} → ${output.name}")

        val markdown = buildMarkdown(input)
        output.writeText(markdown)
        logger.lifecycle(
            "[codex] ✓ Conversion Markdown — ${markdown.lines().size} lignes, ${markdown.length} octets"
        )
    }

    private fun buildMarkdown(adoc: java.io.File): String {
        val lines = adoc.readLines()
        val sb = StringBuilder()
        val codeBlockCollector = mutableListOf<String>()
        var inCodeBlock = false
        var codeLanguage = "text"

        fun flushCodeBlock() {
            if (codeBlockCollector.isNotEmpty()) {
                sb.appendLine("```$codeLanguage")
                codeBlockCollector.forEach { sb.appendLine(it) }
                sb.appendLine("```")
                sb.appendLine()
                codeBlockCollector.clear()
            }
            inCodeBlock = false
            codeLanguage = "text"
        }

        var pendingBlank = 0

        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.trim()

            if (trimmed.startsWith("//")) continue

            if (inCodeBlock) {
                if (trimmed == "----") {
                    flushCodeBlock()
                    pendingBlank = 0
                } else {
                    codeBlockCollector.add(line)
                }
                continue
            }

            if (trimmed == "----") continue

            if (trimmed.startsWith("[source,")) {
                flushCodeBlock()
                codeLanguage = extractLanguage(trimmed)
                inCodeBlock = true
                codeBlockCollector.clear()
                continue
            }

            if (trimmed.startsWith("[NOTE]") || trimmed.startsWith("[CAUTION]") ||
                trimmed.startsWith("[WARNING]") || trimmed.startsWith("[TIP]") ||
                trimmed.startsWith("[IMPORTANT]")
            ) {
                val label = trimmed.removeSurrounding("[", "]").lowercase()
                sb.appendLine("> **${label.replaceFirstChar { it.uppercase() }}**")
                pendingBlank = 0
                continue
            }

            if (trimmed == "====") {
                sb.appendLine()
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith("=") && !trimmed.startsWith("==")) {
                val level = trimmed.takeWhile { it == '=' }.length
                val title = trimmed.drop(level).trim()
                val prefix = "#".repeat(level.coerceAtMost(6))
                if (pendingBlank > 0) sb.appendLine()
                sb.appendLine("$prefix $title")
                sb.appendLine()
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith("==")) {
                val level = trimmed.takeWhile { it == '=' }.length
                val title = trimmed.drop(level).trim()
                val prefix = "#".repeat(level.coerceAtMost(6))
                if (pendingBlank > 0) sb.appendLine()
                sb.appendLine("$prefix $title")
                sb.appendLine()
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith(".Table") || trimmed.startsWith(".") && nextLineLooksLikeTable(lines, idx)) {
                sb.appendLine()
                sb.appendLine("**${trimmed.drop(1).trim()}**")
                sb.appendLine()
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith("|===")) {
                sb.appendLine()
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith("|")) {
                val cells = trimmed.split("|")
                    .drop(1)
                    .dropLastWhile { it.isBlank() }
                    .map { it.trim() }
                sb.appendLine("| " + cells.joinToString(" | ") + " |")
                pendingBlank = 0
                continue
            }

            if (trimmed.isBlank()) {
                pendingBlank++
                continue
            }

            if (trimmed.startsWith("image:")) {
                val alt = trimmed.substringAfter("image:").substringBefore("[")
                val path = trimmed.substringAfter("[").substringBefore("]")
                sb.appendLine("![$alt]($path)")
                sb.appendLine()
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith("link:") || trimmed.startsWith("http")) {
                val linkText = trimmed.substringAfter("link:").substringBefore("[")
                val url = trimmed.substringAfter("[")
                    .substringBefore("]")
                    .ifEmpty { linkText.ifEmpty { trimmed } }
                val display = linkText.ifEmpty { url }
                sb.appendLine("[$display]($url)")
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("** ")) {
                val content = trimmed.drop(if (trimmed[0] == '*') 1 else 2).trim()
                if (pendingBlank > 0) sb.appendLine()
                sb.appendLine("- $content")
                pendingBlank = 0
                continue
            }

            if (trimmed.startsWith(".") && !trimmed.startsWith("..")) {
                val content = trimmed.drop(1).trim()
                if (pendingBlank > 0) sb.appendLine()
                sb.appendLine("- $content")
                pendingBlank = 0
                continue
            }

            if (pendingBlank >= 2) sb.appendLine()

            val converted = convertInlineAdocToMarkdown(trimmed)
            sb.appendLine(converted)
            pendingBlank = 0
        }

        flushCodeBlock()

        return sb.toString().trimEnd() + "\n"
    }

    private fun extractLanguage(sourceLine: String): String {
        val match = Regex("""\[source,(\w+)]""").find(sourceLine)
        return match?.groupValues?.get(1) ?: "text"
    }

    private fun nextLineLooksLikeTable(lines: List<String>, idx: Int): Boolean {
        val nextIdx = idx + 1
        if (nextIdx >= lines.size) return false
        val next = lines[nextIdx].trim()
        return next.startsWith("|===")
    }

    private fun convertInlineAdocToMarkdown(text: String): String {
        var result = text

        result = result.replace(Regex("""\*\*(.+?)\*\*""")) { "**${it.groupValues[1]}**" }
        result = result.replace(Regex("""\*(.+?)\*""")) { "*${it.groupValues[1]}*" }
        result = result.replace(Regex("""_(.+?)_""")) { "_${it.groupValues[1]}_" }
        result = result.replace(Regex("""`([^`]+)`""")) { "`${it.groupValues[1]}`" }

        result = result.replace(Regex("""\bpass:\[([^\]]+)]""")) { it.groupValues[1] }

        return result
    }
}
