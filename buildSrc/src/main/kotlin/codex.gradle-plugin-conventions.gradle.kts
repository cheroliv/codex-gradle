import dev.cheroliv.codex.tasks.AsciiDocToJsonLddTask
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
    testLogging { events("FAILED", "SKIPPED") }
}

tasks.register("extractText", ExtractTextTask::class.java) {
    group = "codex"
    description = "Extrait le texte brut structuré d'un PDF avec métadonnées typographiques"
    if (project.hasProperty("pdfFile"))
        pdfFile.set(project.file(project.property("pdfFile") as String))
    if (project.hasProperty("outputFile"))
        outputFile.set(project.file(project.property("outputFile") as String))
}

tasks.register("extractBookStructure", ExtractBookStructureTask::class.java) {
    group = "codex"
    description = "Extrait la structure d'un PDF (titres, sections) et produit un .adoc hiérarchique"
    if (project.hasProperty("pdfFile"))
        pdfFile.set(project.file(project.property("pdfFile") as String))
    if (project.hasProperty("outputFile"))
        outputFile.set(project.file(project.property("outputFile") as String))
}

tasks.register("asciiDocToJsonLdd", AsciiDocToJsonLddTask::class.java) {
    group = "codex"
    description = "Parse un .adoc via AsciidoctorJ → JSON LDD structuré"
    if (project.hasProperty("adocFile"))
        adocFile.set(project.file(project.property("adocFile") as String))
    if (project.hasProperty("jsonFile"))
        jsonFile.set(project.file(project.property("jsonFile") as String))
}

publishing {
    repositories {
        maven {
            name = "localRepo"
            url = uri(rootProject.layout.buildDirectory.dir("local-repo"))
        }
    }
}
