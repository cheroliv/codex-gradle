package dev.cheroliv.codex.tasks

import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ChunkDocumentTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `chunks simple markdown with multiple headings`() {
        val mdFile = File(tempDir, "doc.md")
        val chunksFile = File(tempDir, "chunks.json")
        mdFile.writeText(
            """
            # Main Title
            
            Intro paragraph text.
            
            ## Chapter One
            
            Chapter one content here.
            
            ### Section 1.1
            
            Section content goes here.
            
            ## Chapter Two
            
            Chapter two content.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        assertTrue(chunksFile.exists())
        val json = chunksFile.readText()
        val chunks = parseChunks(json)

        assertTrue(chunks.size >= 4, "Expected at least 4 chunks, got ${chunks.size}")
        assertEquals("doc", chunks.first().sourceDocument)
        assertTrue(chunks.any { it.sectionPath.contains("Main Title") })
        assertTrue(chunks.any { it.sectionPath.contains("Chapter One") })
        assertTrue(chunks.any { it.sectionPath.contains("Section 1.1") })
        assertEquals("Apache-2.0", chunks.first().license)
    }

    @Test
    fun `single heading produces one chunk`() {
        val mdFile = File(tempDir, "single.md")
        val chunksFile = File(tempDir, "single.json")
        mdFile.writeText(
            """
            # Only Title
            
            Just one section of text.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        assertEquals(1, chunks.size)
        assertEquals(1, chunks[0].headingLevel)
        assertTrue(chunks[0].content.contains("Only Title"))
        assertTrue(chunks[0].content.contains("Just one section"))
    }

    @Test
    fun `document with no headings produces one chunk`() {
        val mdFile = File(tempDir, "nohead.md")
        val chunksFile = File(tempDir, "nohead.json")
        mdFile.writeText(
            """
            This document has no headings.
            Just plain text everywhere.
            Multiple lines of content.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].headingLevel)
        assertEquals("nohead", chunks[0].sourceDocument)
    }

    @Test
    fun `detects code blocks within sections`() {
        val mdFile = File(tempDir, "code.md")
        val chunksFile = File(tempDir, "code.json")
        mdFile.writeText(
            """
            # Code Examples
            
            Here is some javascript:
            
            ```javascript
            function hello() {
                console.log("Hello");
            }
            ```
            
            ## Python Example
            
            More explanation.
            
            ```python
            def greet():
                print("Hi")
            ```
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())

        val jsChunk = chunks.find { it.sectionPath.contains("Code Examples") }
        assertNotNull(jsChunk)
        assertTrue(jsChunk!!.codeBlocks.isNotEmpty(), "Should contain JS code block")
        assertTrue(jsChunk.codeBlocks.any { it.contains("console.log") })

        val pyChunk = chunks.find { it.sectionPath.contains("Python Example") }
        assertNotNull(pyChunk)
        assertTrue(pyChunk!!.codeBlocks.isNotEmpty(), "Should contain Python code block")
        assertTrue(pyChunk.codeBlocks.any { it.contains("print(") })
    }

    @Test
    fun `chunk IDs are deterministic`() {
        val mdFile = File(tempDir, "idtest.md")
        val chunksFile = File(tempDir, "idtest.json")
        mdFile.writeText(
            """
            # Test
            
            Content here.
            """.trimIndent()
        )

        val task1 = createTask(mdFile, chunksFile, "Apache-2.0")
        task1.chunk()
        val ids1 = parseChunks(chunksFile.readText()).map { it.id }

        val task2 = createTask(mdFile, chunksFile, "Apache-2.0")
        task2.chunk()
        val ids2 = parseChunks(chunksFile.readText()).map { it.id }

        assertEquals(ids1, ids2, "Chunk IDs should be deterministic")
    }

    @Test
    fun `sectionPath builds hierarchy from ancestor headings`() {
        val mdFile = File(tempDir, "hierarchy.md")
        val chunksFile = File(tempDir, "hierarchy.json")
        mdFile.writeText(
            """
            # Part One
            
            ## Chapter A
            
            ### Section A.1
            
            Content A.1.
            
            ### Section A.2
            
            Content A.2.
            
            ## Chapter B
            
            Content B.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        val sectionA1 = chunks.find { it.sectionPath.contains("A.1") }
        assertNotNull(sectionA1)
        assertTrue(sectionA1!!.sectionPath.contains("Part One"))
        assertTrue(sectionA1.sectionPath.contains("Chapter A"))
        assertTrue(sectionA1.sectionPath.contains(" > "), "Path should have separators")
    }

    @Test
    fun `overlap captures next section text`() {
        val mdFile = File(tempDir, "overlap.md")
        val chunksFile = File(tempDir, "overlap.json")
        mdFile.writeText(
            """
            # First Section
            
            First section content with multiple sentences.
            This is another sentence about the topic.
            And a third sentence to fill it up.
            
            ## Second Section
            
            Second section starts here.
            More second section text.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        val firstChunk = chunks.find { it.sectionPath.contains("First Section") }
        assertNotNull(firstChunk)
        val overlap = firstChunk!!.overlapNext
        assertNotNull(overlap, "Should have overlap to next section")
        assertTrue(overlap!!.contains("Second"), "Overlap should reference next section text")
    }

    @Test
    fun `last section has no overlap`() {
        val mdFile = File(tempDir, "last.md")
        val chunksFile = File(tempDir, "last.json")
        mdFile.writeText(
            """
            # First
            
            Content one.
            
            ## Last Section
            
            Final content here.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        val lastChunk = chunks.last()
        assertEquals(null, lastChunk.overlapNext, "Last section should have null overlap")
    }

    @Test
    fun `blank sections are skipped`() {
        val mdFile = File(tempDir, "blanks.md")
        val chunksFile = File(tempDir, "blanks.json")
        mdFile.writeText(
            """
            # Title
            
            Content.
            
            ## Empty Section
            
            
            ## Valid Section
            
            Valid content.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        val emptyChunk = chunks.find { it.sectionPath.contains("Empty Section") }
        assertNotNull(emptyChunk, "Empty heading sections are kept (heading line makes content non-blank)")
        assertTrue(chunks.any { it.sectionPath.contains("Valid Section") })
    }

    @Test
    fun `licence is propagated to all chunks`() {
        val mdFile = File(tempDir, "lic.md")
        val chunksFile = File(tempDir, "lic.json")
        mdFile.writeText(
            """
            # Doc
            
            ## A
            
            Text A.
            
            ## B
            
            Text B.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "PROPRIETARY")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { assertEquals("PROPRIETARY", it.license) }
    }

    @Test
    fun `entities field is empty by default`() {
        val mdFile = File(tempDir, "entities.md")
        val chunksFile = File(tempDir, "entities.json")
        mdFile.writeText(
            """
            # Entity Test
            
            Some content about artificial intelligence and machine learning.
            """.trimIndent()
        )

        val task = createTask(mdFile, chunksFile, "Apache-2.0")
        task.chunk()

        val chunks = parseChunks(chunksFile.readText())
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { assertTrue(it.entities.isEmpty(), "Entities should be empty by default") }
    }

    private fun createTask(
        mdFile: File,
        chunksFile: File,
        license: String
    ): ChunkDocumentTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "chunkDocument",
            ChunkDocumentTask::class.java
        ).get()
        task.markdownFile.set(mdFile)
        task.chunksFile.set(chunksFile)
        task.licenseName.set(license)
        return task
    }

    private fun parseChunks(json: String): List<DocumentChunk> {
        val j = Json { ignoreUnknownKeys = true; isLenient = true }
        return j.decodeFromString<List<DocumentChunk>>(json)
    }
}
