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
        val textPositions = extractTextPositions(pdfFile)

        if (textPositions.isEmpty()) return "= [Document vide]\n\n"

        val sizes = textPositions.map { it.fontSize }
        val avg = sizes.average()
        val max = sizes.maxOrNull() ?: avg
        val fontSizeStats = FontSizeStats(avg = avg, max = max)

        val positionedLines = groupTextPositionsByY(textPositions)

        val chapterThreshold =
            fontSizeStats.avg + (fontSizeStats.max - fontSizeStats.avg) * 0.6
        val sectionThreshold =
            fontSizeStats.avg + (fontSizeStats.max - fontSizeStats.avg) * 0.15

        logger.lifecycle(
            "[codex] Fontes: avg=${fontSizeStats.avg}, max=${fontSizeStats.max}, " +
                "chapter≥${chapterThreshold}, section≥${sectionThreshold}"
        )

        return buildAsciiDocFromGroups(positionedLines, chapterThreshold, sectionThreshold)
    }

    private fun extractTextPositions(pdfFile: java.io.File): List<PositionedLine> {
        Loader.loadPDF(pdfFile).use { document ->
            val allPositions = mutableListOf<PositionedLine>()

            for (pageIndex in 0 until document.numberOfPages) {
                val stripper = object : PDFTextStripper() {
                    val positions = mutableListOf<PositionedLine>()

                    override fun processTextPosition(text: TextPosition) {
                        val unicode = text.unicode
                        if (unicode.isBlank()) {
                            return
                        }

                        positions.add(
                            PositionedLine(
                                x = text.xDirAdj.toDouble(),
                                y = text.yDirAdj.toDouble(),
                                fontSize = text.fontSizeInPt.toDouble(),
                                text = unicode,
                                fontName = text.font.name
                            )
                        )
                        super.processTextPosition(text)
                    }

                    override fun getText(document: org.apache.pdfbox.pdmodel.PDDocument): String {
                        sortByPosition = true
                        lineSeparator = "\n"
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                        return super.getText(document)
                    }
                }

                stripper.getText(document)
                allPositions.addAll(stripper.positions)

                logger.lifecycle(
                    "[codex] Page ${pageIndex + 1}/${document.numberOfPages}: " +
                        "${stripper.positions.size} fragments texte"
                )
            }

            return allPositions
        }
    }

    private fun groupTextPositionsByY(
        positions: List<PositionedLine>
    ): List<LineGroup> {
        if (positions.isEmpty()) return emptyList()

        val yTolerance = 3.0

        val sortedPositions = positions.sortedByDescending { it.y }

        val yGroups = mutableListOf<MutableList<PositionedLine>>()

        for (pos in sortedPositions) {
            val lastGroup = yGroups.lastOrNull()
            if (lastGroup != null &&
                kotlin.math.abs(lastGroup.last().y - pos.y) <= yTolerance
            ) {
                lastGroup.add(pos)
            } else {
                yGroups.add(mutableListOf(pos))
            }
        }

        return yGroups.map { group ->
            val sortedByX = group.sortedBy { it.x }

            val lineText = buildString {
                for (i in sortedByX.indices) {
                    val current = sortedByX[i]
                    append(current.text)
                    if (i + 1 < sortedByX.size) {
                        val next = sortedByX[i + 1]
                        val currentEndX = current.x + current.text.length * current.fontSize * 0.45
                        val gap = next.x - currentEndX
                        val spaceThreshold = current.fontSize * 0.2
                        if (gap > spaceThreshold) {
                            append(' ')
                        }
                    }
                }
            }

            val cleanedText = cleanText(lineText)
            val maxFontSize = group.maxOf { it.fontSize }
            LineGroup(
                y = group.first().y,
                x = group.first().x,
                text = cleanedText,
                maxFontSize = maxFontSize
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
        chapterThreshold: Double,
        sectionThreshold: Double
    ): String {
        val sb = StringBuilder()

        sb.appendLine("= Structure extraite du PDF")
        sb.appendLine()

        sb.appendLine("[NOTE]")
        sb.appendLine("====")
        sb.appendLine(
            "Document généré automatiquement par extractBookStructure. " +
                "Hiérarchie basée sur heuristique typographique " +
                "(taille de police > moyenne)."
        )
        sb.appendLine("====")
        sb.appendLine()

        for (group in groups) {
            when {
                group.maxFontSize >= chapterThreshold -> {
                    sb.appendLine()
                    sb.appendLine("== " + group.text)
                    sb.appendLine()
                }

                group.maxFontSize >= sectionThreshold -> {
                    sb.appendLine()
                    sb.appendLine("=== " + group.text)
                    sb.appendLine()
                }

                else -> {
                    sb.appendLine(group.text)
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }

    data class PositionedLine(
        val x: Double,
        val y: Double,
        val fontSize: Double,
        val text: String,
        val fontName: String? = null
    )

    data class LineGroup(
        val y: Double,
        val x: Double,
        val text: String,
        val maxFontSize: Double
    )

    private data class FontSizeStats(
        val avg: Double,
        val max: Double
    )
}
