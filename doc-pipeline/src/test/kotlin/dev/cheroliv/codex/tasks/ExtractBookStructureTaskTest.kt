package dev.cheroliv.codex.tasks

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExtractBookStructureTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `detection bold font style from font name`() {
        assertTrue(FontStyleDetector.detect("Helvetica-Bold").isBold())
        assertTrue(FontStyleDetector.detect("Times-Bold").isBold())
        assertTrue(FontStyleDetector.detect("Arial Bold").isBold())
        assertTrue(FontStyleDetector.detect("DejaVu Sans Bold").isBold())
    }

    @Test
    fun `detection italic font style from font name`() {
        assertTrue(FontStyleDetector.detect("Helvetica-Oblique").isItalic())
        assertTrue(FontStyleDetector.detect("Times-Italic").isItalic())
        assertTrue(FontStyleDetector.detect("Georgia Italic").isItalic())
    }

    @Test
    fun `detection monospace font style from font name`() {
        assertTrue(FontStyleDetector.detect("Courier").isMonospace())
        assertTrue(FontStyleDetector.detect("Consolas").isMonospace())
        assertTrue(FontStyleDetector.detect("Menlo").isMonospace())
        assertTrue(FontStyleDetector.detect("JetBrains Mono").isMonospace())
        assertTrue(FontStyleDetector.detect("Source Code Pro").isMonospace())
        assertTrue(FontStyleDetector.detect("Fira Code").isMonospace())
        assertTrue(FontStyleDetector.detect("DejaVu Sans Mono").isMonospace())
        assertTrue(FontStyleDetector.detect("Liberation Mono").isMonospace())
    }

    @Test
    fun `normal font returns normal style`() {
        val style = FontStyleDetector.detect("Helvetica")
        assertTrue(!style.isBold() && !style.isItalic() && !style.isMonospace())
    }

    @Test
    fun `bold italic font detected correctly`() {
        val style = FontStyleDetector.detect("Helvetica-BoldItalic")
        assertTrue(style.isBold())
        assertTrue(style.isItalic())
    }

    @Test
    fun `extractBookStructure produces AsciiDoc with headers and code block`() {
        val pdfFile = File(tempDir, "test-book.pdf")
        val outputFile = File(tempDir, "test-book.adoc")
        createSyntheticPdf(pdfFile)

        val task = createExtractTask(pdfFile, outputFile)
        task.extract()

        assertTrue(outputFile.exists(), "Output file should exist")
        val content = outputFile.readText()

        assertTrue(content.contains("= "), "Should contain h1 header")
        assertTrue(content.contains("== "), "Should contain h2 header")
        assertTrue(content.contains("=== "), "Should contain h3 header")
        assertTrue(content.contains("[source,text]"), "Should contain code block")
        assertTrue(content.contains("println"), "Should contain println")
        assertTrue(content.contains("function"), "Should contain function")
        assertTrue(content.lines().size > 5, "Should have multiple lines")
    }

    @Test
    fun `empty PDF returns empty document marker`() {
        val emptyPdf = File(tempDir, "empty.pdf")
        val outputFile = File(tempDir, "empty.adoc")
        PDDocument().use { it.save(emptyPdf) }

        val task = createExtractTask(emptyPdf, outputFile)
        task.extract()

        assertTrue(outputFile.exists())
        assertTrue(outputFile.readText().contains("[Document vide]"))
    }

    @Test
    fun `monospace detection triggers code blocks even with short code`() {
        val pdfFile = File(tempDir, "code-only.pdf")
        val outputFile = File(tempDir, "code-only.adoc")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val monoFont = PDType1Font(Standard14Fonts.FontName.COURIER)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("val x = 42")
                cs.endText()
                cs.beginText()
                cs.newLineAtOffset(50f, 685f)
                cs.showText("println(x)")
                cs.endText()
            }
            doc.save(pdfFile)
        }

        val task = createExtractTask(pdfFile, outputFile)
        task.extract()

        val content = outputFile.readText()
        assertTrue(content.contains("[source,text]"), "Code block should be present")
        assertTrue(content.contains("val x = 42"))
        assertTrue(content.contains("println(x)"))
    }

    @Test
    fun `header hierarchy reflects font size and boldness`() {
        val pdfFile = File(tempDir, "hierarchy.pdf")
        val outputFile = File(tempDir, "hierarchy.adoc")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val normalFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val boldFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(boldFont, 24f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Main Title")
                cs.endText()

                cs.beginText()
                cs.setFont(boldFont, 18f)
                cs.newLineAtOffset(50f, 670f)
                cs.showText("Chapter One")
                cs.endText()

                cs.beginText()
                cs.setFont(boldFont, 14f)
                cs.newLineAtOffset(50f, 640f)
                cs.showText("Section 1.1")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 12f)
                cs.newLineAtOffset(50f, 610f)
                cs.showText("This is regular body text under section 1.1.")
                cs.endText()

                cs.beginText()
                cs.setFont(boldFont, 14f)
                cs.newLineAtOffset(50f, 580f)
                cs.showText("Section 1.2")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 12f)
                cs.newLineAtOffset(50f, 550f)
                cs.showText("Another paragraph of body text.")
                cs.endText()
            }
            doc.save(pdfFile)
        }

        val task = createExtractTask(pdfFile, outputFile)
        task.extract()

        val content = outputFile.readText()
        assertTrue(content.contains("= Main Title"),
            "Expected h1 header, got:\n${content.lines().take(10).joinToString("\n")}")
        assertTrue(content.contains("== Chapter One"))
        assertTrue(content.contains("=== "), "Should have h3 sections")
        assertTrue(content.contains("This is regular body text"))
    }

    @Test
    fun `single line monospace becomes inline code`() {
        val pdfFile = File(tempDir, "inline-code.pdf")
        val outputFile = File(tempDir, "inline-code.adoc")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val monoFont = PDType1Font(Standard14Fonts.FontName.COURIER)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("single_line_code")
                cs.endText()
            }
            doc.save(pdfFile)
        }

        val task = createExtractTask(pdfFile, outputFile)
        task.extract()

        val content = outputFile.readText()
        assertTrue(content.contains("`single_line_code`"), "Expected inline code, got:\n$content")
    }

    private fun createExtractTask(pdfFile: File, outputFile: File): ExtractBookStructureTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "extractBookStructure",
            ExtractBookStructureTask::class.java
        ).get()
        task.pdfFile.set(pdfFile)
        task.outputFile.set(outputFile)
        return task
    }

    private fun createSyntheticPdf(file: File) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            val boldFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val normalFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val monoFont = PDType1Font(Standard14Fonts.FontName.COURIER)

            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(boldFont, 20f)
                cs.newLineAtOffset(50f, 720f)
                cs.showText("Introduction to Programming")
                cs.endText()

                cs.beginText()
                cs.setFont(boldFont, 16f)
                cs.newLineAtOffset(50f, 695f)
                cs.showText("Getting Started")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 11f)
                cs.newLineAtOffset(50f, 670f)
                cs.showText("This chapter introduces programming concepts to beginners.")
                cs.endText()

                cs.beginText()
                cs.setFont(boldFont, 14f)
                cs.newLineAtOffset(50f, 645f)
                cs.showText("Hello World Example")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 11f)
                cs.newLineAtOffset(50f, 620f)
                cs.showText("Let us write our first program.")
                cs.endText()

                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(60f, 595f)
                cs.showText("function main() {")
                cs.endText()

                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(60f, 582f)
                cs.showText("    println(\"Hello, World!\")")
                cs.endText()

                cs.beginText()
                cs.setFont(monoFont, 10f)
                cs.newLineAtOffset(60f, 569f)
                cs.showText("}")
                cs.endText()

                cs.beginText()
                cs.setFont(normalFont, 11f)
                cs.newLineAtOffset(50f, 544f)
                cs.showText("The function keyword declares a new function.")
                cs.endText()
            }
            doc.save(file)
        }
    }
}
