package dev.cheroliv.codex.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

@Serializable
data class DocumentChunk(
    val id: String,
    val sourceDocument: String,
    val sectionPath: String,
    val headingLevel: Int,
    val content: String,
    val codeBlocks: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val overlapNext: String? = null,
    val license: String = "UNKNOWN"
)

abstract class ChunkDocumentTask : DefaultTask() {

    private data class Section(
        val headingLine: String,
        val headingLevel: Int,
        val headingText: String,
        val contentLines: MutableList<String> = mutableListOf()
    )

    @get:InputFile
    abstract val markdownFile: RegularFileProperty

    @get:OutputFile
    abstract val chunksFile: RegularFileProperty

    @TaskAction
    fun chunk() {
        val input = markdownFile.asFile.get()
        val output = chunksFile.asFile.get()

        logger.lifecycle("[codex] chunkDocument : ${input.name} → ${output.name}")

        val sourceDocument = input.nameWithoutExtension
        val text = input.readText()
        val chunks = buildChunks(text, sourceDocument)

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        output.writeText(json.encodeToString(chunks))

        logger.lifecycle(
            "[codex] ✓ Chunking terminé — ${chunks.size} chunks produits " +
                "(${chunks.map { it.content.lines().size }.sum()} lignes totales)"
        )
    }

    private fun buildChunks(text: String, sourceDocument: String): List<DocumentChunk> {
        val lines = text.lines()
        val headingPattern = Regex("""^(#{1,6})\s+(.+)$""")

        val sections = mutableListOf<Section>()
        var currentSection: Section? = null
        val pendingLines = mutableListOf<String>()

        for (line in lines) {
            val match = headingPattern.find(line)
            if (match != null) {
                if (currentSection != null) {
                    mergePendingIntoContent(pendingLines, currentSection)
                    sections.add(currentSection)
                }
                val level = match.groupValues[1].length
                val title = match.groupValues[2].trim()
                currentSection = Section(
                    headingLine = line,
                    headingLevel = level,
                    headingText = title
                )
                pendingLines.clear()
            } else {
                pendingLines.add(line)
            }
        }

        if (currentSection != null) {
            mergePendingIntoContent(pendingLines, currentSection)
            sections.add(currentSection)
        } else if (pendingLines.isNotEmpty()) {
            sections.add(
                Section(
                    headingLine = "",
                    headingLevel = 0,
                    headingText = sourceDocument,
                    contentLines = pendingLines.toMutableList()
                )
            )
        }

        val chunks = mutableListOf<DocumentChunk>()

        for (i in sections.indices) {
            val section = sections[i]
            val content = buildContent(section)
            if (content.isBlank()) continue

            val codeBlocks = extractCodeBlocks(section.contentLines)
            val sectionPath = buildSectionPath(sections, i)

            val nextContent = if (i + 1 < sections.size) {
                extractFirstTwoSentences(sections[i + 1].contentLines)
            } else null

            val id = generateChunkId(sourceDocument, sectionPath)

            chunks.add(
                DocumentChunk(
                    id = id,
                    sourceDocument = sourceDocument,
                    sectionPath = sectionPath,
                    headingLevel = section.headingLevel,
                    content = content,
                    codeBlocks = codeBlocks,
                    entities = emptyList(),
                    overlapNext = nextContent,
                    license = "UNKNOWN"
                )
            )
        }

        return chunks
    }

    private fun mergePendingIntoContent(pendingLines: MutableList<String>, section: Section) {
        val trimmed = trimLeadingAndTrailingBlanks(pendingLines)
        section.contentLines.addAll(trimmed)
    }

    private fun trimLeadingAndTrailingBlanks(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { it.isNotBlank() }
        val end = lines.indexOfLast { it.isNotBlank() }
        if (start == -1) return emptyList()
        return lines.subList(start, end + 1)
    }

    private fun buildContent(section: Section): String {
        val lines = mutableListOf<String>()
        lines.add(section.headingLine)
        section.contentLines.forEach { lines.add(it) }
        return lines.joinToString("\n").trim()
    }

    private fun extractCodeBlocks(lines: List<String>): List<String> {
        val blocks = mutableListOf<String>()
        var inCode = false
        val currentBlock = StringBuilder()

        for (line in lines) {
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    blocks.add(currentBlock.toString().trimEnd())
                    currentBlock.clear()
                }
                inCode = !inCode
            } else if (inCode) {
                currentBlock.appendLine(line)
            }
        }
        return blocks
    }

    private fun buildSectionPath(sections: List<Section>, currentIndex: Int): String {
        val path = mutableListOf<String>()
        val target = sections[currentIndex]

        path.add(0, target.headingText)

        for (j in (currentIndex - 1) downTo 0) {
            val ancestor = sections[j]
            if (ancestor.headingLevel < target.headingLevel && ancestor.headingLevel > 0) {
                path.add(0, ancestor.headingText)
                if (ancestor.headingLevel == 1) break
            }
        }

        return path.joinToString(" > ")
    }

    private fun extractFirstTwoSentences(lines: List<String>): String? {
        val textLines = lines.filter { l ->
            val t = l.trim()
            !t.startsWith("#") && !t.startsWith("```") && t.isNotBlank()
        }
        if (textLines.isEmpty()) return null

        val combined = textLines.take(3).joinToString(" ")
        val sentences = combined.split(Regex("(?<=[.!?])\\s+"))
        val result = sentences.take(2).joinToString(" ").trim()
        return result.ifBlank { null }
    }

    private fun generateChunkId(source: String, sectionPath: String): String {
        val input = "$source:$sectionPath"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray()).take(8).joinToString("") {
            "%02x".format(it)
        }
        return "chk-$hash"
    }
}
