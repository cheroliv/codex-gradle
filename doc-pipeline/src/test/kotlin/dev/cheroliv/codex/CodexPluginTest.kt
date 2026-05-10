package dev.cheroliv.codex

import dev.cheroliv.codex.tasks.AsciiDocToJsonLddTask
import dev.cheroliv.codex.tasks.ConvertToMarkdownTask
import dev.cheroliv.codex.tasks.ExtractBookStructureTask
import dev.cheroliv.codex.tasks.ExtractTextTask
import dev.cheroliv.codex.tasks.ImportBookSqlTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CodexPluginTest {

    @Test
    fun `plugin registers all 5 tasks`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.codex.doc-pipeline")

        val extractText = project.tasks.findByName("extractText")
        assertNotNull(extractText, "extractText task should be registered")
        assertEquals("codex", extractText?.group)

        val extractBookStructure = project.tasks.findByName("extractBookStructure")
        assertNotNull(extractBookStructure, "extractBookStructure task should be registered")

        val asciiDocToJsonLdd = project.tasks.findByName("asciiDocToJsonLdd")
        assertNotNull(asciiDocToJsonLdd, "asciiDocToJsonLdd task should be registered")

        val importBookSql = project.tasks.findByName("importBookSql")
        assertNotNull(importBookSql, "importBookSql task should be registered")

        val convertToMarkdown = project.tasks.findByName("convertToMarkdown")
        assertNotNull(convertToMarkdown, "convertToMarkdown task should be registered")
    }

    @Test
    fun `tasks have correct types`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.codex.doc-pipeline")

        assert(project.tasks.getByName("extractText") is ExtractTextTask)
        assert(project.tasks.getByName("extractBookStructure") is ExtractBookStructureTask)
        assert(project.tasks.getByName("asciiDocToJsonLdd") is AsciiDocToJsonLddTask)
        assert(project.tasks.getByName("importBookSql") is ImportBookSqlTask)
        assert(project.tasks.getByName("convertToMarkdown") is ConvertToMarkdownTask)
    }

    @Test
    fun `all tasks belong to codex group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.codex.doc-pipeline")

        project.tasks.filter { it.group == "codex" }.forEach { task ->
            assertEquals("codex", task.group, "${task.name} should be in codex group")
        }
        assertEquals(5, project.tasks.count { it.group == "codex" })
    }
}
