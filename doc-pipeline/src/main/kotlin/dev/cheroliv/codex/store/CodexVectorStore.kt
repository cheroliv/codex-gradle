package dev.cheroliv.codex.store

import dev.cheroliv.codex.tasks.RetrieveResult
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux

/**
 * Public contract for semantic search over the codex document corpus.
 *
 * Decoupled from Gradle tasks — usable by engine N3 and any JVM consumer.
 * Manages its own ONNX embedding model and R2DBC pgvector connection.
 */
class CodexVectorStore(
    private val host: String = "localhost",
    private val port: Int = 5432,
    private val database: String = "codex",
    private val username: String = "codex",
    private val password: String = "codex"
) {
    private val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }

    fun searchBlocking(query: String, topK: Int = 10): List<RetrieveResult> =
        runBlocking { search(query, topK) }

    suspend fun search(query: String, topK: Int = 10): List<RetrieveResult> {
        val embedding = computeEmbedding(query)
        val factory = buildConnectionFactory()
        return searchSimilar(factory, embedding, topK)
    }

    private fun buildConnectionFactory(): ConnectionFactory {
        val config = PostgresqlConnectionConfiguration.builder()
            .host(host)
            .port(port)
            .database(database)
            .username(username)
            .password(password)
            .build()
        return PostgresqlConnectionFactory(config)
    }

    private fun computeEmbedding(text: String): String {
        val embedding = model.embed(TextSegment.from(text)).content()
        return embedding.vector().joinToString(",", "[", "]")
    }

    private suspend fun searchSimilar(
        factory: ConnectionFactory,
        vectorStr: String,
        k: Int
    ): List<RetrieveResult> {
        val conn = factory.create().awaitFirst()
        try {
            val sql = """
                SELECT
                    sub.chunk_id,
                    sub.chunk_index,
                    sub.chunk_text,
                    sub.section_path,
                    sub.heading_level,
                    sub.source_document,
                    1.0 - sub.distance AS similarity
                FROM (
                    SELECT
                        c.id AS chunk_id,
                        c.chunk_index,
                        c.chunk_text,
                        c.section_path,
                        c.heading_level,
                        d.source_document,
                        c.embedding <=> ${'$'}1::vector AS distance
                    FROM codex_chunks c
                    JOIN codex_documents d ON c.document_id = d.id
                    WHERE c.embedding IS NOT NULL
                ) sub
                ORDER BY sub.distance ASC
                LIMIT ${'$'}2
            """.trimIndent()

            val result = conn.createStatement(sql)
                .bind(0, vectorStr)
                .bind(1, k)
                .execute()
                .awaitFirst()

            return Flux.from(result.map { row, _ ->
                @Suppress("UNCHECKED_CAST")
                RetrieveResult(
                    chunkId = row.get("chunk_id", Long::class.java)!!,
                    chunkIndex = (row.get("chunk_index") as Number).toInt(),
                    chunkText = row.get("chunk_text", String::class.java)!!,
                    sectionPath = row.get("section_path", String::class.java)!!,
                    headingLevel = (row.get("heading_level") as Number).toInt(),
                    sourceDocument = row.get("source_document", String::class.java)!!,
                    similarity = row.get("similarity", Double::class.java)!!
                )
            }).collectList().awaitFirst()
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }
}
