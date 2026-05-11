package dev.cheroliv.codex.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexRetrieveTaskTest {

    @Test
    fun `task is registered and has correct type`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("codexRetrieve", CodexRetrieveTask::class.java).get()

        assertNotNull(task)
        assertTrue(task is CodexRetrieveTask)
    }

    @Test
    fun `task properties are configurable`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("codexRetrieve", CodexRetrieveTask::class.java).get()

        task.query.set("What is the capital of France?")
        task.topK.set("5")
        task.pgHost.set("localhost")
        task.pgPort.set("5432")
        task.pgDatabase.set("codex")
        task.pgUser.set("codex")
        task.pgPassword.set("codex")

        assertEquals("What is the capital of France?", task.query.get())
        assertEquals("5", task.topK.get())
        assertEquals("localhost", task.pgHost.get())
        assertEquals("5432", task.pgPort.get())
        assertEquals("codex", task.pgDatabase.get())
        assertEquals("codex", task.pgUser.get())
        assertEquals("codex", task.pgPassword.get())
    }
}
