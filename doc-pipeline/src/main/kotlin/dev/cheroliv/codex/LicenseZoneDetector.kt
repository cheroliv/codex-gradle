package dev.cheroliv.codex

enum class LicenseZone { OSS, CSS, UNKNOWN }

object LicenseZoneDetector {

    fun detect(projectDir: String): LicenseZone {
        val normalized = projectDir.replace("\\", "/")
        return when {
            normalized.contains("/foundry/OSS/") -> LicenseZone.OSS
            normalized.contains("/foundry/CSS/") -> LicenseZone.CSS
            else -> LicenseZone.UNKNOWN
        }
    }

    fun toLicenseName(zone: LicenseZone): String = when (zone) {
        LicenseZone.OSS -> "Apache-2.0"
        LicenseZone.CSS -> "PROPRIETARY"
        LicenseZone.UNKNOWN -> "UNKNOWN"
    }
}
