package com.phyll.filmhub

import com.phyll.filmhub.cloud.CloudSyncClient
import com.phyll.filmhub.data.ContentSafetyFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncCanonicalKeyTest {

    @Test
    fun normalizeMovieSlug_stripsProviderPrefixesConsistentlyWithWeb() {
        assertEquals("nu-hoang-nuoc-mat", CloudSyncClient.normalizeMovieSlug("modern_nu-hoang-nuoc-mat"))
        assertEquals("nu-hoang-nuoc-mat", CloudSyncClient.normalizeMovieSlug("nu-hoang-nuoc-mat"))
        assertEquals("gia-tai-cua-ngoai", CloudSyncClient.normalizeMovieSlug("KKPHIM_Gia-Tai-Cua-Ngoai"))
        assertEquals("unknown", CloudSyncClient.normalizeMovieSlug("   "))
    }

    @Test
    fun normalizeEpisodeKey_unifiesAndroidAndWebEpisodeFormats() {
        assertEquals("tap-1", CloudSyncClient.normalizeEpisodeKey("Tập 01"))
        assertEquals("tap-1", CloudSyncClient.normalizeEpisodeKey("tap-01"))
        assertEquals("tap-1", CloudSyncClient.normalizeEpisodeKey("Tập 1"))
        assertEquals("tap-12", CloudSyncClient.normalizeEpisodeKey("EP 12"))
        assertEquals("tap-7", CloudSyncClient.normalizeEpisodeKey("07"))
        assertEquals("full", CloudSyncClient.normalizeEpisodeKey("Full"))
        assertEquals("full", CloudSyncClient.normalizeEpisodeKey("Full HD"))
    }

    @Test
    fun contentSafetyFilter_allowsLegalT18CinemaAndBlocksPornOrVnBanned() {
        assertFalse(
            ContentSafetyFilter.isBlockedRaw(
                slug = "deadpool-va-wolverine",
                title = "Deadpool & Wolverine",
                categoryTags = listOf("hanh-dong", "hai-huoc")
            )
        )
        assertFalse(
            ContentSafetyFilter.isBlockedRaw(
                slug = "sex-education",
                title = "Sex Education",
                originalTitle = "Sex Education",
                categoryTags = listOf("hai-huoc", "tam-ly")
            )
        )
        assertTrue(
            ContentSafetyFilter.isBlockedRaw(
                slug = "sample-adult",
                title = "Adult Film",
                categoryTags = listOf("phim-18", "Phim 18+")
            )
        )
        assertTrue(
            ContentSafetyFilter.isBlockedRaw(
                slug = "ssis-999",
                title = "SSIS-999 Uncensored"
            )
        )
        assertTrue(
            ContentSafetyFilter.isBlockedRaw(
                slug = "uncharted",
                title = "Uncharted"
            )
        )
    }
}
