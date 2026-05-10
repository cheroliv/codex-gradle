package dev.cheroliv.codex.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class ExportKnowledgeBaseTask : DefaultTask() {

    @get:InputFile
    abstract val chunksFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun export() {
        val input = chunksFile.asFile.get()
        val output = outputDir.asFile.get()

        logger.lifecycle("[codex] exportKnowledgeBase : ${input.name} → ${output.absolutePath}")

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val raw = input.readText()
        val chunks = json.decodeFromString<List<DocumentChunk>>(raw)

        if (chunks.isEmpty()) {
            logger.warn("[codex] Aucun chunk trouvé — export vide")
            return
        }

        val sourceDocument = chunks.first().sourceDocument
        val docDir = output.resolve(sourceDocument)
        docDir.mkdirs()

        docDir.resolve("knowledge-base.json").writeText(buildJsonLd(chunks, sourceDocument))
        logger.lifecycle("[codex]   ✓ JSON-L — ${docDir.resolve("knowledge-base.json").length()} octets")

        docDir.resolve("knowledge-base.md").writeText(buildMarkdown(chunks))
        logger.lifecycle("[codex]   ✓ Markdown — ${docDir.resolve("knowledge-base.md").length()} octets")

        docDir.resolve("knowledge-base.adoc").writeText(buildAsciiDoc(chunks))
        logger.lifecycle("[codex]   ✓ AsciiDoc — ${docDir.resolve("knowledge-base.adoc").length()} octets")

        logger.lifecycle(
            "[codex] ✓ Export terminé — ${chunks.size} chunks dans build/codex/knowledge-base/$sourceDocument/"
        )
    }

    private fun buildJsonLd(chunks: List<DocumentChunk>, sourceDocument: String): String {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        @Suppress("OPT_IN_USAGE")
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val graph = chunks.map { chunk ->
            JsonObject(
                mapOf(
                    "@id" to JsonPrimitive("urn:codex:${chunk.id}"),
                    "@type" to JsonPrimitive("DocumentChunk"),
                    "identifier" to JsonPrimitive(chunk.id),
                    "isPartOf" to JsonPrimitive("urn:codex:doc:$sourceDocument"),
                    "sectionPath" to JsonPrimitive(chunk.sectionPath),
                    "headingLevel" to JsonPrimitive(chunk.headingLevel),
                    "text" to JsonPrimitive(chunk.content),
                    "codeBlocks" to JsonArray(
                        chunk.codeBlocks.map { JsonPrimitive(it) }
                    ),
                    "entities" to JsonArray(
                        chunk.entities.map { JsonPrimitive(it) }
                    ),
                    "overlapNext" to (chunk.overlapNext?.let { JsonPrimitive(it) }
                        ?: JsonPrimitive("null")),
                    "license" to JsonPrimitive(chunk.license)
                )
            )
        }

        val root = JsonObject(
            mapOf(
                "@context" to JsonObject(
                    mapOf(
                        "schema" to JsonPrimitive("https://schema.org/"),
                        "codex" to JsonPrimitive("https://cheroliv.github.io/codex/"),
                        "isPartOf" to JsonObject(
                            mapOf(
                                "@id" to JsonPrimitive("schema:isPartOf"),
                                "@type" to JsonPrimitive("@id")
                            )
                        )
                    )
                ),
                "@graph" to JsonArray(graph)
            )
        )

        return json.encodeToString(root)
    }

    private fun buildMarkdown(chunks: List<DocumentChunk>): String {
        val sb = StringBuilder()
        val sorted = chunks.sortedBy { c -> sectionSortKey(c.sectionPath) }

        sb.appendLine("# ${sorted.firstOrNull()?.sourceDocument ?: "Knowledge Base"}")
        sb.appendLine()

        var lastLevel = 0

        for (chunk in sorted) {
            if (chunk.headingLevel > 0 && chunk.headingLevel != lastLevel) {
                if (chunk.headingLevel == 1) sb.appendLine()
            }

            val headingMarkers = "#".repeat(chunk.headingLevel.coerceIn(1..6))
            val title = chunk.sectionPath.substringAfterLast(" > ")

            if (chunk.headingLevel > 0) {
                sb.appendLine("$headingMarkers $title")
                sb.appendLine()
            }

            val contentWithoutHeading = if (chunk.headingLevel > 0) {
                chunk.content.lines().drop(1).joinToString("\n").trim()
            } else {
                chunk.content.trim()
            }

            if (contentWithoutHeading.isNotBlank()) {
                sb.appendLine(contentWithoutHeading)
                sb.appendLine()
            }

            if (chunk.codeBlocks.isNotEmpty()) {
                for (block in chunk.codeBlocks) {
                    sb.appendLine("```text")
                    sb.appendLine(block.trim())
                    sb.appendLine("```")
                    sb.appendLine()
                }
            }

            if (chunk.overlapNext != null) {
                sb.appendLine("> _Suite : ${chunk.overlapNext}_")
                sb.appendLine()
            }

            if (chunk.headingLevel == 1) sb.appendLine("---")
            sb.appendLine()

            lastLevel = chunk.headingLevel
        }

        return sb.toString().trimEnd() + "\n"
    }

    private fun buildAsciiDoc(chunks: List<DocumentChunk>): String {
        val sb = StringBuilder()
        val sorted = chunks.sortedBy { c -> sectionSortKey(c.sectionPath) }

        val docTitle = sorted.firstOrNull()?.sourceDocument ?: "Knowledge Base"
        sb.appendLine("= $docTitle")
        sb.appendLine()

        sb.appendLine("[NOTE]")
        sb.appendLine("====")
        sb.appendLine(
            "Document genere automatiquement par exportKnowledgeBase. " +
                "Base de connaissance agregee depuis ${sorted.size} chunks."
        )
        sb.appendLine("====")
        sb.appendLine()

        for (chunk in sorted) {
            val headingMarkers = "=".repeat(chunk.headingLevel.coerceIn(1..5))
            val title = chunk.sectionPath.substringAfterLast(" > ")

            if (chunk.headingLevel > 0) {
                sb.appendLine("$headingMarkers $title")
                sb.appendLine()
            }

            val contentWithoutHeading = if (chunk.headingLevel > 0) {
                chunk.content.lines().drop(1).joinToString("\n").trim()
            } else {
                chunk.content.trim()
            }

            if (contentWithoutHeading.isNotBlank()) {
                sb.appendLine(contentWithoutHeading)
                sb.appendLine()
            }

            if (chunk.codeBlocks.isNotEmpty()) {
                for (block in chunk.codeBlocks) {
                    sb.appendLine("[source,text]")
                    sb.appendLine("----")
                    sb.appendLine(block.trim())
                    sb.appendLine("----")
                    sb.appendLine()
                }
            }

            if (chunk.overlapNext != null) {
                sb.appendLine("[TIP]")
                sb.appendLine("Suite : ${chunk.overlapNext}")
                sb.appendLine()
            }

            sb.appendLine("'''")
            sb.appendLine()
        }

        return sb.toString().trimEnd() + "\n"
    }

    private fun sectionSortKey(sectionPath: String): String {
        val parts = sectionPath.split(" > ")
        return parts.joinToString(" > ") { part ->
            val padLen = 4
            val numStart = part.indexOfFirst { it.isDigit() }
            if (numStart >= 0) {
                val prefix = part.substring(0, numStart)
                val numPart = part.substring(numStart)
                val num = numPart.takeWhile { it.isDigit() || it == '.' }
                val suffix = numPart.drop(num.length)
                val padded = num.padStart(padLen, '0')
                "$prefix$padded$suffix"
            } else {
                part.padStart(padLen, ' ')
            }
        }
    }
}
