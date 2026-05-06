package dev.cheroliv.codex.tasks

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ExtractTextTask : DefaultTask() {

    @get:InputFile
    abstract val pdfFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = pdfFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] extractText : ${input.name} → ${output.name}")

        Loader.loadPDF(input).use { document ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
            }
            val rawText = stripper.getText(document)
            output.writeText(rawText)
        }

        logger.lifecycle("[codex] ✓ Extraction terminée — ${output.length()} octets")
    }
}
