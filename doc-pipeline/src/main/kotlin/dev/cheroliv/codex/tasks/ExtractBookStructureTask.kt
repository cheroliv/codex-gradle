package dev.cheroliv.codex.tasks

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ExtractBookStructureTask : DefaultTask() {

    @get:InputFile
    abstract val pdfFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = pdfFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] extractBookStructure : ${input.name} → ${output.name}")

        val adocContent = buildAsciiDoc(input)

        output.writeText(adocContent)
        logger.lifecycle(
            "[codex] ✓ Structure extraite — ${adocContent.lines().size} lignes AsciiDoc"
        )
    }

    private fun buildAsciiDoc(pdfFile: java.io.File): String {
        val positionedLines = extractTextPositions(pdfFile)

        if (positionedLines.isEmpty()) return "= [Document vide]\n\n"

        val nonCode = positionedLines.filter { it.fontStyle != FontStyle.MONOSPACE }
        val sizes = nonCode.map { it.fontSize }
        val avg = sizes.average()
        val max = sizes.maxOrNull() ?: avg
        val min = sizes.minOrNull() ?: avg
        val range = max - min

        val headerThresholds = computeHeaderThresholds(avg, max, min, range)

        logger.lifecycle(
            "[codex] Fontes: avg=${"%.1f".format(avg)}, max=${"%.1f".format(max)}, " +
                "h1≥${"%.1f".format(headerThresholds.h1)}, h2≥${"%.1f".format(headerThresholds.h2)}, " +
                "h3≥${"%.1f".format(headerThresholds.h3)}, h4≥${"%.1f".format(headerThresholds.h4)}"
        )

        val groups = groupTextPositionsByY(positionedLines)

        return buildAsciiDocFromGroups(groups, headerThresholds)
    }

    private data class HeaderThresholds(
        val h1: Double, val h2: Double, val h3: Double, val h4: Double
    )

    private fun computeHeaderThresholds(
        avg: Double, max: Double, min: Double, range: Double
    ): HeaderThresholds {
        if (range <= 0.5) {
            return HeaderThresholds(
                h1 = max + 0.1,
                h2 = max + 0.1,
                h3 = max + 0.1,
                h4 = max + 0.1
            )
        }

        return HeaderThresholds(
            h1 = max * 0.95,
            h2 = max * 0.75,
            h3 = max * 0.50,
            h4 = max * 0.40
        )
    }

    private fun extractTextPositions(pdfFile: java.io.File): List<PositionedLine> {
        Loader.loadPDF(pdfFile).use { document ->
            val allLines = mutableListOf<PositionedLine>()

            for (pageIndex in 0 until document.numberOfPages) {
                val stripper = object : PDFTextStripper() {
                    val linesForPage = mutableListOf<PositionedLine>()

                    init {
                        sortByPosition = true
                        lineSeparator = "\n"
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }

                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        if (text.isBlank()) return

                        val y = textPositions.firstOrNull()?.yDirAdj?.toDouble() ?: 0.0
                        val x = textPositions.firstOrNull()?.xDirAdj?.toDouble() ?: 0.0
                        val maxFontSize = textPositions.maxOfOrNull {
                            it.fontSizeInPt.toDouble()
                        } ?: 0.0
                        val fontName = textPositions.firstOrNull()?.font?.name

                        linesForPage.add(
                            PositionedLine(
                                x = x,
                                y = y,
                                fontSize = maxFontSize,
                                text = text.replace("\\s+".toRegex(), " ").trim(),
                                fontName = fontName,
                                fontStyle = FontStyleDetector.detect(fontName ?: "")
                            )
                        )
                    }
                }

                stripper.getText(document)
                allLines.addAll(stripper.linesForPage)

                logger.lifecycle(
                    "[codex] Page ${pageIndex + 1}/${document.numberOfPages}: " +
                        "${stripper.linesForPage.size} fragments texte"
                )
            }

            return allLines
        }
    }

    private fun groupTextPositionsByY(
        positions: List<PositionedLine>
    ): List<LineGroup> {
        return positions.map { pos ->
            LineGroup(
                y = pos.y,
                x = pos.x,
                text = pos.text,
                maxFontSize = pos.fontSize,
                fontStyle = pos.fontStyle
            )
        }.filter { it.text.isNotBlank() }
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildAsciiDocFromGroups(
        groups: List<LineGroup>,
        thresholds: HeaderThresholds
    ): String {
        val sb = StringBuilder()

        sb.appendLine("= Structure extraite du PDF")
        sb.appendLine()

        sb.appendLine("[NOTE]")
        sb.appendLine("====")
        sb.appendLine(
            "Document généré automatiquement par extractBookStructure. " +
                "Hiérarchie basée sur heuristique typographique " +
                "(taille de police + graisse + détection code)."
        )
        sb.appendLine("====")
        sb.appendLine()

        var pendingBlanks = 0
        var codeLines = mutableListOf<String>()
        var inCodeBlock = false

        fun flushCodeBlock() {
            if (codeLines.size >= 2) {
                sb.appendLine("[source,text]")
                sb.appendLine("----")
                codeLines.forEach { sb.appendLine(it) }
                sb.appendLine("----")
                sb.appendLine()
            } else if (codeLines.size == 1) {
                sb.appendLine("`" + codeLines[0] + "`")
                sb.appendLine()
            }
            codeLines.clear()
            inCodeBlock = false
        }

        fun closeCodeBlockIfNeeded() {
            if (inCodeBlock) flushCodeBlock()
        }

        for (group in groups) {
            val text = group.text
            val fontSize = group.maxFontSize
            val style = group.fontStyle

            val isMonospace = style.isMonospace()
            val isBold = style.isBold() && !isMonospace

            if (isMonospace) {
                if (!inCodeBlock) {
                    closeCodeBlockIfNeeded()
                    inCodeBlock = true
                }
                codeLines.add(text)
                continue
            }

            if (inCodeBlock) flushCodeBlock()

            if (text.isBlank()) {
                pendingBlanks++
                continue
            }

            val headerLevel = when {
                fontSize >= thresholds.h1 && isBold -> 1
                fontSize >= thresholds.h1 -> 2
                fontSize >= thresholds.h2 && isBold -> 2
                fontSize >= thresholds.h2 -> 3
                fontSize >= thresholds.h3 && isBold -> 3
                else -> 0
            }

            if (pendingBlanks > 0) {
                sb.appendLine()
                pendingBlanks = 0
            }

            when (headerLevel) {
                1 -> {
                    sb.appendLine()
                    sb.appendLine("= " + text)
                    sb.appendLine()
                    pendingBlanks = 0
                }
                2 -> {
                    sb.appendLine()
                    sb.appendLine("== " + text)
                    sb.appendLine()
                    pendingBlanks = 0
                }
                3 -> {
                    sb.appendLine()
                    sb.appendLine("=== " + text)
                    sb.appendLine()
                    pendingBlanks = 0
                }
                4 -> {
                    sb.appendLine()
                    sb.appendLine("==== " + text)
                    sb.appendLine()
                    pendingBlanks = 0
                }
                else -> {
                    sb.appendLine(text)
                }
            }
        }

        closeCodeBlockIfNeeded()

        return sb.toString().trimEnd() + "\n"
    }

    data class PositionedLine(
        val x: Double,
        val y: Double,
        val fontSize: Double,
        val text: String,
        val fontName: String? = null,
        val fontStyle: FontStyle = FontStyle.NORMAL
    )

    data class LineGroup(
        val y: Double,
        val x: Double,
        val text: String,
        val maxFontSize: Double,
        val fontStyle: FontStyle = FontStyle.NORMAL
    )
}
