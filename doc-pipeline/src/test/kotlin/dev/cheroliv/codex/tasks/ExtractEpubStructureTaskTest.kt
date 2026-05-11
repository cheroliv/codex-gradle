package dev.cheroliv.codex.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractEpubStructureTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `extractEpubStructure produces AsciiDoc with h1, h2, h3 and code block`() {
        val epubFile = File(tempDir, "test-book.epub")
        val outputFile = File(tempDir, "test-book.adoc")

        createSyntheticEpub(
            epubFile,
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Test Book</title></head>
                <body>
                    <h1>Introduction to Programming</h1>
                    <h2>Getting Started</h2>
                    <p>This chapter introduces programming concepts to beginners.</p>
                    <h3>Hello World Example</h3>
                    <p>Let us write our first program.</p>
                    <pre><code>function main() {
    println("Hello, World!")
}</code></pre>
                    <p>The function keyword declares a new function.</p>
                </body>
                </html>
            """.trimIndent()
        )

        val task = createTask(epubFile, outputFile)
        task.extract()

        assertTrue(outputFile.exists(), "Output file should exist")
        val content = outputFile.readText()

        assertTrue(content.contains("= Introduction to Programming"), "Should contain h1")
        assertTrue(content.contains("== Getting Started"), "Should contain h2")
        assertTrue(content.contains("=== Hello World Example"), "Should contain h3")
        assertTrue(content.contains("[source,text]"), "Should contain code block")
        assertTrue(content.contains("println"), "Should contain println body")
        assertTrue(content.contains("function main"), "Should contain function main")
        assertTrue(content.lines().size > 5, "Should have multiple lines")
    }

    @Test
    fun `extractEpubStructure with h4-h6 produces deeper hierarchy`() {
        val epubFile = File(tempDir, "deep-hierarchy.epub")
        val outputFile = File(tempDir, "deep-hierarchy.adoc")

        createSyntheticEpub(
            epubFile,
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <h1>Part One</h1>
                    <h2>Chapter A</h2>
                    <h3>Section A.1</h3>
                    <h4>Subsection A.1.a</h4>
                    <h5>Detail A.1.a.i</h5>
                    <h6>Note A.1.a.i.alpha</h6>
                    <p>Deeply nested content.</p>
                </body>
                </html>
            """.trimIndent()
        )

        val task = createTask(epubFile, outputFile)
        task.extract()

        val content = outputFile.readText()
        assertTrue(content.contains("= Part One"))
        assertTrue(content.contains("== Chapter A"))
        assertTrue(content.contains("=== Section A.1"))
        assertTrue(content.contains("==== Subsection A.1.a"))
        assertTrue(content.contains("===== Detail A.1.a.i"))
        assertTrue(content.contains("====== Note A.1.a.i.alpha"))
        assertTrue(content.contains("Deeply nested content"))
    }

    @Test
    fun `empty EPUB returns empty marker`() {
        val epubFile = File(tempDir, "empty.epub")
        val outputFile = File(tempDir, "empty.adoc")

        createSyntheticEpub(
            epubFile,
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                </body>
                </html>
            """.trimIndent()
        )

        val task = createTask(epubFile, outputFile)
        task.extract()

        assertTrue(outputFile.exists())
        val content = outputFile.readText()
        assertTrue(content.contains("Structure extraite de l'EPUB") || content.contains("EPUB vide"))
    }

    @Test
    fun `inline code tags become backtick code`() {
        val epubFile = File(tempDir, "inline-code.epub")
        val outputFile = File(tempDir, "inline-code.adoc")

        createSyntheticEpub(
            epubFile,
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <p>Use the <code>println</code> function to output text.</p>
                </body>
                </html>
            """.trimIndent()
        )

        val task = createTask(epubFile, outputFile)
        task.extract()

        val content = outputFile.readText()
        assertTrue(content.contains("`println`"), "Expected backtick code, got:\n$content")
    }

    @Test
    fun `pre without code tag produces literal block`() {
        val epubFile = File(tempDir, "pre-only.epub")
        val outputFile = File(tempDir, "pre-only.adoc")

        createSyntheticEpub(
            epubFile,
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <pre>val x = 42
println(x)</pre>
                </body>
                </html>
            """.trimIndent()
        )

        val task = createTask(epubFile, outputFile)
        task.extract()

        val content = outputFile.readText()
        assertTrue(content.contains("[source,text]"))
        assertTrue(content.contains("val x = 42"))
        assertTrue(content.contains("println(x)"))
    }

    @Test
    fun `extractEpubStructure produces expected note header`() {
        val epubFile = File(tempDir, "simple.epub")
        val outputFile = File(tempDir, "simple.adoc")

        createSyntheticEpub(
            epubFile,
            """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <h1>Title</h1>
                    <p>Some text.</p>
                </body>
                </html>
            """.trimIndent()
        )

        val task = createTask(epubFile, outputFile)
        task.extract()

        val content = outputFile.readText()
        assertTrue(content.contains("[NOTE]"))
        assertTrue(content.contains("extractEpubStructure"))
    }

    private fun createTask(epubFile: File, outputFile: File): ExtractEpubStructureTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "extractEpubStructure",
            ExtractEpubStructureTask::class.java
        ).get()
        task.epubFile.set(epubFile)
        task.outputFile.set(outputFile)
        return task
    }

    private fun createSyntheticEpub(file: File, xhtmlContent: String) {
        val mimetype = "application/epub+zip"
        val container =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """.trimIndent()
        val opf =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="book-id">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:identifier id="book-id">urn:uuid:test</dc:identifier>
                    <dc:language>en</dc:language>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="content" href="content.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="content"/>
                </spine>
            </package>
            """.trimIndent()

        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write(mimetype.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(container.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opf.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.xhtml"))
            zip.write(xhtmlContent.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write("<ncx xmlns='http://www.daisy.org/z3986/2005/ncx/'/>".toByteArray())
            zip.closeEntry()
        }
    }
}
