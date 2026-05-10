package dev.cheroliv.codex.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ImportBookSqlTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `generates DDL and INSERT from simple JSON LDD`() {
        val jsonFile = File(tempDir, "book.json")
        val sqlFile = File(tempDir, "book.sql")
        jsonFile.writeText(
            """
            [
              {
                "title": "My Book",
                "level": 1,
                "children": [
                  {
                    "title": "Chapter 1",
                    "level": 2
                  },
                  {
                    "title": "Chapter 2",
                    "level": 2,
                    "children": [
                      {
                        "title": "Section 2.1",
                        "level": 3
                      }
                    ]
                  }
                ]
              }
            ]
            """.trimIndent()
        )

        val task = createTask(jsonFile, sqlFile)
        task.generate()

        assertTrue(sqlFile.exists())
        val sql = sqlFile.readText()

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS book"), "Should have book DDL")
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS documents"), "Should have documents DDL")
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS paragraphs"), "Should have paragraphs DDL")
        assertTrue(sql.contains("INSERT INTO book"), "Should have book INSERT")
        assertTrue(sql.contains("INSERT INTO documents"), "Should have document INSERT")
        assertTrue(sql.contains("BEGIN;"), "Should be transactional")
        assertTrue(sql.contains("COMMIT;"), "Should be transactional")
    }

    @Test
    fun `generates INSERT with correct book title`() {
        val jsonFile = File(tempDir, "titled.json")
        val sqlFile = File(tempDir, "titled.sql")
        jsonFile.writeText(
            """
            [
              {
                "title": "Kotlin Programming Guide",
                "level": 1
              }
            ]
            """.trimIndent()
        )

        val task = createTask(jsonFile, sqlFile)
        task.generate()

        val sql = sqlFile.readText()
        assertTrue(sql.contains("'Kotlin Programming Guide'"), "Should contain book title, got: $sql")
    }

    @Test
    fun `paragraphs get their own INSERTs`() {
        val jsonFile = File(tempDir, "with-para.json")
        val sqlFile = File(tempDir, "with-para.sql")
        jsonFile.writeText(
            """
            [
              {
                "title": "Doc",
                "level": 1,
                "children": [
                  {
                    "title": "Section A",
                    "level": 2,
                    "children": [
                      {
                        "type": "paragraph",
                        "text": "Hello from paragraph"
                      }
                    ]
                  }
                ]
              }
            ]
            """.trimIndent()
        )

        val task = createTask(jsonFile, sqlFile)
        task.generate()

        val sql = sqlFile.readText()
        assertTrue(sql.contains("INSERT INTO paragraphs"), "Should have paragraph INSERT")
        assertTrue(sql.contains("Hello from paragraph"), "Should contain paragraph text")
    }

    @Test
    fun `empty JSON array produces minimal SQL`() {
        val jsonFile = File(tempDir, "empty.json")
        val sqlFile = File(tempDir, "empty.sql")
        jsonFile.writeText("[]")

        val task = createTask(jsonFile, sqlFile)
        task.generate()

        assertTrue(sqlFile.exists())
        val sql = sqlFile.readText()
        assertTrue(sql.contains("CREATE TABLE"), "Should have DDL even for empty input")
        assertTrue(sql.contains("BEGIN;"))
        assertTrue(sql.contains("COMMIT;"))
    }

    private fun createTask(jsonFile: File, sqlFile: File): ImportBookSqlTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "importBookSql",
            ImportBookSqlTask::class.java
        ).get()
        task.jsonFile.set(jsonFile)
        task.sqlFile.set(sqlFile)
        return task
    }
}
