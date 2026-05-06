package dev.cheroliv.codex

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
    }

    companion object {
        const val GROUP = "codex"
    }
}
