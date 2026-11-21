package com.phyll.filmhub

import com.phyll.filmhub.cloud.CloudSyncClient
import org.junit.Assert.assertEquals
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
}
