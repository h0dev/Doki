package org.dokiteam.doki.core.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateRepositoryTest {

	@Test
	fun testExtractVersionFromReleaseName_StableRelease() {
		val result = extractVersionFromReleaseName("Stable Release 1.2.7")
		assertEquals("1.2.7", result)
	}

	@Test
	fun testExtractVersionFromReleaseName_BetaRelease() {
		val result = extractVersionFromReleaseName("Beta Release 1.2.7")
		assertEquals("1.2.7", result)
	}

	@Test
	fun testExtractVersionFromReleaseName_BetaBuild() {
		// Old format with commit hash should return null
		val result = extractVersionFromReleaseName("Beta Build abc123")
		assertNull(result)
	}

	@Test
	fun testExtractVersionFromReleaseName_StableReleaseMain() {
		// Old format with branch name should return null
		val result = extractVersionFromReleaseName("Stable Release main")
		assertNull(result)
	}

	@Test
	fun testExtractVersionFromReleaseName_WithVPrefix() {
		val result = extractVersionFromReleaseName("v1.2.7")
		assertEquals("1.2.7", result)
	}

	@Test
	fun testExtractVersionFromReleaseName_PlainVersion() {
		val result = extractVersionFromReleaseName("1.2.7")
		assertEquals("1.2.7", result)
	}

	@Test
	fun testExtractVersionFromReleaseName_VersionWithVariant() {
		val result = extractVersionFromReleaseName("Stable Release 2.0.1-beta1")
		assertEquals("2.0.1-beta1", result)
	}

	@Test
	fun testExtractVersionFromReleaseName_TwoPartVersion() {
		val result = extractVersionFromReleaseName("Stable Release 2.0")
		assertEquals("2.0", result)
	}

	@Test
	fun testExtractVersionFromReleaseName_Invalid() {
		assertNull(extractVersionFromReleaseName("Invalid"))
		assertNull(extractVersionFromReleaseName(""))
		assertNull(extractVersionFromReleaseName("Release abc"))
	}
}
