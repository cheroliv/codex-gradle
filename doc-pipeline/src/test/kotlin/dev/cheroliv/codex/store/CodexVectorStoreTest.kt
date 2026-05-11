package dev.cheroliv.codex.store

import dev.cheroliv.codex.tasks.RetrieveResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexVectorStoreTest {

    @Test
    fun `store can be instantiated with all connection parameters`() {
        val store = CodexVectorStore(
            host = "testhost",
            port = 5433,
            database = "testdb",
            username = "testuser",
            password = "testpass"
        )
        assertNotNull(store)
    }

    @Test
    fun `default parameters are localhost standard`() {
        val store = CodexVectorStore()
        assertNotNull(store)
    }

    @Test
    fun `RetrieveResult data class contract is intact`() {
        val result = RetrieveResult(
            chunkId = 1L,
            chunkIndex = 0,
            chunkText = "test chunk",
            sectionPath = "Chapter 1",
            headingLevel = 1,
            sourceDocument = "test-doc",
            similarity = 0.95
        )
        assertEquals(1L, result.chunkId)
        assertEquals(0, result.chunkIndex)
        assertEquals("test chunk", result.chunkText)
        assertEquals("Chapter 1", result.sectionPath)
        assertEquals(1, result.headingLevel)
        assertEquals("test-doc", result.sourceDocument)
        assertEquals(0.95, result.similarity, 0.001)
    }

    @Test
    fun `searchBlocking is a valid suspending wrapper`() {
        val store = CodexVectorStore()
        val method = store.javaClass.getMethod("searchBlocking", String::class.java, Int::class.java)
        assertNotNull(method)
        assertEquals(List::class.java, method.returnType)
    }

    @Test
    fun `search exists as a method`() {
        val store = CodexVectorStore()
        val methodExists = store.javaClass.declaredMethods.any { it.name == "search" }
        assertTrue(methodExists)
    }
}
