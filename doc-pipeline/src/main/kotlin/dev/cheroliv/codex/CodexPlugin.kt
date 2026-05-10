package dev.cheroliv.codex

import dev.cheroliv.codex.tasks.AsciiDocToJsonLddTask
import dev.cheroliv.codex.tasks.ChunkDocumentTask
import dev.cheroliv.codex.tasks.ConvertToMarkdownTask
import dev.cheroliv.codex.tasks.ExportKnowledgeBaseTask
import dev.cheroliv.codex.tasks.ExtractBookStructureTask
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
    }

    companion object {
        const val GROUP = "codex"
    }
}
