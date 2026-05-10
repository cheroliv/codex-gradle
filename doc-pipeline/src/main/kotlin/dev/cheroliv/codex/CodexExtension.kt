package dev.cheroliv.codex

import org.gradle.api.provider.Property

abstract class CodexExtension {
    abstract val zone: Property<LicenseZone>

    abstract val pgvectorHost: Property<String>
    abstract val pgvectorPort: Property<String>
    abstract val pgvectorDatabase: Property<String>
    abstract val pgvectorUser: Property<String>
    abstract val pgvectorPassword: Property<String>
}
