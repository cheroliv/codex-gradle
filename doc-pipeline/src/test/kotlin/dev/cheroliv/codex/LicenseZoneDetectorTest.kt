package dev.cheroliv.codex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LicenseZoneDetectorTest {

    @Test
    fun `detects OSS zone from foundry OSS path`() {
        assertEquals(
            LicenseZone.OSS,
            LicenseZoneDetector.detect("/home/user/foundry/OSS/codex-gradle")
        )
        assertEquals(
            LicenseZone.OSS,
            LicenseZoneDetector.detect("C:\\Users\\dev\\foundry\\OSS\\codex-gradle")
        )
    }

    @Test
    fun `detects CSS zone from foundry CSS path`() {
        assertEquals(
            LicenseZone.CSS,
            LicenseZoneDetector.detect("/home/user/foundry/CSS/internal-tool")
        )
        assertEquals(
            LicenseZone.CSS,
            LicenseZoneDetector.detect("C:\\Projects\\foundry\\CSS\\secret-project")
        )
    }

    @Test
    fun `detects UNKNOWN zone for paths without foundry`() {
        assertEquals(
            LicenseZone.UNKNOWN,
            LicenseZoneDetector.detect("/home/user/random/project")
        )
        assertEquals(
            LicenseZone.UNKNOWN,
            LicenseZoneDetector.detect("/tmp/build")
        )
    }

    @Test
    fun `detects UNKNOWN for foundry without OSS or CSS`() {
        assertEquals(
            LicenseZone.UNKNOWN,
            LicenseZoneDetector.detect("/opt/foundry/something/else")
        )
    }

    @Test
    fun `OSS maps to Apache-2-0 license name`() {
        assertEquals("Apache-2.0", LicenseZoneDetector.toLicenseName(LicenseZone.OSS))
    }

    @Test
    fun `CSS maps to PROPRIETARY license name`() {
        assertEquals("PROPRIETARY", LicenseZoneDetector.toLicenseName(LicenseZone.CSS))
    }

    @Test
    fun `UNKNOWN maps to UNKNOWN license name`() {
        assertEquals("UNKNOWN", LicenseZoneDetector.toLicenseName(LicenseZone.UNKNOWN))
    }
}
