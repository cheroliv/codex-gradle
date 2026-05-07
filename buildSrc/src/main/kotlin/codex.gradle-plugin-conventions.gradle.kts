import dev.cheroliv.codex.tasks.ExtractBookStructureTask
import dev.cheroliv.codex.tasks.ExtractTextTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.gradle.plugin-publish")
    kotlin("jvm")
}

group = "com.cheroliv"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
    jvmToolchain(25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("FAILED", "SKIPPED")
    }
}

val CODEX_GROUP = "codex"

tasks.register("extractText", ExtractTextTask::class.java) {
    group = CODEX_GROUP
    description = "Extrait le texte brut structuré d'un PDF avec métadonnées typographiques"
    pdfFile = project.propertyOrNull("pdfFile")?.toString()?.let { project.layout.projectDirectory.file(it) }
        ?: project.objects.fileProperty().also { it.set(project.providers.gradleProperty("pdfFile")) }
    outputFile = project.propertyOrNull("outputFile")?.toString()?.let { project.layout.projectDirectory.file(it) }
        ?: project.objects.fileProperty().also { it.set(project.providers.gradleProperty("outputFile")) }
}

tasks.register("extractBookStructure", ExtractBookStructureTask::class.java) {
    group = CODEX_GROUP
    description = "Extrait la structure d'un PDF (titres, sections) et produit un .adoc hiérarchique"
    val pdf = project.propertyOrNull("pdfFile")?.toString()
    val out = project.propertyOrNull("outputFile")?.toString()
    if (pdf != null) pdfFile.set(project.file(pdf))
    if (out != null) outputFile.set(project.file(out))
}

fun Project.propertyOrNull(name: String): Any? =
    if (hasProperty(name)) property(name) else null

publishing {
    repositories {
        maven {
            name = "localRepo"
            url = uri(rootProject.layout.buildDirectory.dir("local-repo"))
        }
    }
}
