plugins {
    `java-library`
    signing
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    id("codex.gradle-plugin-conventions")
}

group = "com.cheroliv"

version = libs.versions.doc.pipeline.get()

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)

    // PDF extraction
    implementation(libs.pdfbox)
    implementation(libs.tika.core)
    implementation(libs.tika.parsers.standard)

    // Document conversion
    implementation(libs.flexmark.all)
    implementation(libs.asciidoctorj)

    // Sérialisation
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)

    // RAG/Embedding — ONNX pgvector (R2DBC)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.minilm)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.spi)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)

    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    website = "https://github.com/cheroliv/codex"
    vcsUrl  = "https://github.com/cheroliv/codex"
    plugins {
        create("codexDocPipeline") {
            id                  = "com.cheroliv.codex.doc-pipeline"
            implementationClass = "dev.cheroliv.codex.CodexPlugin"
            displayName         = "Codex — Pipeline d'acquisition de documents"
            description         = """
                Pipeline Gradle d'acquisition de documents PDF/EPUB pour
                alimenter la base de connaissance RAG + Knowledge Graph.
                Extraction typographique, conversion Markdown/AsciiDoc,
                chunking sémantique, export structuré (JSON-L, Markdown, AsciiDoc).
            """.trimIndent()
            tags = listOf(
                "pdf", "epub", "markdown", "asciidoc",
                "rag", "knowledge-graph", "text-extraction",
                "chunking", "kotlin"
            )
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("codexDocPipeline").displayName)
                    description.set(gradlePlugin.plugins.getByName("codexDocPipeline").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cheroliv")
                            name.set("cheroliv")
                            email.set("cheroliv.developer@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = (if (version.toString().endsWith("-SNAPSHOT"))
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"))
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
        mavenCentral()
    }
}

signing {
    val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")
    if (isReleaseVersion) sign(publishing.publications)
    useGpgCmd()
}

java {
    withJavadocJar()
    withSourcesJar()
}

kover {
    reports {
        total {
            xml { onCheck = true }
            html { onCheck = true }
        }
    }
}
