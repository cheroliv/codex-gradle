package dev.cheroliv.codex

import org.gradle.api.provider.Property

abstract class CodexExtension {
    abstract val zone: Property<LicenseZone>
}
