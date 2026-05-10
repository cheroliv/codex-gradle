package dev.cheroliv.codex.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConvertToMarkdownTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `converts headers to markdown hash format`() {
        val adocFile = File(tempDir, "headers.adoc")
        val mdFile = File(tempDir, "headers.md")
        adocFile.writeText(
            """
            = Main Title
            
            == Chapter One
            
            Some intro text.
            
            === Section 1.1
            
            Section content.
            
            ==== Deep section
            
            == Chapter Two
            
            Final text.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        assertTrue(mdFile.exists())
        val md = mdFile.readText()
        assertTrue(md.startsWith("# "), "Should start with h1, got: ${md.lines().first()}")
        assertTrue(md.contains("## Chapter One"), "Should have h2")
        assertTrue(md.contains("### Section 1.1"), "Should have h3")
        assertTrue(md.contains("#### Deep section"), "Should have h4")
        assertTrue(md.contains("## Chapter Two"), "Should have second chapter")
    }

    @Test
    fun `preserves code blocks with language tag`() {
        val adocFile = File(tempDir, "code.adoc")
        val mdFile = File(tempDir, "code.md")
        adocFile.writeText(
            """
            = Code Example
            
            [source,java]
            ----
            public class Hello {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            ----
            
            Some follow-up text.
            
            [source,kotlin]
            ----
            fun main() {
                println("Kotlin")
            }
            ----
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("System.out.println"), "Should preserve java code as text")
        assertTrue(md.contains("println(\"Kotlin\")"), "Should preserve kotlin code as text")
        assertTrue(md.contains("Some follow-up text."), "Should preserve text after code block")
    }

    @Test
    fun `converts admonitions to blockquotes`() {
        val adocFile = File(tempDir, "notes.adoc")
        val mdFile = File(tempDir, "notes.md")
        adocFile.writeText(
            """
            = Notes
            
            [NOTE]
            ====
            This is important information.
            ====
            
            [WARNING]
            ====
            Be careful with this setting.
            ====
            
            [TIP]
            ====
            Try using the shortcut Ctrl+S.
            ====
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("> **Note**"), "Should have note admonition")
        assertTrue(md.contains("> **Warning**"), "Should have warning admonition")
        assertTrue(md.contains("> **Tip**"), "Should have tip admonition")
    }

    @Test
    fun `converts table markup to markdown`() {
        val adocFile = File(tempDir, "table.adoc")
        val mdFile = File(tempDir, "table.md")
        adocFile.writeText(
            """
            = Table Test
            
            .Table 1. Comparison
            |===
            | Name | Value | Notes
            | foo  | 42    | first
            | bar  | 99    | second
            |===
            
            After the table.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("| Name | Value | Notes |"), "Should have table header")
        assertTrue(md.contains("| foo | 42 | first |"), "Should have table row")
        assertTrue(md.contains("After the table."), "Should preserve text after table")
    }

    @Test
    fun `converts images to markdown syntax`() {
        val adocFile = File(tempDir, "images.adoc")
        val mdFile = File(tempDir, "images.md")
        adocFile.writeText(
            """
            = Images
            
            image:screenshot.png[Screenshot of the application]
            
            Some text.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("![screenshot.png](Screenshot of the application)"), "Should convert image markdown")
    }

    @Test
    fun `converts links to markdown`() {
        val adocFile = File(tempDir, "links.adoc")
        val mdFile = File(tempDir, "links.md")
        adocFile.writeText(
            """
            = Links
            
            Check https://example.com for more info.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("https://example.com"), "Should preserve URL in output")
    }

    @Test
    fun `converts list items`() {
        val adocFile = File(tempDir, "list.adoc")
        val mdFile = File(tempDir, "list.md")
        adocFile.writeText(
            """
            = Lists
            
            * First item
            * Second item
            * Third item
            
            Normal text after list.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("- First item"), "Should convert list item")
        assertTrue(md.contains("- Second item"))
        assertTrue(md.contains("- Third item"))
        assertTrue(md.contains("Normal text after list."))
    }

    @Test
    fun `preserves inline formatting`() {
        val adocFile = File(tempDir, "inline.adoc")
        val mdFile = File(tempDir, "inline.md")
        adocFile.writeText(
            """
            = Formatting
            
            This text has **bold** and *italic* and `code` inline.
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertTrue(md.contains("**bold**"), "Should preserve bold")
        assertTrue(md.contains("*italic*"), "Should preserve italic")
        assertTrue(md.contains("`code`"), "Should preserve code")
    }

    @Test
    fun `skips comment lines`() {
        val adocFile = File(tempDir, "comments.adoc")
        val mdFile = File(tempDir, "comments.md")
        adocFile.writeText(
            """
            = Doc
            
            // This is a comment that should be skipped
            
            Visible content.
            
            // Another comment
            """.trimIndent()
        )

        val task = createTask(adocFile, mdFile)
        task.convert()

        val md = mdFile.readText()
        assertFalse(md.contains("comment"), "Should not contain comments, got: $md")
        assertTrue(md.contains("Visible content"), "Should preserve visible content")
    }

    @Test
    fun `empty adoc produces minimal markdown`() {
        val adocFile = File(tempDir, "empty.adoc")
        val mdFile = File(tempDir, "empty.md")
        adocFile.writeText("= Empty\n")

        val task = createTask(adocFile, mdFile)
        task.convert()

        assertTrue(mdFile.exists())
        val md = mdFile.readText()
        assertTrue(md.length > 0, "Should produce non-empty output")
    }

    private fun createTask(adocFile: File, mdFile: File): ConvertToMarkdownTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "convertToMarkdown",
            ConvertToMarkdownTask::class.java
        ).get()
        task.adocFile.set(adocFile)
        task.markdownFile.set(mdFile)
        return task
    }
}
