package dev.cheroliv.codex.tasks

import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.ToXMLContentHandler
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

@Serializable
data class PipelineResult(
    val sourceDocument: String,
    val format: String,
    val license: String,
    val totalDocuments: Int,
    val totalChunks: Int,
    val output: String
)

abstract class CodexPipelineTask : DefaultTask() {

    @get:InputFile
    abstract val sourceFile: RegularFileProperty

    @get:Optional
    @get:Input
    abstract val licenseName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val pgHost: Property<String>

    @get:Input
    abstract val pgPort: Property<String>

    @get:Input
    abstract val pgDatabase: Property<String>

    @get:Input
    abstract val pgUser: Property<String>

    @get:Input
    abstract val pgPassword: Property<String>

    @get:Optional
    @get:Input
    abstract val batchSize: Property<String>

    @TaskAction
    fun pipeline() {
        val file = sourceFile.asFile.get()
        val output = outputFile.asFile.get()
        val name = file.name.lowercase()
        val format = when {
            name.endsWith(".pdf") -> "PDF"
            name.endsWith(".epub") -> "EPUB"
            else -> "UNKNOWN"
        }

        logger.lifecycle("[codex] codexPipeline : ${file.name} (format=$format) → ${output.name}")

        if (format == "UNKNOWN") {
            logger.warn("[codex] Format inconnu pour ${file.name} — abandon")
            output.writeText("""{"error":"unknown format"}""")
            return
        }

        val adocContent = when (format) {
            "PDF" -> extractPdf(file)
            "EPUB" -> extractEpub(file)
            else -> return
        }

        val mdContent = convertToMd(adocContent)
        val license = licenseName.orNull ?: "UNKNOWN"
        val chunks = chunkMd(mdContent, file.nameWithoutExtension, license)

        val result = PipelineResult(
            sourceDocument = file.name,
            format = format,
            license = license,
            totalDocuments = 1,
            totalChunks = chunks.size,
            output = output.absolutePath
        )

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val outputJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
        output.writeText(outputJson.encodeToString(result))

        logger.lifecycle("[codex] ✓ codexPipeline termine — ${chunks.size} chunks, format=$format")
    }

