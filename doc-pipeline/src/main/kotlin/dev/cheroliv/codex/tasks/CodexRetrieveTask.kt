package dev.cheroliv.codex.tasks

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import reactor.core.publisher.Flux

@Serializable
data class RetrieveResult(
    val chunkId: Long,
    val chunkIndex: Int,
    val chunkText: String,
    val sectionPath: String,
    val headingLevel: Int,
    val sourceDocument: String,
    val similarity: Double
)
abstract class CodexRetrieveTask : DefaultTask() {

    @get:Input
    abstract val query: Property<String>

    @get:Input
    abstract val topK: Property<String>

    @get:Input
    abstract val pgHost: Property<String>

    @get:Input
    abstract val pgPort: Property<String>

    @get:Input
    abstract val pgDatabase: Property<String>

    @get:Input
    abstract val pgUser: Property<String>

    @get:Input
    abstract val pgPassword: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }

    @TaskAction
    fun retrieve() = runBlocking {
        val q = query.get()
        val k = topK.get().toInt()

        logger.lifecycle("[codex] codexRetrieve : \"${q.take(80)}\" → pgvector (top-$k)")

        val embedding = computeEmbedding(q)
        val factory = buildConnectionFactory()
        val results = searchSimilar(factory, embedding, k)

        val output = outputFile.asFile.get()
                @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
                val json = Json { prettyPrint = true; prettyPrintIndent = "  " }
        output.writeText(json.encodeToString(results))

        logger.lifecycle("[codex] ✓ codexRetrieve terminé — ${results.size} résultats retournés")
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

    private fun computeEmbedding(text: String): String {
        val embedding = model.embed(TextSegment.from(text)).content()
        return embedding.vector().joinToString(",", "[", "]")
    }

    private suspend fun searchSimilar(factory: ConnectionFactory, vectorStr: String, k: Int): List<RetrieveResult> {
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
