package dev.cheroliv.codex

enum class LicenseZone { OSS, CSS, UNKNOWN }

object LicenseZoneDetector {

    fun detect(projectDir: String): LicenseZone {
        val normalized = projectDir.replace("\\", "/")
        return when {
            normalized.contains("/foundry/public/") -> LicenseZone.OSS
            normalized.contains("/foundry/private/") -> LicenseZone.CSS
            else -> LicenseZone.UNKNOWN
        }
    }

    fun toLicenseName(zone: LicenseZone): String = when (zone) {
        LicenseZone.OSS -> "Apache-2.0"
        LicenseZone.CSS -> "PROPRIETARY"
        LicenseZone.UNKNOWN -> "UNKNOWN"
    }
}
