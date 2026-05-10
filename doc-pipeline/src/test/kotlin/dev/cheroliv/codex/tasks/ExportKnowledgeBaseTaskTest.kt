package dev.cheroliv.codex.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExportKnowledgeBaseTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `exports three format files from chunks`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(createSimpleChunksJson("testbook", "Apache-2.0"))

        val task = createTask(chunksFile, outputDir)
        task.export()

        val docDir = outputDir.resolve("testbook")
        assertTrue(docDir.isDirectory, "Document directory should exist")

        val jsonFile = docDir.resolve("knowledge-base.json")
        val mdFile = docDir.resolve("knowledge-base.md")
        val adocFile = docDir.resolve("knowledge-base.adoc")

        assertTrue(jsonFile.exists(), "JSON-L file should exist")
        assertTrue(jsonFile.length() > 0, "JSON-L should not be empty")

        assertTrue(mdFile.exists(), "Markdown file should exist")
        assertTrue(mdFile.length() > 0, "Markdown should not be empty")

        assertTrue(adocFile.exists(), "AsciiDoc file should exist")
        assertTrue(adocFile.length() > 0, "AsciiDoc should not be empty")
    }

    @Test
    fun `JSON-L contains context and graph`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(createSimpleChunksJson("ldtest", "Apache-2.0"))

        val task = createTask(chunksFile, outputDir)
        task.export()

        val jsonContent = outputDir.resolve("ldtest").resolve("knowledge-base.json").readText()
        assertTrue(jsonContent.contains("@context"), "Should contain @context")
        assertTrue(jsonContent.contains("@graph"), "Should contain @graph")
        assertTrue(jsonContent.contains("urn:codex:"), "Should contain URN ids")
        assertTrue(jsonContent.contains("DocumentChunk"), "Should contain DocumentChunk type")
        assertTrue(jsonContent.contains("https://schema.org/"), "Should reference schema.org")
    }

    @Test
    fun `JSON-L includes code blocks and license`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(chunksWithCodeBlock("codetest", "Apache-2.0"))

        val task = createTask(chunksFile, outputDir)
        task.export()

        val jsonContent = outputDir.resolve("codetest").resolve("knowledge-base.json").readText()
        assertTrue(jsonContent.contains("codeBlocks"), "Should have codeBlocks field")
        assertTrue(jsonContent.contains("println"), "Should contain code content")
        assertTrue(jsonContent.contains("Apache-2.0"), "Should contain license")
    }

    @Test
    fun `Markdown contains proper heading hierarchy`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(chunksWithHierarchy("hierbook", "Apache-2.0"))

        val task = createTask(chunksFile, outputDir)
        task.export()

        val mdContent = outputDir.resolve("hierbook").resolve("knowledge-base.md").readText()
        assertTrue(mdContent.contains("# "), "Should have h1")
        assertTrue(mdContent.contains("## "), "Should have h2")
        assertTrue(mdContent.contains("### "), "Should have h3")
    }

    @Test
    fun `Markdown includes overlap hints`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(chunksWithOverlap("overlapbook", "Apache-2.0"))

        val task = createTask(chunksFile, outputDir)
        task.export()

        val mdContent = outputDir.resolve("overlapbook").resolve("knowledge-base.md").readText()
        assertTrue(mdContent.contains("_Suite :"), "Should contain overlap hint")
    }

    @Test
    fun `AsciiDoc contains proper structure`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(createSimpleChunksJson("adocbook", "Apache-2.0"))

        val task = createTask(chunksFile, outputDir)
        task.export()

        val adocContent = outputDir.resolve("adocbook").resolve("knowledge-base.adoc").readText()
        assertTrue(adocContent.startsWith("= "), "Should start with document title")
        assertTrue(adocContent.contains("== "), "Should have h2 sections")
        assertTrue(adocContent.contains("[NOTE]"), "Should have note about generation")
        assertTrue(adocContent.contains("'''"), "Should have section separators")
    }

    @Test
    fun `AsciiDoc contains code blocks in source format`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(chunksWithCodeBlock("adoc-code", "Apache-2.0"))

        val task = createTask(chunksFile, outputDir)
        task.export()

        val adocContent = outputDir.resolve("adoc-code").resolve("knowledge-base.adoc").readText()
        assertTrue(adocContent.contains("[source,text]"), "Should have source block")
        assertTrue(adocContent.contains("----"), "Should have code delimiters")
        assertTrue(adocContent.contains("println"), "Should contain code")
    }

    @Test
    fun `export sorts chunks by section path`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(
            """
            [
              {"id":"c","sourceDocument":"sort","sectionPath":"Chapter 2","headingLevel":2,"content":"## Chapter 2\nThird","codeBlocks":[],"entities":[],"overlapNext":null,"license":"Apache-2.0"},
              {"id":"a","sourceDocument":"sort","sectionPath":"Chapter 1","headingLevel":2,"content":"## Chapter 1\nFirst","codeBlocks":[],"entities":[],"overlapNext":null,"license":"Apache-2.0"},
              {"id":"b","sourceDocument":"sort","sectionPath":"Chapter 1 > Section 1","headingLevel":3,"content":"### Section 1\nSecond","codeBlocks":[],"entities":[],"overlapNext":null,"license":"Apache-2.0"}
            ]
            """.trimIndent()
        )

        val task = createTask(chunksFile, outputDir)
        task.export()

        val mdContent = outputDir.resolve("sort").resolve("knowledge-base.md").readText()
        val chapter1Pos = mdContent.indexOf("Chapter 1")
        val chapter2Pos = mdContent.indexOf("Chapter 2")
        assertTrue(chapter1Pos < chapter2Pos,
            "Chapter 1 should come before Chapter 2 in sorted output")
    }

    @Test
    fun `content without heading is preserved`() {
        val chunksFile = File(tempDir, "chunks.json")
        val outputDir = File(tempDir, "output")
        chunksFile.writeText(
            """
            [
              {
                "id": "a",
                "sourceDocument": "nohead",
                "sectionPath": "nohead",
                "headingLevel": 0,
                "content": "Plain text without any heading.",
                "codeBlocks": [],
                "entities": [],
                "overlapNext": null,
                "license": "Apache-2.0"
              }
            ]
            """.trimIndent()
        )

        val task = createTask(chunksFile, outputDir)
        task.export()

        val mdContent = outputDir.resolve("nohead").resolve("knowledge-base.md").readText()
        assertTrue(mdContent.contains("Plain text without any heading"))
        assertFalse(mdContent.contains("## nohead"),
            "Should not render heading for headingLevel=0")
    }

    private fun createTask(
        chunksFile: File,
        outputDir: File
    ): ExportKnowledgeBaseTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "exportKnowledgeBase",
            ExportKnowledgeBaseTask::class.java
        ).get()
        task.chunksFile.set(chunksFile)
        task.outputDir.set(outputDir)
        return task
    }

    private fun createSimpleChunksJson(source: String, license: String): String {
        return """
        [
          {
            "id": "chk-aaa",
            "sourceDocument": "$source",
            "sectionPath": "Main Title",
            "headingLevel": 1,
            "content": "# Main Title\n\nIntro content here.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": null,
            "license": "$license"
          },
          {
            "id": "chk-bbb",
            "sourceDocument": "$source",
            "sectionPath": "Main Title > Chapter One",
            "headingLevel": 2,
            "content": "## Chapter One\n\nChapter content.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": "Chapter Two starts with more topics.",
            "license": "$license"
          },
          {
            "id": "chk-ccc",
            "sourceDocument": "$source",
            "sectionPath": "Main Title > Chapter Two",
            "headingLevel": 2,
            "content": "## Chapter Two\n\nMore content here.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": null,
            "license": "$license"
          }
        ]
        """.trimIndent()
    }

    private fun chunksWithCodeBlock(source: String, license: String): String {
        return """
        [
          {
            "id": "chk-code",
            "sourceDocument": "$source",
            "sectionPath": "Code Section",
            "headingLevel": 1,
            "content": "# Code Section\n\nExample code below.",
            "codeBlocks": ["println(\"Hello\")"],
            "entities": [],
            "overlapNext": null,
            "license": "$license"
          }
        ]
        """.trimIndent()
    }

    private fun chunksWithHierarchy(source: String, license: String): String {
        return """
        [
          {
            "id": "chk-h1",
            "sourceDocument": "$source",
            "sectionPath": "Root",
            "headingLevel": 1,
            "content": "# Root\n\nTop level text.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": null,
            "license": "$license"
          },
          {
            "id": "chk-h2",
            "sourceDocument": "$source",
            "sectionPath": "Root > Branch",
            "headingLevel": 2,
            "content": "## Branch\n\nSecond level.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": null,
            "license": "$license"
          },
          {
            "id": "chk-h3",
            "sourceDocument": "$source",
            "sectionPath": "Root > Branch > Leaf",
            "headingLevel": 3,
            "content": "### Leaf\n\nThird level detail.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": null,
            "license": "$license"
          }
        ]
        """.trimIndent()
    }

    private fun chunksWithOverlap(source: String, license: String): String {
        return """
        [
          {
            "id": "chk-overlap",
            "sourceDocument": "$source",
            "sectionPath": "Section A",
            "headingLevel": 2,
            "content": "## Section A\n\nContent for section A.",
            "codeBlocks": [],
            "entities": [],
            "overlapNext": "The next section covers advanced topics.",
            "license": "$license"
          }
        ]
        """.trimIndent()
    }
}
