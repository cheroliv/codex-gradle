package dev.cheroliv.codex.tasks

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
import org.asciidoctor.ast.Block
import org.asciidoctor.ast.Section
import org.asciidoctor.ast.StructuralNode
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class AsciiDocToJsonLddTask : DefaultTask() {

    @get:InputFile
    abstract val adocFile: RegularFileProperty

    @get:OutputFile
    abstract val jsonFile: RegularFileProperty

    @TaskAction
    fun convert() {
        val input = adocFile.asFile.get()
        val output = jsonFile.asFile.get()

        logger.lifecycle("[codex] asciiDocToJsonLdd : ${input.name} -> ${output.name}")

        val json = buildJsonLdd(input)
        output.writeText(json)
        logger.lifecycle("[codex] Done - JSON LDD : ${json.length} octets")
    }

    private fun buildJsonLdd(adoc: File): String {
        val asciidoctor = Asciidoctor.Factory.create()
        val options = Options.builder().build()
        val document = asciidoctor.loadFile(adoc, options)

        val root = DocNode(title = "root", level = 0)
        traverse(document, root)

        val json = toJson(root)
        asciidoctor.close()
        return json
    }

    private fun traverse(node: StructuralNode, parent: DocNode) {
        for (block in node.blocks) {
            when {
                block is Section -> {
                    val sectionNode = DocNode(
                        title = block.title ?: "",
                        level = block.level,
                        children = mutableListOf()
                    )
                    parent.children.add(sectionNode)
                    traverse(block, sectionNode)
                }
                block is Block -> {
                    val text = (block.source ?: "").trim()
                    if (text.isNotBlank()) {
                        parent.children.add(DocNode(title = text, level = -1))
                    }
                }
            }
        }
    }

    private fun toJson(node: DocNode): String {
        val sb = StringBuilder()
        writeNode(node, sb, 0)
        return sb.toString()
    }

    private fun writeNode(node: DocNode, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)

        if (depth == 0 && node.children.isNotEmpty()) {
            sb.appendLine("[")

            for (i in 0 until node.children.size) {
                writeNode(node.children[i], sb, 1)
                if (i < node.children.size - 1) sb.appendLine(",")
            }

            sb.appendLine("]")
        } else if (node.level > 0) {
            sb.appendLine("${indent}{")
            sb.appendLine("${indent}  \"title\": \"${esc(node.title)}\",")
            sb.appendLine("${indent}  \"level\": ${node.level},")

            if (node.children.isEmpty()) {
                if (sb.endsWith(",\n")) {
                    sb.setLength(sb.length - 2)
                    sb.appendLine()
                }
            } else {
                sb.appendLine("${indent}  \"children\": [")
                for (i in 0 until node.children.size) {
                    writeNode(node.children[i], sb, depth + 2)
                    if (i < node.children.size - 1) sb.appendLine(",")
                }
                sb.appendLine()
                sb.appendLine("${indent}  ]")
            }
            sb.append("${indent}}")
        } else if (node.level == -1) {
            val text = esc(node.title)
            val displayText = if (text.length > 600) text.substring(0, 600) + "..." else text
            sb.appendLine("${indent}{")
            sb.appendLine("${indent}  \"type\": \"paragraph\",")
            sb.appendLine("${indent}  \"text\": \"$displayText\"")
            sb.append("${indent}}")
        }
    }

    private fun esc(s: String): String {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    data class DocNode(
        val title: String,
        val level: Int,
        val children: MutableList<DocNode> = mutableListOf()
    )
}
