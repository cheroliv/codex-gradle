package dev.cheroliv.codex.tasks

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

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

        val lines = extractLines(input)
        val adocContent = buildAsciiDoc(lines)

        output.writeText(adocContent)
        logger.lifecycle(
            "[codex] Done — Structure extraite : ${adocContent.lines().size} lignes AsciiDoc"
        )
    }

    private fun extractLines(pdfFile: File): List<StructuredLine> {
        Loader.loadPDF(pdfFile).use { document ->
            val allLines = mutableListOf<StructuredLine>()

            for (pageIndex in 0 until document.numberOfPages) {
                val linesForPage = mutableListOf<StructuredLine>()

                val stripper = object : PDFTextStripper() {

                    init {
                        sortByPosition = true
                        lineSeparator = "\n"
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }

                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        if (text.isBlank()) return

                        val y = textPositions.firstOrNull()?.yDirAdj?.toDouble() ?: 0.0
                        val startX = textPositions.firstOrNull()?.xDirAdj?.toDouble() ?: 0.0

                        val maxFontSize = textPositions.maxOfOrNull {
                            it.fontSizeInPt.toDouble()
                        } ?: 0.0

                        val fontName = textPositions.firstOrNull()?.font?.name

                        linesForPage.add(
                            StructuredLine(
                                page = pageIndex + 1,
                                y = y,
                                x = startX,
                                fontSize = maxFontSize,
                                text = text.replace("\\s+".toRegex(), " ").trim(),
                                fontName = fontName
                            )
                        )
                    }
                }

                stripper.getText(document)
                allLines.addAll(linesForPage)

                if (document.numberOfPages <= 50 || pageIndex == 0 ||
                    pageIndex == document.numberOfPages - 1 ||
                    (pageIndex + 1) % 20 == 0
                ) {
                    logger.lifecycle(
                        "[codex] Page ${pageIndex + 1}/${document.numberOfPages}: " +
                            "${linesForPage.size} lignes"
                    )
                }
            }

            return allLines
        }
    }

    private fun buildAsciiDoc(lines: List<StructuredLine>): String {
        if (lines.isEmpty()) return "= [Document vide]\n\n"

        val sizes = lines.map { it.fontSize }
        val avg = sizes.average()
        val max = sizes.maxOrNull() ?: avg
        val range = max - avg

        val headerThreshold = if (range > 0) avg + range * 0.25 else avg + 1.0
        val subHeaderThreshold = if (range > 0) avg + range * 0.10 else avg

        logger.lifecycle(
            "[codex] Fontes: avg=%.1f, max=%.1f, header>=%.1f, sub>=%.1f".format(
                avg, max, headerThreshold, subHeaderThreshold
            )
        )

        val sb = StringBuilder()
        sb.appendLine("= Structure extraite du PDF")
        sb.appendLine()
        sb.appendLine("[NOTE]")
        sb.appendLine("====")
        sb.appendLine("Généré automatiquement par extractBookStructure. Hiérarchie basée sur heuristique typographique.")
        sb.appendLine("====")
        sb.appendLine()

        var pendingBlanks = 0

        for (line in lines) {
            val fontSize = line.fontSize
            val text = line.text

            if (text.isBlank()) {
                pendingBlanks++
                continue
            }

            val isHeader = fontSize >= headerThreshold
            val isSubHeader = fontSize >= subHeaderThreshold && !isHeader

            if (pendingBlanks > 0) {
                sb.appendLine()
                pendingBlanks = 0
            }

            when {
                isHeader -> {
                    sb.appendLine()
                    sb.appendLine("== $text")
                    sb.appendLine()
                    pendingBlanks = 0
                }
                isSubHeader -> {
                    sb.appendLine()
                    sb.appendLine("=== $text")
                    sb.appendLine()
                    pendingBlanks = 0
                }
                else -> {
                    sb.appendLine(text)
                }
            }
        }

        return sb.toString()
    }

    data class StructuredLine(
        val page: Int,
        val y: Double,
        val x: Double,
        val fontSize: Double,
        val text: String,
        val fontName: String? = null
    )
}
