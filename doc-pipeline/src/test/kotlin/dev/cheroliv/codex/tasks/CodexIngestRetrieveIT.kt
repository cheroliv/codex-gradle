package dev.cheroliv.codex.tasks

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Path
import kotlin.io.path.writeText

@Tag("integration")
class CodexIngestRetrieveIT {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("codex").withUsername("codex").withPassword("codex")

        @JvmStatic @BeforeAll fun start() { postgres.start() }
        @JvmStatic @AfterAll fun stop() { postgres.stop() }

        fun host() = postgres.host
        fun port() = postgres.firstMappedPort
        fun db() = postgres.databaseName
        fun user() = postgres.username
        fun pass() = postgres.password
    }

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun makeIngestTask(project: org.gradle.api.Project, chunksFile: java.io.File, batch: String = "32") =
        project.tasks.register("codexIngest", CodexIngestTask::class.java).get().also {
            it.chunksFile.set(chunksFile); it.pgHost.set(host()); it.pgPort.set(port().toString())
            it.pgDatabase.set(db()); it.pgUser.set(user()); it.pgPassword.set(pass())
            it.batchSize.set(batch)
        }

    private fun makeRetrieveTask(project: org.gradle.api.Project, query: String, topK: String, output: java.io.File) =
        project.tasks.register("codexRetrieve", CodexRetrieveTask::class.java).get().also {
            it.query.set(query); it.topK.set(topK)
            it.pgHost.set(host()); it.pgPort.set(port().toString())
            it.pgDatabase.set(db()); it.pgUser.set(user()); it.pgPassword.set(pass())
            it.outputFile.set(output)
        }

    @Test
    fun `end-to-end ingest and retrieve`(@TempDir tempDir: Path) {
        val chunksFile = tempDir.resolve("chunks.json").toFile()
        val chunks = listOf(
            DocumentChunk("chk-001", "test-book", "Ch1", 1, "Machine learning fundamentals.", license = "Apache-2.0"),
            DocumentChunk("chk-002", "test-book", "Ch2", 1, "Deep neural network architectures.", license = "Apache-2.0"),
            DocumentChunk("chk-003", "test-book", "Ch3", 1, "French cuisine recipes.", license = "Apache-2.0")
        )
        chunksFile.writeText(json.encodeToString(ListSerializer(DocumentChunk.serializer()), chunks))

        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        makeIngestTask(project, chunksFile, "2").ingest()

        val outputFile = tempDir.resolve("retrieve.json").toFile()
        makeRetrieveTask(project, "machine learning", "3", outputFile).retrieve()

        assertTrue(outputFile.exists())
        val results = Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<List<RetrieveResult>>(outputFile.readText())
        assertFalse(results.isEmpty(), "Should return results but got ${results.size}")
    }

    @Test
    fun `ingest empty chunks`(@TempDir tempDir: Path) {
        val f = tempDir.resolve("empty.json").toFile(); f.writeText("[]")
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        makeIngestTask(project, f).ingest()
    }

    @Test
    fun `batching with batch size 1`(@TempDir tempDir: Path) {
        val chunksFile = tempDir.resolve("batch.json").toFile()
        val chunks = (1..3).map { i -> DocumentChunk("chk-b$i", "batch-test", "S$i", 1, "Content $i.", license = "Apache-2.0") }
        chunksFile.writeText(json.encodeToString(ListSerializer(DocumentChunk.serializer()), chunks))

        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        makeIngestTask(project, chunksFile, "1").ingest()

        val outputFile = tempDir.resolve("batch-retrieve.json").toFile()
        makeRetrieveTask(project, "Content", "3", outputFile).retrieve()
        val results = Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<List<RetrieveResult>>(outputFile.readText())
        assertEquals(3, results.size, "Expected 3 results, got ${results.size}")
    }

    @Test
    fun `codexPipeline auto-detects PDF`(@TempDir tempDir: Path) {
        val pdf = """%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj
xref 0 4
0000000000 65535 f 
0000000009 00000 n 
0000000052 00000 n 
0000000101 00000 n 
trailer<</Size 4/Root 1 0 R>>
startxref 190
%%EOF""".trimIndent()
        val pdfFile = tempDir.resolve("doc.pdf").toFile(); pdfFile.writeText(pdf)
        val outputFile = tempDir.resolve("pipeline.json").toFile()
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.tasks.register("codexPipeline", CodexPipelineTask::class.java).get().also {
            it.sourceFile.set(pdfFile); it.outputFile.set(outputFile); it.licenseName.set("Apache-2.0")
            it.pgHost.set(host()); it.pgPort.set(port().toString())
            it.pgDatabase.set(db()); it.pgUser.set(user()); it.pgPassword.set(pass())
        }.pipeline()
        assertTrue(outputFile.readText().contains("PDF"))
    }
}
