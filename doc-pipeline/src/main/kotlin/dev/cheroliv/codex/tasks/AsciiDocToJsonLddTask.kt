package dev.cheroliv.codex.tasks

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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

    companion object {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val jsonFormat = Json { prettyPrint = true; prettyPrintIndent = "  " }
    }

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

        val lddNodes = root.children.map { it.toLddNode() }
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val json = jsonFormat.encodeToString(ListSerializer(LddNode.serializer()), lddNodes)
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
}