    private fun extractPdf(pdfFile: java.io.File): String {
        Loader.loadPDF(pdfFile).use { document ->
            val allLines = mutableListOf<ExtractBookStructureTask.PositionedLine>()
            for (pageIndex in 0 until document.numberOfPages) {
                val stripper = object : PDFTextStripper() {
                    val linesForPage = mutableListOf<ExtractBookStructureTask.PositionedLine>()
                    init { sortByPosition = true; lineSeparator = "\n"; startPage = pageIndex + 1; endPage = pageIndex + 1 }
                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        if (text.isBlank()) return
                        val y = textPositions.firstOrNull()?.yDirAdj?.toDouble() ?: 0.0
                        val x = textPositions.firstOrNull()?.xDirAdj?.toDouble() ?: 0.0
                        val fs = textPositions.maxOfOrNull { it.fontSizeInPt.toDouble() } ?: 0.0
                        val fn = textPositions.firstOrNull()?.font?.name
                        linesForPage.add(ExtractBookStructureTask.PositionedLine(
                            x, y, fs, text.replace("\\s+".toRegex(), " ").trim(), fn,
                            FontStyleDetector.detect(fn ?: "")
                        ))
                    }
                }
                stripper.getText(document)
                allLines.addAll(stripper.linesForPage)
            }
            return buildPdfAdoc(allLines)
        }
    }

    private fun buildPdfAdoc(lines: List<ExtractBookStructureTask.PositionedLine>): String {
        if (lines.isEmpty()) return "= [Document vide]\n\n"
        val nonCode = lines.filter { it.fontStyle != FontStyle.MONOSPACE }
        val sizes = nonCode.map { it.fontSize }
        val max = sizes.maxOrNull() ?: 0.0
        val min = sizes.minOrNull() ?: 0.0
        val range = max - min
        val h1 = if (range <= 0.5) max + 0.1 else max * 0.95
        val h2 = if (range <= 0.5) max + 0.1 else max * 0.75
        val h3 = if (range <= 0.5) max + 0.1 else max * 0.50

        val sb = StringBuilder()
        sb.appendLine("= Structure extraite du PDF")
        sb.appendLine()
        sb.appendLine("[NOTE]")
        sb.appendLine("====")
        sb.appendLine("Document genere automatiquement — pipeline auto-detection PDF/EPUB.")
        sb.appendLine("====")
        sb.appendLine()

        var pending = 0; val codeLines = mutableListOf<String>(); var inCode = false

        fun flush() {
            if (codeLines.isNotEmpty()) {
                sb.appendLine("[source,text]")
                sb.appendLine("----")
                codeLines.forEach { sb.appendLine(it) }
                sb.appendLine("----")
                sb.appendLine()
            }
            codeLines.clear(); inCode = false
        }

        for (line in lines) {
            val isMono = line.fontStyle.isMonospace()
            val isBold = line.fontStyle.isBold() && !isMono
            if (isMono) { if (!inCode) { flush(); inCode = true }; codeLines.add(line.text); continue }
            if (inCode) flush()
            if (line.text.isBlank()) { pending++; continue }
            val lvl = when { line.fontSize >= h1 && isBold -> 1; line.fontSize >= h1 -> 2; line.fontSize >= h2 && isBold -> 2; line.fontSize >= h2 -> 3; line.fontSize >= h3 && isBold -> 3; else -> 0 }
            if (pending > 0) { sb.appendLine(); pending = 0 }
            when (lvl) { 1 -> { sb.appendLine(); sb.appendLine("= ${line.text}"); sb.appendLine(); pending = 0 }; 2 -> { sb.appendLine(); sb.appendLine("== ${line.text}"); sb.appendLine(); pending = 0 }; 3 -> { sb.appendLine(); sb.appendLine("=== ${line.text}"); sb.appendLine(); pending = 0 }; else -> sb.appendLine(line.text) }
        }
        if (inCode) flush()
        return sb.toString().trimEnd() + "\n"
    }

    private fun extractEpub(epubFile: java.io.File): String {
        val parser = AutoDetectParser()
        val handler = ToXMLContentHandler()
        epubFile.inputStream().use { parser.parse(it, handler, Metadata(), ParseContext()) }
        val xhtml = handler.toString()
        if (xhtml.isBlank()) return "= [EPUB vide]\n\n"

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(xhtml.byteInputStream())
        val body = findElement(doc.documentElement, "body") ?: return "= [EPUB vide]\n\n"

        val sb = StringBuilder()
        sb.appendLine("= Structure extraite de l'EPUB")
        sb.appendLine()
        sb.appendLine("[NOTE]")
        sb.appendLine("====")
        sb.appendLine("Document genere automatiquement — pipeline auto-detection PDF/EPUB.")
        sb.appendLine("====")
        sb.appendLine()
        traverse(body, sb)
        return sb.toString().trimEnd() + "\n"
    }

    private fun findElement(node: Node, tagName: String): Element? {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val el = node as Element
            if (el.localName.equals(tagName, ignoreCase = true)) return el
            for (i in 0 until el.childNodes.length) {
                findElement(el.childNodes.item(i), tagName)?.let { return it }
            }
        }
        return null
    }

    private fun traverse(element: Element, sb: StringBuilder) {
        for (i in 0 until element.childNodes.length) traverseNode(element.childNodes.item(i), sb)
    }

    private fun traverseNode(node: Node, sb: StringBuilder) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val el = node as Element
                when (el.localName.lowercase()) {
                    "h1" -> { sb.appendLine(); sb.appendLine("= ${el.textContent.trim()}"); sb.appendLine() }
                    "h2" -> { sb.appendLine(); sb.appendLine("== ${el.textContent.trim()}"); sb.appendLine() }
                    "h3" -> { sb.appendLine(); sb.appendLine("=== ${el.textContent.trim()}"); sb.appendLine() }
                    "h4" -> { sb.appendLine(); sb.appendLine("==== ${el.textContent.trim()}"); sb.appendLine() }
                    "h5" -> { sb.appendLine(); sb.appendLine("===== ${el.textContent.trim()}"); sb.appendLine() }
                    "h6" -> { sb.appendLine(); sb.appendLine("====== ${el.textContent.trim()}"); sb.appendLine() }
                    "pre" -> {
                        val code = el.textContent.trimEnd()
                        if (code.isNotBlank()) { sb.appendLine("[source,text]"); sb.appendLine("----"); code.lines().forEach { sb.appendLine(it) }; sb.appendLine("----"); sb.appendLine() }
                    }
                    "p", "div" -> sb.appendLine(el.textContent.trim())
                    else -> traverse(el, sb)
                }
            }
            Node.TEXT_NODE -> {
                val txt = node.textContent.trim()
                if (txt.isNotBlank()) sb.appendLine(txt)
            }
        }
    }

    private fun convertToMd(adoc: String): String {
        val lines = adoc.lines()
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
            inCodeBlock = false; codeLanguage = "text"
        }

        var pendingBlank = 0

        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//")) continue
            if (inCodeBlock) {
                if (trimmed == "----") { flushCodeBlock(); pendingBlank = 0 } else codeBlockCollector.add(line)
                continue
            }
            if (trimmed == "----") continue
            if (trimmed.startsWith("[source,")) {
                flushCodeBlock()
                codeLanguage = Regex("""\[source,(\w+)]""").find(trimmed)?.groupValues?.get(1) ?: "text"
                inCodeBlock = true; codeBlockCollector.clear()
                continue
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.getOrNull(1) != null && trimmed[1].isUpperCase()) {
                val label = trimmed.removeSurrounding("[", "]").lowercase()
                sb.appendLine("> **${label.replaceFirstChar { it.uppercase() }}**"); pendingBlank = 0; continue
            }
            if (trimmed == "====") { sb.appendLine(); pendingBlank = 0; continue }

            val headingRegex = Regex("""^(=+)\s+(.+)$""")
            val headingMatch = headingRegex.find(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val title = headingMatch.groupValues[2]
                val prefix = "#".repeat(level.coerceAtMost(6))
                if (pendingBlank > 0) sb.appendLine()
                sb.appendLine("$prefix $title"); sb.appendLine(); pendingBlank = 0; continue
            }

            if (trimmed.isBlank()) { pendingBlank++; continue }
            if (pendingBlank >= 2) sb.appendLine()
            sb.appendLine(trimmed); pendingBlank = 0
        }
        flushCodeBlock()
        return sb.toString().trimEnd() + "\n"
    }

    private fun chunkMd(md: String, sourceDocument: String, license: String): List<DocumentChunk> {
        val lines = md.lines()
        val headingPattern = Regex("""^(#{1,6})\s+(.+)$""")
        val sections = mutableListOf<Pair<Int, String>>()
        val pendingLines = mutableListOf<String>()
        var currentLevel = 0
        var currentTitle = sourceDocument

        for (line in lines) {
            val match = headingPattern.find(line)
            if (match != null) {
                if (pendingLines.isNotEmpty()) {
                    sections.add(currentLevel to pendingLines.joinToString("\n"))
                    pendingLines.clear()
                }
                currentLevel = match.groupValues[1].length
                currentTitle = match.groupValues[2].trim()
                pendingLines.add(line)
            } else {
                pendingLines.add(line)
            }
        }
        if (pendingLines.isNotEmpty()) sections.add(currentLevel to pendingLines.joinToString("\n"))

        return sections.mapIndexed { i, (level, content) ->
            if (content.isBlank()) return@mapIndexed null
            val id = "chk-${MessageDigest.getInstance("SHA-256")
                .digest("$sourceDocument:$i".toByteArray()).take(8)
                .joinToString("") { "%02x".format(it) }}"
            DocumentChunk(id = id, sourceDocument = sourceDocument, sectionPath = content.lines().first().trimStart('#', ' '),
                headingLevel = level, content = content, license = license)
        }.filterNotNull()
    }
}
