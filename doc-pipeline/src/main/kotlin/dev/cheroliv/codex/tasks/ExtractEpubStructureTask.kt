package dev.cheroliv.codex.tasks

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.ToXMLContentHandler
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

abstract class ExtractEpubStructureTask : DefaultTask() {

    @get:InputFile
    abstract val epubFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = epubFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] extractEpubStructure : ${input.name} → ${output.name}")

        val adocContent = buildAsciiDoc(input)

        output.writeText(adocContent)
        logger.lifecycle(
            "[codex] ✓ Structure EPUB extraite — ${adocContent.lines().size} lignes AsciiDoc"
        )
    }

    private fun buildAsciiDoc(epubFile: java.io.File): String {
        val xhtmlContent = extractXhtmlViaTika(epubFile)

        if (xhtmlContent.isBlank()) return "= [EPUB vide]\n\n"

        val document = parseXhtml(xhtmlContent)
        val sb = StringBuilder()

        sb.appendLine("= Structure extraite de l'EPUB")
        sb.appendLine()

        sb.appendLine("[NOTE]")
        sb.appendLine("====")
        sb.appendLine("Document généré automatiquement par extractEpubStructure.")
        sb.appendLine("====")
        sb.appendLine()

        val body = findElement(document.documentElement, "body")
        if (body != null) {
            traverseChildren(body, sb)
        }

        return sb.toString().trimEnd() + "\n"
    }

    private fun extractXhtmlViaTika(epubFile: java.io.File): String {
        val parser = AutoDetectParser()
        val handler = ToXMLContentHandler()
        val metadata = Metadata()
        val context = ParseContext()

        epubFile.inputStream().use { stream ->
            parser.parse(stream, handler, metadata, context)
        }

        return handler.toString()
    }

    private fun parseXhtml(content: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(content.byteInputStream())
    }

    private fun findElement(node: Node, tagName: String): Element? {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            if (element.localName.equals(tagName, ignoreCase = true)) return element
            val children = element.childNodes
            for (i in 0 until children.length) {
                val found = findElement(children.item(i), tagName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun traverseChildren(element: Element, sb: StringBuilder) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            traverseNode(children.item(i), sb)
        }
    }

    private fun traverseNode(node: Node, sb: StringBuilder) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val element = node as Element
                val tagName = element.localName.lowercase()

                when (tagName) {
                    "h1" -> {
                        sb.appendLine()
                        sb.appendLine("= " + element.textContent.trim())
                        sb.appendLine()
                    }
                    "h2" -> {
                        sb.appendLine()
                        sb.appendLine("== " + element.textContent.trim())
                        sb.appendLine()
                    }
                    "h3" -> {
                        sb.appendLine()
                        sb.appendLine("=== " + element.textContent.trim())
                        sb.appendLine()
                    }
                    "h4" -> {
                        sb.appendLine()
                        sb.appendLine("==== " + element.textContent.trim())
                        sb.appendLine()
                    }
                    "h5" -> {
                        sb.appendLine()
                        sb.appendLine("===== " + element.textContent.trim())
                        sb.appendLine()
                    }
                    "h6" -> {
                        sb.appendLine()
                        sb.appendLine("====== " + element.textContent.trim())
                        sb.appendLine()
                    }
                    "pre" -> {
                        val code = element.textContent.trimEnd()
                        if (code.isNotBlank()) {
                            sb.appendLine("[source,text]")
                            sb.appendLine("----")
                            code.lines().forEach { line -> sb.appendLine(line) }
                            sb.appendLine("----")
                            sb.appendLine()
                        }
                    }
                    "p", "div" -> {
                        val line = buildInlineContent(element)
                        if (line.isNotBlank()) {
                            sb.appendLine(line)
                        }
                    }
                    else -> traverseChildren(element, sb)
                }
            }
            Node.TEXT_NODE -> {
                val text = node.textContent.trim()
                if (text.isNotBlank()) {
                    sb.appendLine(text)
                }
            }
        }
    }

    private fun buildInlineContent(element: Element): String {
        val sb = StringBuilder()
        val children = element.childNodes
        for (i in 0 until children.length) {
            appendInlineNode(children.item(i), sb)
        }
        return sb.toString().trim()
    }

    private fun appendInlineNode(node: Node, sb: StringBuilder) {
        when (node.nodeType) {
            Node.TEXT_NODE -> sb.append(node.textContent)
            Node.ELEMENT_NODE -> {
                val element = node as Element
                val tagName = element.localName.lowercase()
                when (tagName) {
                    "code" -> sb.append("`" + element.textContent + "`")
                    "em", "i" -> sb.append("_" + element.textContent + "_")
                    "strong", "b" -> sb.append("*" + element.textContent + "*")
                    "a" -> sb.append(element.textContent)
                    "br" -> {} // intentional no-op
                    else -> appendInlineChildren(element, sb)
                }
            }
        }
    }

    private fun appendInlineChildren(element: Element, sb: StringBuilder) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            appendInlineNode(children.item(i), sb)
        }
    }
}
