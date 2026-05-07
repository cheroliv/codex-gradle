package dev.cheroliv.codex

import dev.cheroliv.codex.tasks.ExtractBookStructureTask
import dev.cheroliv.codex.tasks.ExtractTextTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodexPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.lifecycle("[codex] Plugin chargé — pipeline doc activé")

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
    }

    companion object {
        const val GROUP = "codex"
    }
}
