package dev.cheroliv.codex

import dev.cheroliv.codex.tasks.AsciiDocToJsonLddTask
import dev.cheroliv.codex.tasks.ChunkDocumentTask
import dev.cheroliv.codex.tasks.CodexIngestTask
import dev.cheroliv.codex.tasks.CodexRetrieveTask
import dev.cheroliv.codex.tasks.ConvertToMarkdownTask
import dev.cheroliv.codex.tasks.ExportKnowledgeBaseTask
import dev.cheroliv.codex.tasks.ExtractBookStructureTask
import dev.cheroliv.codex.tasks.ExtractEpubStructureTask
import dev.cheroliv.codex.tasks.ExtractTextTask
import dev.cheroliv.codex.tasks.ImportBookSqlTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodexPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val zone = LicenseZoneDetector.detect(project.projectDir.absolutePath)
        val license = LicenseZoneDetector.toLicenseName(zone)

        project.logger.lifecycle("[codex] Plugin chargé — pipeline doc activé (zone: $zone, licence: $license)")

        val extension = project.extensions.create("codex", CodexExtension::class.java)
        extension.zone.convention(zone)
        extension.pgvectorHost.convention("localhost")
        extension.pgvectorPort.convention("5432")
        extension.pgvectorDatabase.convention("codex")
        extension.pgvectorUser.convention("codex")
        extension.pgvectorPassword.convention("codex")

        project.tasks.register(
            "extractText",
            ExtractTextTask::class.java
        ) {
            it.group = GROUP
            it.description = "Extrait le texte brut structuré d'un PDF avec métadonnées typographiques"
        }

        project.tasks.register(
            "extractBookStructure",
            ExtractBookStructureTask::class.java
        ) {
            it.group = GROUP
            it.description = "Extrait la structure d'un PDF (titres, sections) et produit un .adoc hiérarchique"
        }

        project.tasks.register(
            "extractEpubStructure",
            ExtractEpubStructureTask::class.java
        ) {
            it.group = GROUP
            it.description = "Extrait la structure d'un EPUB (XHTML → .adoc avec hiérarchie et blocs de code)"
        }

        project.tasks.register(
            "asciiDocToJsonLdd",
            AsciiDocToJsonLddTask::class.java
        ) {
            it.group = GROUP
            it.description = "Parse un .adoc via AsciidoctorJ → JSON LDD structuré"
        }

        project.tasks.register(
            "importBookSql",
            ImportBookSqlTask::class.java
        ) {
            it.group = GROUP
            it.description = "JSON LDD → DDL + INSERT PostgreSQL"
        }

        project.tasks.register(
            "convertToMarkdown",
            ConvertToMarkdownTask::class.java
        ) {
            it.group = GROUP
            it.description = "Convertit un .adoc structure en Markdown avec hierarchie et blocs de code preserves"
        }

        project.tasks.register(
            "chunkDocument",
            ChunkDocumentTask::class.java
        ) {
            it.group = GROUP
            it.description = "Decoupe un document Markdown en chunks semantiques par section (1 chunk par heading)"
            it.licenseName.convention(license)
        }

        project.tasks.register(
            "exportKnowledgeBase",
            ExportKnowledgeBaseTask::class.java
        ) {
            it.group = GROUP
            it.description = "Agrege les chunks en base de connaissance multi-format (JSON-L, Markdown, AsciiDoc)"
        }

        project.tasks.register(
            "codexIngest",
            CodexIngestTask::class.java
        ) {
            it.group = GROUP
            it.description = "Vectorise les chunks avec ONNX AllMiniLmL6V2 et les stocke dans pgvector via R2DBC"
            it.pgHost.convention(extension.pgvectorHost)
            it.pgPort.convention(extension.pgvectorPort)
            it.pgDatabase.convention(extension.pgvectorDatabase)
            it.pgUser.convention(extension.pgvectorUser)
            it.pgPassword.convention(extension.pgvectorPassword)
        }

        project.tasks.register(
            "codexRetrieve",
            CodexRetrieveTask::class.java
        ) {
            it.group = GROUP
            it.description = "Recherche semantique cosine similarity dans pgvector — corpus documentaire requetable"
            it.topK.convention("10")
            it.pgHost.convention(extension.pgvectorHost)
            it.pgPort.convention(extension.pgvectorPort)
            it.pgDatabase.convention(extension.pgvectorDatabase)
            it.pgUser.convention(extension.pgvectorUser)
            it.pgPassword.convention(extension.pgvectorPassword)
        }
    }

    companion object {
        const val GROUP = "codex"
    }
}
