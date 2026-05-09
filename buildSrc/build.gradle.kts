plugins { `kotlin-dsl` }

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.plugin.publish.gradle.plugin)
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.asciidoctor:asciidoctorj:3.0.0")
}
