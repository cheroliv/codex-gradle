package dev.cheroliv.codex.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AsciiDocToJsonLddTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `converts simple hierarchy to JSON LDD`() {
        val adocFile = File(tempDir, "test.adoc")
        val jsonFile = File(tempDir, "test.json")
        adocFile.writeText(
            """
            = Main Title
            
            == Chapter One
            
            Some chapter content.
            
            === Section 1.1
            
            Section content here.
            
            == Chapter Two
            
            Second chapter text.
            """.trimIndent()
        )

        val task = createTask(adocFile, jsonFile)
        task.convert()

        assertTrue(jsonFile.exists())
        val json = jsonFile.readText()
        assertTrue(json.contains("\"title\": \"Chapter One\""), "Should contain first chapter, got: $json")
        assertTrue(json.contains("\"level\": 1"), "Should have level 1")
        assertTrue(json.contains("\"title\": \"Chapter One\""))
        assertTrue(json.contains("\"title\": \"Chapter Two\""))
        assertTrue(json.contains("\"title\": \"Section 1.1\""))
    }

    @Test
    fun `paragraphs appear in JSON LDD`() {
        val adocFile = File(tempDir, "para.adoc")
        val jsonFile = File(tempDir, "para.json")
        adocFile.writeText(
            """
            = Doc
            
            This is a paragraph of text.
            
            Another paragraph here.
            """.trimIndent()
        )

        val task = createTask(adocFile, jsonFile)
        task.convert()

        val json = jsonFile.readText()
        assertTrue(json.contains("\"type\": \"paragraph\""), "Should contain paragraphs, got: $json")
    }

    @Test
    fun `empty document produces valid JSON`() {
        val adocFile = File(tempDir, "empty.adoc")
        val jsonFile = File(tempDir, "empty.json")
        adocFile.writeText("= Empty Doc\n")

        val task = createTask(adocFile, jsonFile)
        task.convert()

        assertTrue(jsonFile.exists())
        assertEquals(1, jsonFile.readText().lines().size, "Empty doc produces empty JSON array")
    }

    @Test
    fun `nested children are preserved in JSON`() {
        val adocFile = File(tempDir, "nested.adoc")
        val jsonFile = File(tempDir, "nested.json")
        adocFile.writeText(
            """
            = Root
            
            == A
            
            === A.1
            
            ==== A.1.1
            
            === A.2
            
            == B
            """.trimIndent()
        )

        val task = createTask(adocFile, jsonFile)
        task.convert()

        val json = jsonFile.readText()
        assertTrue(json.contains("\"title\": \"A\""))
        assertTrue(json.contains("\"title\": \"A.1\""))
        assertTrue(json.contains("\"title\": \"A.1.1\""))
        assertTrue(json.contains("\"title\": \"A.2\""))
        assertTrue(json.contains("\"title\": \"B\""))
        assertTrue(json.contains("\"children\""), "Should contain nested children, got: $json")
    }

    private fun createTask(adocFile: File, jsonFile: File): AsciiDocToJsonLddTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "asciiDocToJsonLdd",
            AsciiDocToJsonLddTask::class.java
        ).get()
        task.adocFile.set(adocFile)
        task.jsonFile.set(jsonFile)
        return task
    }
}
