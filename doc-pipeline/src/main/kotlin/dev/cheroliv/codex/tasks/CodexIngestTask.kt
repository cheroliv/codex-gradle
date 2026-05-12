package dev.cheroliv.codex.tasks

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class CodexIngestTask : DefaultTask() {

    @get:InputFile abstract val chunksFile: RegularFileProperty
    @get:Input abstract val pgHost: Property<String>
    @get:Input abstract val pgPort: Property<String>
    @get:Input abstract val pgDatabase: Property<String>
    @get:Input abstract val pgUser: Property<String>
    @get:Input abstract val pgPassword: Property<String>
    @get:Optional @get:Input abstract val batchSize: Property<String>

    private val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }

    @TaskAction
    fun ingest() = runBlocking {
        val input = chunksFile.asFile.get()
        val host = pgHost.get(); val port = pgPort.get().toInt()
        val db = pgDatabase.get(); val user = pgUser.get(); val pass = pgPassword.get()

        logger.lifecycle("[codex] codexIngest : ${input.name} → pgvector ($host:$port/$db)")

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val chunks = json.decodeFromString<List<DocumentChunk>>(input.readText())
        if (chunks.isEmpty()) { logger.warn("[codex] Aucun chunk"); return@runBlocking }

        val factory = createFactory(host, port, db, user, pass)
        initSchema(factory)
        val conn = factory.create().awaitFirst()
        try {
            val docCount = ingestChunks(conn, chunks)
            logger.lifecycle("[codex] ✓ codexIngest — $docCount docs, ${chunks.size} chunks")
        } finally { conn.close().awaitFirstOrNull() }
    }

    private fun createFactory(host: String, port: Int, db: String, user: String, pass: String): ConnectionFactory =
        PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(host).port(port).database(db).username(user).password(pass).build()
        )

    private suspend fun initSchema(factory: ConnectionFactory) {
        val conn = factory.create().awaitFirst()
        try {
            listOf(
                "CREATE EXTENSION IF NOT EXISTS vector",
                "CREATE TABLE IF NOT EXISTS codex_documents (id BIGSERIAL PRIMARY KEY, source_document TEXT NOT NULL, chunk_count INTEGER NOT NULL, license TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT NOW())",
                "CREATE TABLE IF NOT EXISTS codex_chunks (id BIGSERIAL PRIMARY KEY, document_id BIGINT REFERENCES codex_documents(id) ON DELETE CASCADE, chunk_index INTEGER NOT NULL, chunk_text TEXT NOT NULL, section_path TEXT NOT NULL, heading_level INTEGER DEFAULT 0, embedding vector(384), created_at TIMESTAMPTZ DEFAULT NOW())"
            ).forEach { conn.createStatement(it).execute().awaitFirst() }
        } finally { conn.close().awaitFirstOrNull() }
    }

    private suspend fun ingestChunks(conn: io.r2dbc.spi.Connection, chunks: List<DocumentChunk>): Int {
        val groups = chunks.groupBy { it.sourceDocument }
        val effectiveBatchSize = batchSize.orNull?.toIntOrNull() ?: 32
        var docCount = 0

        for ((source, docChunks) in groups) {
            val license = docChunks.first().license
            val docId = conn.createStatement(
                "INSERT INTO codex_documents (source_document, chunk_count, license) VALUES (${'$'}1, ${'$'}2, ${'$'}3) RETURNING id"
            ).bind(0, source).bind(1, docChunks.size).bind(2, license)
                .execute().awaitFirst().map { r, _ -> r.get("id", Long::class.java)!! }.awaitFirst()

            logger.lifecycle("[codex]   $source (${docChunks.size} chunks, batch=$effectiveBatchSize)")

            var stored = 0
            for (batch in docChunks.chunked(effectiveBatchSize)) {
                for ((i, chunk) in batch.withIndex()) {
                    val idx = chunks.indexOf(chunk)
                    val chunkId = conn.createStatement(
                        "INSERT INTO codex_chunks (document_id, chunk_index, chunk_text, section_path, heading_level) VALUES (${'$'}1, ${'$'}2, ${'$'}3, ${'$'}4, ${'$'}5) RETURNING id"
                    ).bind(0, docId).bind(1, idx).bind(2, chunk.content)
                        .bind(3, chunk.sectionPath).bind(4, chunk.headingLevel)
                        .execute().awaitFirst().map { r, _ -> r.get("id", Long::class.java)!! }.awaitFirst()

                    val vec = computeEmbedding(chunk.content)
                    conn.createStatement("UPDATE codex_chunks SET embedding = '[$vec]'::vector WHERE id = $chunkId")
                        .execute().awaitFirst()
                }
                stored += batch.size
            }
            docCount++
            logger.lifecycle("[codex]   ✓ $stored embeddings")
        }
        return docCount
    }

    private fun computeEmbedding(text: String): String {
        val v = model.embed(TextSegment.from(text)).content().vector()
        return v.joinToString(",")
    }
}
