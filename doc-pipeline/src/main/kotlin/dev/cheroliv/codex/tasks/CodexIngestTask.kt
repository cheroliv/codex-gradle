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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class CodexIngestTask : DefaultTask() {

    @get:InputFile
    abstract val chunksFile: RegularFileProperty

    @get:Input
    abstract val pgHost: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val pgPort: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val pgDatabase: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val pgUser: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val pgPassword: org.gradle.api.provider.Property<String>

    private val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }

    @TaskAction
    fun ingest() = runBlocking {
        val input = chunksFile.asFile.get()

        logger.lifecycle("[codex] codexIngest : ${input.name} → pgvector (${pgHost.get()}:${pgPort.get()}/${pgDatabase.get()})")

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val chunks = json.decodeFromString<List<DocumentChunk>>(input.readText())

        if (chunks.isEmpty()) {
            logger.warn("[codex] Aucun chunk — ingestion annulée")
            return@runBlocking
        }

        val factory = buildConnectionFactory()
        initSchema(factory)
        val docCount = ingestChunks(factory, chunks)

        logger.lifecycle("[codex] ✓ codexIngest terminé — $docCount documents, ${chunks.size} chunks vectorisés dans pgvector")
    }

    private fun buildConnectionFactory(): ConnectionFactory {
        val config = PostgresqlConnectionConfiguration.builder()
            .host(pgHost.get())
            .port(pgPort.get().toInt())
            .database(pgDatabase.get())
            .username(pgUser.get())
            .password(pgPassword.get())
            .build()
        return PostgresqlConnectionFactory(config)
    }

    private suspend fun initSchema(factory: ConnectionFactory) {
        val conn = factory.create().awaitFirst()
        try {
            for (sql in listOf(
                "CREATE EXTENSION IF NOT EXISTS vector",
                """
                CREATE TABLE IF NOT EXISTS codex_documents (
                    id              BIGSERIAL PRIMARY KEY,
                    source_document TEXT NOT NULL,
                    chunk_count     INTEGER NOT NULL,
                    license         TEXT NOT NULL,
                    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                )
                """.trimIndent(),
                """
                CREATE TABLE IF NOT EXISTS codex_chunks (
                    id              BIGSERIAL PRIMARY KEY,
                    document_id     BIGINT NOT NULL REFERENCES codex_documents(id) ON DELETE CASCADE,
                    chunk_index     INTEGER NOT NULL,
                    chunk_text      TEXT NOT NULL,
                    section_path    TEXT NOT NULL,
                    heading_level   INTEGER NOT NULL DEFAULT 0,
                    embedding       vector(384),
                    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                )
                """.trimIndent()
            )) {
                conn.createStatement(sql).execute().awaitFirst()
            }
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    private suspend fun ingestChunks(factory: ConnectionFactory, chunks: List<DocumentChunk>): Int {
        val groups = chunks.groupBy { it.sourceDocument }
        var docCount = 0

        for ((sourceDocument, docChunks) in groups) {
            val license = docChunks.firstOrNull()?.license ?: "UNKNOWN"

            val docId = insertDocument(factory, sourceDocument, docChunks.size, license)
            logger.lifecycle("[codex]   Document: $sourceDocument (${docChunks.size} chunks)")

            for ((index, chunk) in docChunks.withIndex()) {
                val chunkId = insertChunk(factory, docId, index, chunk.content, chunk.sectionPath, chunk.headingLevel)
                val embedding = computeEmbedding(chunk.content)
                updateEmbedding(factory, chunkId, embedding)
            }

            docCount++
            logger.lifecycle("[codex]   ✓ ${docChunks.size} embeddings stockés pour $sourceDocument")
        }

        return docCount
    }

    private suspend fun insertDocument(
        factory: ConnectionFactory,
        sourceDocument: String,
        chunkCount: Int,
        license: String
    ): Long {
        val conn = factory.create().awaitFirst()
        try {
            val result = conn.createStatement(
                "INSERT INTO codex_documents (source_document, chunk_count, license) VALUES (\$1, \$2, \$3) RETURNING id"
            )
                .bind(0, sourceDocument)
                .bind(1, chunkCount)
                .bind(2, license)
                .execute()
                .awaitFirst()

            return result.map { row, _ -> row.get("id", Long::class.java) }
                .awaitFirstOrNull()
                ?: error("INSERT codex_documents n'a pas retourné d'id pour $sourceDocument")
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    private suspend fun insertChunk(
        factory: ConnectionFactory,
        docId: Long,
        chunkIndex: Int,
        chunkText: String,
        sectionPath: String,
        headingLevel: Int
    ): Long {
        val conn = factory.create().awaitFirst()
        try {
            val result = conn.createStatement(
                """
                INSERT INTO codex_chunks (document_id, chunk_index, chunk_text, section_path, heading_level)
                VALUES (\$1, \$2, \$3, \$4, \$5)
                RETURNING id
                """.trimIndent()
            )
                .bind(0, docId)
                .bind(1, chunkIndex)
                .bind(2, chunkText)
                .bind(3, sectionPath)
                .bind(4, headingLevel)
                .execute()
                .awaitFirst()

            return result.map { row, _ -> row.get("id", Long::class.java) }
                .awaitFirstOrNull()
                ?: error("INSERT codex_chunks n'a pas retourné d'id")
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    private fun computeEmbedding(text: String): String {
        val embedding = model.embed(TextSegment.from(text)).content()
        return embedding.vector().joinToString(",", "[", "]")
    }

    private suspend fun updateEmbedding(factory: ConnectionFactory, chunkId: Long, vectorStr: String) {
        val conn = factory.create().awaitFirst()
        try {
            conn.createStatement("UPDATE codex_chunks SET embedding = \$1::vector WHERE id = \$2")
                .bind(0, vectorStr)
                .bind(1, chunkId)
                .execute()
                .awaitFirst()
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }
}
