package dev.cheroliv.codex.tasks

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExtractTextTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `extracts text from simple PDF`() {
        val pdfFile = File(tempDir, "simple.pdf")
        val outputFile = File(tempDir, "simple.txt")
        createSimplePdf(pdfFile)

        val task = createTask(pdfFile, outputFile)
        task.extract()

        assertTrue(outputFile.exists())
        val content = outputFile.readText()
        assertTrue(content.contains("Hello World"), "Should contain 'Hello World', got:\n$content")
        assertTrue(content.contains("This is a test PDF"), "Should contain body text")
    }

    @Test
    fun `output file has expected extension and content length`() {
        val pdfFile = File(tempDir, "multi.pdf")
        val outputFile = File(tempDir, "multi.txt")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(font, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Line one")
                cs.endText()
                cs.beginText()
                cs.setFont(font, 12f)
                cs.newLineAtOffset(50f, 685f)
                cs.showText("Line two")
                cs.endText()
            }
            doc.save(pdfFile)
        }

        val task = createTask(pdfFile, outputFile)
        task.extract()

        assertTrue(outputFile.exists())
        val content = outputFile.readText()
        assertTrue(content.contains("Line one"))
        assertTrue(content.contains("Line two"))
        assertTrue(content.lines().size >= 2, "Should have at least 2 lines, got ${content.lines().size}")
    }

    @Test
    fun `empty PDF produces output file`() {
        val emptyPdf = File(tempDir, "empty.pdf")
        val outputFile = File(tempDir, "empty.txt")
        PDDocument().use { it.save(emptyPdf) }

        val task = createTask(emptyPdf, outputFile)
        task.extract()

        assertTrue(outputFile.exists())
    }

    private fun createTask(pdfFile: File, outputFile: File): ExtractTextTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "extractText",
            ExtractTextTask::class.java
        ).get()
        task.pdfFile.set(pdfFile)
        task.outputFile.set(outputFile)
        return task
    }

    private fun createSimplePdf(file: File) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20f)
                cs.newLineAtOffset(50f, 720f)
                cs.showText("Hello World")
                cs.endText()
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 11f)
                cs.newLineAtOffset(50f, 690f)
                cs.showText("This is a test PDF with some content.")
                cs.endText()
            }
            doc.save(file)
        }
    }
}
