package com.phyll.filmhub.data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phyll.filmhub.cloud.CloudSession
import com.phyll.filmhub.cloud.CloudSyncClient
import com.phyll.filmhub.model.ControlTargetMode
import com.phyll.filmhub.model.DeviceUiMode
import com.phyll.filmhub.model.ManagedTvDevice
import com.phyll.filmhub.model.SeriesSubtitleProfile
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.TranslationEngineMode
import com.phyll.filmhub.model.VodProviderConfig
import com.phyll.filmhub.model.WatchProgress
import com.phyll.filmhub.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("phyll_film_hub_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * True when running on an actual Smart TV / Android TV Box (e.g. TV360, V88mini).
     * False when running on a handheld Android phone or tablet controller.
     */
    val isTvHardware: Boolean = isPhysicalTvHardware(appContext)

    init {
        // Migrate any legacy plain key cleanly into the persistent vault
        val legacyKey = prefs.getString(KEY_LEGACY_GEMINI_API, null)?.trim().orEmpty()
        if (legacyKey.isNotEmpty() && !prefs.contains(KEY_PHONE_PERSISTED_GEMINI_KEY)) {
            val encoded = Base64.encodeToString(legacyKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_PHONE_PERSISTED_GEMINI_KEY, encoded)
                .remove(KEY_LEGACY_GEMINI_API)
                .commit()
        } else if (prefs.contains(KEY_LEGACY_GEMINI_API)) {
            prefs.edit().remove(KEY_LEGACY_GEMINI_API).apply()
        }
    }

    private val _subtitleConfig = MutableStateFlow(loadSubtitleConfig())
    val subtitleConfig: StateFlow<SubtitleStyleConfig> = _subtitleConfig.asStateFlow()

    /**
     * Persisted locally inside the Android app's private SharedPreferences vault so the user
     * enters their Gemini API key ONCE and never has to re-enter it on Phone or TV!
     */
    private val _geminiApiKey = MutableStateFlow(loadPhonePersistedGeminiKey())
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _managedTvDevices = MutableStateFlow(loadManagedTvDevices())
    val managedTvDevices: StateFlow<List<ManagedTvDevice>> = _managedTvDevices.asStateFlow()

    private val _selectedTvIp = MutableStateFlow(loadSelectedTvIp())
    val selectedTvIp: StateFlow<String?> = _selectedTvIp.asStateFlow()

    private val _controlTargetMode = MutableStateFlow(loadControlTargetMode())
    val controlTargetMode: StateFlow<ControlTargetMode> = _controlTargetMode.asStateFlow()

    private val _promptQrOnStartup = MutableStateFlow(
        prefs.getBoolean(KEY_PROMPT_QR_ON_STARTUP, false)
    )
    val promptQrOnStartup: StateFlow<Boolean> = _promptQrOnStartup.asStateFlow()

    private val _discoveredGeminiModels = MutableStateFlow(PRESET_GEMINI_MODELS)
    val discoveredGeminiModels: StateFlow<List<String>> = _discoveredGeminiModels.asStateFlow()

    private val _deviceUiMode = MutableStateFlow(loadDeviceUiMode())
    val deviceUiMode: StateFlow<DeviceUiMode> = _deviceUiMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(loadAppLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _cloudSession = MutableStateFlow(loadCloudSession().also { CloudSyncClient.applySession(it) })
    val cloudSession: StateFlow<CloudSession?> = _cloudSession.asStateFlow()

    fun saveCloudSession(session: CloudSession?) {
        _cloudSession.value = session
        CloudSyncClient.applySession(session)
        val editor = prefs.edit()
        if (session == null) {
            editor.remove(KEY_CLOUD_SESSION_V1)
        } else {
            editor.putString(KEY_CLOUD_SESSION_V1, gson.toJson(session))
        }
        editor.commit()
    }

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        prefs.edit().putString(KEY_APP_UI_LANGUAGE, language.code).commit()
    }

    private val _watchHistory = MutableStateFlow(loadWatchHistory())
    val watchHistory: StateFlow<List<WatchProgress>> = _watchHistory.asStateFlow()

    private val _favoriteIds = MutableStateFlow(loadFavoriteIds())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _customProviders = MutableStateFlow(loadCustomProviders())
    val customProviders: StateFlow<List<VodProviderConfig>> = _customProviders.asStateFlow()

    private val _seriesProfiles = MutableStateFlow(loadSeriesProfiles())
    val seriesProfiles: StateFlow<Map<String, SeriesSubtitleProfile>> = _seriesProfiles.asStateFlow()

    private val episodeResumeCache: MutableMap<String, WatchProgress> = loadEpisodeResumeMap().toMutableMap()

    fun getEpisodeResumePositionMs(movieId: String, episodeId: String, streamUrl: String = "", episodeName: String = ""): Long {
        val targetSlug = CloudSyncClient.normalizeMovieSlug(movieId)
        val targetEpKey = CloudSyncClient.normalizeEpisodeKey(episodeName.ifBlank { episodeId })
        val byEp = episodeResumeCache["$movieId::$episodeId"]
            ?: episodeResumeCache["$targetSlug::$targetEpKey"]
            ?: episodeResumeCache.values.firstOrNull {
                (it.movieId == movieId || CloudSyncClient.normalizeMovieSlug(it.movieId) == targetSlug) &&
                    (it.episodeId == episodeId ||
                        CloudSyncClient.normalizeEpisodeKey(it.episodeName.ifBlank { it.episodeId }) == targetEpKey ||
                        (streamUrl.isNotBlank() && it.streamUrl == streamUrl))
            }
            ?: _watchHistory.value.firstOrNull {
                (it.movieId == movieId || CloudSyncClient.normalizeMovieSlug(it.movieId) == targetSlug) &&
                    (it.episodeId == episodeId ||
                        CloudSyncClient.normalizeEpisodeKey(it.episodeName.ifBlank { it.episodeId }) == targetEpKey ||
                        (streamUrl.isNotBlank() && it.streamUrl == streamUrl))
            }
            ?: return 0L
        if (byEp.positionMs < 3_000L) return 0L
        if (byEp.durationMs > 30_000L && byEp.positionMs >= (byEp.durationMs * 0.96).toLong()) {
            return 0L // Finished watching (>96%), restart cleanly from beginning
        }
        return (byEp.positionMs - 2_000L).coerceAtLeast(0L)
    }

    fun getLatestMovieWatchProgress(movieId: String): WatchProgress? {
        val targetSlug = CloudSyncClient.normalizeMovieSlug(movieId)
        val fromHistory = _watchHistory.value.firstOrNull {
            it.movieId == movieId || CloudSyncClient.normalizeMovieSlug(it.movieId) == targetSlug
        }
        val fromMap = episodeResumeCache.values
            .filter { it.movieId == movieId || CloudSyncClient.normalizeMovieSlug(it.movieId) == targetSlug }
            .maxByOrNull { it.updatedAt }
        val best = when {
            fromHistory != null && fromMap != null -> if (fromMap.updatedAt >= fromHistory.updatedAt) fromMap else fromHistory
            else -> fromMap ?: fromHistory
        } ?: return null
        if (best.positionMs < 3_000L) return null
        if (best.durationMs > 30_000L && best.positionMs >= (best.durationMs * 0.96).toLong()) return null
        return best
    }

    /**
     * Merges remote watch progress rows pulled from Neon Postgres using Last-Write-Wins on updatedAt.
     */
    fun mergeCloudWatchProgress(remoteItems: List<WatchProgress>): Boolean {
        if (remoteItems.isEmpty()) return false
        var changed = false
        val historyBySlug = LinkedHashMap<String, WatchProgress>()
        for (h in _watchHistory.value) {
            historyBySlug[CloudSyncClient.normalizeMovieSlug(h.movieId)] = h
        }

        for (remote in remoteItems) {
            val slug = CloudSyncClient.normalizeMovieSlug(remote.movieId)
            val epKey = CloudSyncClient.normalizeEpisodeKey(remote.episodeName.ifBlank { remote.episodeId })
            val canonicalMapKey = "$slug::$epKey"

            val existingEp = episodeResumeCache[canonicalMapKey]
                ?: episodeResumeCache.values.firstOrNull {
                    CloudSyncClient.normalizeMovieSlug(it.movieId) == slug &&
                        CloudSyncClient.normalizeEpisodeKey(it.episodeName.ifBlank { it.episodeId }) == epKey
                }
            if (existingEp == null || remote.updatedAt > existingEp.updatedAt) {
                val mergedEp = if (existingEp != null) {
                    remote.copy(
                        movieId = existingEp.movieId,
                        posterUrl = remote.posterUrl.ifBlank { existingEp.posterUrl },
                        streamUrl = remote.streamUrl.ifBlank { existingEp.streamUrl }
                    )
                } else {
                    remote
                }
                episodeResumeCache[canonicalMapKey] = mergedEp
                episodeResumeCache["${mergedEp.movieId}::${mergedEp.episodeId}"] = mergedEp
                changed = true
            }

            val existingHist = historyBySlug[slug]
            if (existingHist == null || remote.updatedAt > existingHist.updatedAt) {
                val mergedHist = if (existingHist != null) {
                    remote.copy(
                        movieId = existingHist.movieId,
                        posterUrl = remote.posterUrl.ifBlank { existingHist.posterUrl },
                        streamUrl = remote.streamUrl.ifBlank { existingHist.streamUrl }
                    )
                } else {
                    remote
                }
                historyBySlug[slug] = mergedHist
                changed = true
            }
        }

        if (changed) {
            val mergedHistory = historyBySlug.values
                .sortedByDescending { it.updatedAt }
                .take(40)
            _watchHistory.value = mergedHistory
            prefs.edit()
                .putString(KEY_WATCH_HISTORY, gson.toJson(mergedHistory))
                .putString(KEY_EPISODE_RESUME_MAP, gson.toJson(episodeResumeCache.values.distinctBy {
                    "${CloudSyncClient.normalizeMovieSlug(it.movieId)}::${CloudSyncClient.normalizeEpisodeKey(it.episodeName.ifBlank { it.episodeId })}"
                }.take(200)))
                .apply()
        }
        return changed
    }

    fun getSeriesSubtitleProfile(seriesId: String): SeriesSubtitleProfile? {
        if (seriesId.isBlank()) return null
        return _seriesProfiles.value[seriesId]
    }

    fun saveSeriesSubtitleProfile(
        seriesId: String,
        transform: (SeriesSubtitleProfile) -> SeriesSubtitleProfile
    ) {
        if (seriesId.isBlank()) return
        val existing = _seriesProfiles.value[seriesId] ?: SeriesSubtitleProfile(seriesId = seriesId)
        val updated = transform(existing).copy(
            seriesId = seriesId,
            updatedAtMs = System.currentTimeMillis()
        )
        val mutable = _seriesProfiles.value.toMutableMap()
        mutable[seriesId] = updated
        // Keep most recent 80 series profiles
        val trimmed = mutable.values
            .sortedByDescending { it.updatedAtMs }
            .take(80)
            .associateBy { it.seriesId }
        _seriesProfiles.value = trimmed
        prefs.edit().putString(KEY_SERIES_SYNC_PROFILES, gson.toJson(trimmed.values.toList())).apply()
    }

    fun clearSeriesSubtitleProfile(seriesId: String) {
        if (seriesId.isBlank()) return
        val mutable = _seriesProfiles.value.toMutableMap()
        if (mutable.remove(seriesId) != null) {
            _seriesProfiles.value = mutable
            prefs.edit().putString(KEY_SERIES_SYNC_PROFILES, gson.toJson(mutable.values.toList())).apply()
        }
    }

    fun updateSubtitleConfig(transform: (SubtitleStyleConfig) -> SubtitleStyleConfig) {
        val updated = transform(_subtitleConfig.value)
        _subtitleConfig.value = updated
        prefs.edit().putString(KEY_SUBTITLE_CONFIG, gson.toJson(updated)).apply()
    }

    fun resetSubtitleConfigToThaiDefault() {
        val currentModel = _subtitleConfig.value.geminiModelId
        val defaultCfg = SubtitleStyleConfig(
            geminiModelId = currentModel,
            translationEngine = if (_geminiApiKey.value.isNotBlank()) {
                TranslationEngineMode.GEMINI_AI
            } else {
                TranslationEngineMode.FAST_FREE
            }
        )
        _subtitleConfig.value = defaultCfg
        prefs.edit().putString(KEY_SUBTITLE_CONFIG, gson.toJson(defaultCfg)).apply()
    }

    /**
     * Stores Gemini API credentials persistently inside the Android app vault.
     * IMPORTANT: If [key] is blank, preserves the already-saved API key untouched and only updates [modelId]!
     */
    fun setSessionGeminiCredentials(key: String, modelId: String? = null) {
        val trimmedKey = key.trim()
        if (trimmedKey.isNotEmpty()) {
            _geminiApiKey.value = trimmedKey
            val encoded = Base64.encodeToString(trimmedKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit().putString(KEY_PHONE_PERSISTED_GEMINI_KEY, encoded).commit()
        }

        val cleanModel = modelId?.trim()?.removePrefix("models/")?.takeIf { it.isNotEmpty() }
        if (cleanModel != null) {
            registerDiscoveredModels(listOf(cleanModel))
        }

        val hasAnyKey = _geminiApiKey.value.isNotBlank()
        if (trimmedKey.isNotEmpty() || (hasAnyKey && cleanModel != null)) {
            updateSubtitleConfig { cfg ->
                cfg.copy(
                    translationEngine = if (hasAnyKey) TranslationEngineMode.GEMINI_AI else cfg.translationEngine,
                    geminiModelId = cleanModel ?: cfg.geminiModelId.ifBlank { DEFAULT_GEMINI_MODEL }
                )
            }
        } else if (cleanModel != null) {
            updateSubtitleConfig { cfg -> cfg.copy(geminiModelId = cleanModel) }
        }
    }

    /**
     * Explicitly erases the API key when the user clicks "Xóa Key".
     */
    fun wipeSessionGeminiKeyFromRam(clearPhoneVault: Boolean = true) {
        _geminiApiKey.value = ""
        prefs.edit()
            .remove(KEY_LEGACY_GEMINI_API)
            .apply {
                if (clearPhoneVault) {
                    remove(KEY_PHONE_PERSISTED_GEMINI_KEY)
                }
            }
            .commit()
    }

    fun upsertManagedTvDevice(device: ManagedTvDevice) {
        val cleanIp = device.ip.trim()
        if (cleanIp.isEmpty()) return
        val current = _managedTvDevices.value.toMutableList()
        val idx = current.indexOfFirst { it.ip == cleanIp }
        if (idx >= 0) {
            val prev = current[idx]
            current[idx] = device.copy(
                name = device.name.ifBlank { prev.name },
                model = device.model.ifBlank { prev.model },
                appVersion = device.appVersion.ifBlank { prev.appVersion }
            )
        } else {
            current.add(device)
        }
        _managedTvDevices.value = current
        prefs.edit().putString(KEY_MANAGED_TV_DEVICES, gson.toJson(current)).apply()
    }

    fun mergeDiscoveredTvDevices(discovered: List<ManagedTvDevice>) {
        val merged = _managedTvDevices.value.associateBy { it.ip }.toMutableMap()
        // Mark previously saved ones offline until verified by scan results
        for ((ip, prev) in merged) {
            if (discovered.none { it.ip == ip }) {
                merged[ip] = prev.copy(adbOnline = false, companionOnline = false)
            }
        }
        for (d in discovered) {
            val prev = merged[d.ip]
            merged[d.ip] = if (prev != null) {
                d.copy(
                    // Preserve custom user nickname if user renamed it
                    name = if (prev.name.isNotBlank() && !prev.name.startsWith("Android TV")) prev.name else d.name
                )
            } else {
                d
            }
        }
        val list = merged.values.sortedByDescending { it.companionOnline || it.adbOnline }
        _managedTvDevices.value = list
        prefs.edit().putString(KEY_MANAGED_TV_DEVICES, gson.toJson(list)).apply()
    }

    fun renameManagedTvDevice(ip: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val updated = _managedTvDevices.value.map {
            if (it.ip == ip) it.copy(name = clean) else it
        }
        _managedTvDevices.value = updated
        prefs.edit().putString(KEY_MANAGED_TV_DEVICES, gson.toJson(updated)).apply()
    }

    fun removeManagedTvDevice(ip: String) {
        val updated = _managedTvDevices.value.filterNot { it.ip == ip }
        _managedTvDevices.value = updated
        prefs.edit().putString(KEY_MANAGED_TV_DEVICES, gson.toJson(updated)).apply()
        if (_selectedTvIp.value == ip) {
            selectManagedTvIp(updated.firstOrNull()?.ip)
        }
    }

    fun selectManagedTvIp(ip: String?) {
        val clean = ip?.trim()?.takeIf { it.isNotEmpty() }
        _selectedTvIp.value = clean
        prefs.edit().apply {
            if (clean == null) remove(KEY_SELECTED_TV_IP) else putString(KEY_SELECTED_TV_IP, clean)
        }.apply()
    }

    fun setControlTargetMode(mode: ControlTargetMode) {
        _controlTargetMode.value = mode
        prefs.edit().putString(KEY_CONTROL_TARGET_MODE, mode.name).apply()
    }

    fun setSelectedGeminiModel(modelId: String) {
        val clean = modelId.trim().removePrefix("models/")
        if (clean.isEmpty()) return
        registerDiscoveredModels(listOf(clean))
        updateSubtitleConfig { it.copy(geminiModelId = clean) }
    }

    fun registerDiscoveredModels(models: List<String>) {
        if (models.isEmpty()) return
        val merged = LinkedHashSet<String>()
        models.map { it.trim().removePrefix("models/") }
            .filter { it.isNotEmpty() }
            .forEach { merged.add(it) }
        PRESET_GEMINI_MODELS.forEach { merged.add(it) }
        _discoveredGeminiModels.value = merged.toList()
    }

    fun setPromptQrOnStartup(enabled: Boolean) {
        _promptQrOnStartup.value = enabled
        prefs.edit().putBoolean(KEY_PROMPT_QR_ON_STARTUP, enabled).apply()
    }

    fun setDeviceUiMode(mode: DeviceUiMode) {
        _deviceUiMode.value = mode
        prefs.edit().putString(KEY_UI_MODE_V25, mode.name).apply()
    }

    fun saveWatchProgress(progress: WatchProgress, immediateCommit: Boolean = false) {
        val current = _watchHistory.value.toMutableList()
        current.removeAll { it.movieId == progress.movieId }
        current.add(0, progress)
        val trimmed = current.take(40)
        _watchHistory.value = trimmed

        // Also persist exact per-episode resume position so re-entering ANY movie or episode resumes seamlessly!
        val epKey = "${progress.movieId}::${progress.episodeId}"
        episodeResumeCache[epKey] = progress
        if (episodeResumeCache.size > 200) {
            val keep = episodeResumeCache.entries
                .sortedByDescending { it.value.updatedAt }
                .take(180)
                .associate { it.key to it.value }
            episodeResumeCache.clear()
            episodeResumeCache.putAll(keep)
        }

        val editor = prefs.edit()
            .putString(KEY_WATCH_HISTORY, gson.toJson(trimmed))
            .putString(KEY_EPISODE_RESUME_MAP, gson.toJson(episodeResumeCache.values.toList()))
        if (immediateCommit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun clearWatchHistory() {
        _watchHistory.value = emptyList()
        episodeResumeCache.clear()
        prefs.edit()
            .remove(KEY_WATCH_HISTORY)
            .remove(KEY_EPISODE_RESUME_MAP)
            .apply()
    }

    fun toggleFavorite(movieId: String) {
        val updated = _favoriteIds.value.toMutableSet()
        if (!updated.add(movieId)) {
            updated.remove(movieId)
        }
        _favoriteIds.value = updated
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    fun addCustomProvider(provider: VodProviderConfig) {
        val current = _customProviders.value.toMutableList()
        current.removeAll { it.id == provider.id || it.baseUrl.equals(provider.baseUrl, ignoreCase = true) }
        current.add(0, provider)
        _customProviders.value = current
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS, gson.toJson(current)).apply()
    }

    fun removeCustomProvider(providerId: String) {
        val updated = _customProviders.value.filterNot { it.id == providerId }
        _customProviders.value = updated
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS, gson.toJson(updated)).apply()
    }

    fun toggleCustomProvider(providerId: String) {
        val updated = _customProviders.value.map {
            if (it.id == providerId) it.copy(enabled = !it.enabled) else it
        }
        _customProviders.value = updated
        prefs.edit().putString(KEY_CUSTOM_PROVIDERS, gson.toJson(updated)).apply()
    }

    private fun loadSubtitleConfig(): SubtitleStyleConfig {
        val json = prefs.getString(KEY_SUBTITLE_CONFIG, null)
        if (json == null) {
            prefs.edit().putBoolean(KEY_SUBTITLE_V26_MIGRATED, true).apply()
            return SubtitleStyleConfig()
        }
        val parsed = runCatching { gson.fromJson(json, SubtitleStyleConfig::class.java) }
            .getOrNull() ?: return SubtitleStyleConfig()
        // Safely migrate newly added v2.2..v2.6 fields when reading older JSON from SharedPreferences
        val safeModel = runCatching { parsed.geminiModelId }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        val safeAutoAudio = if (json.contains("autoGenerateAudioSubWhenMissing")) {
            parsed.autoGenerateAudioSubWhenMissing
        } else {
            true
        }
        val safeAutoSync = if (json.contains("autoSyncDownloadedSub")) {
            parsed.autoSyncDownloadedSub
        } else {
            true
        }
        val safeSpeedScale = if (parsed.subSpeedScale in 0.80f..1.25f) {
            parsed.subSpeedScale
        } else {
            1.0f
        }
        val safePrimaryColor = runCatching { parsed.primaryColor }.getOrNull() ?: SubtitleColorChoice.CINEMA_YELLOW
        val safeSecondaryColor = if (json.contains("secondaryColor")) {
            runCatching { parsed.secondaryColor }.getOrNull() ?: SubtitleColorChoice.WHITE
        } else {
            SubtitleColorChoice.WHITE
        }
        val safeSecondarySize = if (json.contains("secondaryFontSizeSp") && parsed.secondaryFontSizeSp in 10f..48f) {
            parsed.secondaryFontSizeSp
        } else {
            (parsed.fontSizeSp * 0.80f).coerceIn(12f, 36f)
        }
        val safeLinkMode = if (json.contains("linkPrimarySecondaryStyle")) {
            parsed.linkPrimarySecondaryStyle
        } else {
            false
        }
        val safeSecondaryLangCode = if (json.contains("secondaryLangCode")) {
            runCatching { parsed.secondaryLangCode }.getOrNull().orEmpty()
        } else {
            ""
        }
        val safePrimaryLang = runCatching { parsed.preferredPrimaryLang }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "vi"

        // One-time v2.6 migration: disable the old oversized 86dp hardsub slab & shrink huge default box
        val migratedV26 = prefs.getBoolean(KEY_SUBTITLE_V26_MIGRATED, false)
        val finalConfig = if (!migratedV26) {
            val compactPrimarySize = if (parsed.fontSizeSp >= 25f) 20f else parsed.fontSizeSp.coerceIn(12f, 48f)
            val compactSecondarySize = (compactPrimarySize * 0.80f).coerceIn(12f, 36f)
            val migrated = parsed.copy(
                preferredPrimaryLang = safePrimaryLang,
                secondaryLangCode = safeSecondaryLangCode,
                geminiModelId = safeModel,
                autoGenerateAudioSubWhenMissing = safeAutoAudio,
                autoSyncDownloadedSub = safeAutoSync,
                subSpeedScale = safeSpeedScale,
                fontSizeSp = compactPrimarySize,
                secondaryFontSizeSp = compactSecondarySize,
                primaryColor = safePrimaryColor,
                secondaryColor = safeSecondaryColor,
                linkPrimarySecondaryStyle = safeLinkMode,
                edgeStyle = SubtitleEdgeStyle.BACKGROUND_BOX,
                boxOpacity = 0.78f,
                bottomOffsetDp = 18f,
                hardsubMaskEnabled = false,
                hardsubMaskHeightDp = 52f
            )
            prefs.edit()
                .putBoolean(KEY_SUBTITLE_V26_MIGRATED, true)
                .putString(KEY_SUBTITLE_CONFIG, gson.toJson(migrated))
                .apply()
            migrated
        } else {
            parsed.copy(
                preferredPrimaryLang = safePrimaryLang,
                secondaryLangCode = safeSecondaryLangCode,
                geminiModelId = safeModel,
                autoGenerateAudioSubWhenMissing = safeAutoAudio,
                autoSyncDownloadedSub = safeAutoSync,
                subSpeedScale = safeSpeedScale,
                primaryColor = safePrimaryColor,
                secondaryColor = safeSecondaryColor,
                secondaryFontSizeSp = safeSecondarySize,
                linkPrimarySecondaryStyle = safeLinkMode
            )
        }
        return finalConfig
    }

    private fun loadDeviceUiMode(): DeviceUiMode {
        val raw = prefs.getString(KEY_UI_MODE_V25, DeviceUiMode.MOBILE.name) ?: DeviceUiMode.MOBILE.name
        return runCatching { DeviceUiMode.valueOf(raw) }.getOrDefault(DeviceUiMode.MOBILE)
    }

    private fun loadWatchHistory(): List<WatchProgress> {
        val json = prefs.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<WatchProgress>>() {}.type
        return runCatching { gson.fromJson<List<WatchProgress>>(json, type) }
            .getOrNull() ?: emptyList()
    }

    private fun loadEpisodeResumeMap(): Map<String, WatchProgress> {
        val json = prefs.getString(KEY_EPISODE_RESUME_MAP, null) ?: return emptyMap()
        val type = object : TypeToken<List<WatchProgress>>() {}.type
        val list = runCatching { gson.fromJson<List<WatchProgress>>(json, type) }
            .getOrNull() ?: return emptyMap()
        return list.associateBy { "${it.movieId}::${it.episodeId}" }
    }

    private fun loadFavoriteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
    }

    private fun loadCustomProviders(): List<VodProviderConfig> {
        val json = prefs.getString(KEY_CUSTOM_PROVIDERS, null) ?: return emptyList()
        val type = object : TypeToken<List<VodProviderConfig>>() {}.type
        return runCatching { gson.fromJson<List<VodProviderConfig>>(json, type) }
            .getOrNull() ?: emptyList()
    }

    private fun loadSeriesProfiles(): Map<String, SeriesSubtitleProfile> {
        val json = prefs.getString(KEY_SERIES_SYNC_PROFILES, null) ?: return emptyMap()
        val type = object : TypeToken<List<SeriesSubtitleProfile>>() {}.type
        val list = runCatching { gson.fromJson<List<SeriesSubtitleProfile>>(json, type) }
            .getOrNull() ?: return emptyMap()
        return list.filter { it.seriesId.isNotBlank() }.associateBy { it.seriesId }
    }

    private fun loadPhonePersistedGeminiKey(): String {
        val raw = prefs.getString(KEY_PHONE_PERSISTED_GEMINI_KEY, null) ?: return ""
        return runCatching {
            String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8).trim()
        }.getOrDefault("")
    }

    private fun loadManagedTvDevices(): List<ManagedTvDevice> {
        val json = prefs.getString(KEY_MANAGED_TV_DEVICES, null) ?: return emptyList()
        val type = object : TypeToken<List<ManagedTvDevice>>() {}.type
        return runCatching { gson.fromJson<List<ManagedTvDevice>>(json, type) }
            .getOrNull() ?: emptyList()
    }

    private fun loadSelectedTvIp(): String? {
        return prefs.getString(KEY_SELECTED_TV_IP, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun loadAppLanguage(): AppLanguage {
        return AppLanguage.fromCode(prefs.getString(KEY_APP_UI_LANGUAGE, AppLanguage.EN.code))
    }

    private fun loadCloudSession(): CloudSession? {
        val raw = prefs.getString(KEY_CLOUD_SESSION_V1, null) ?: return null
        return runCatching { gson.fromJson(raw, CloudSession::class.java) }
            .getOrNull()
            ?.takeIf { it.token.isNotBlank() }
    }

    private fun loadControlTargetMode(): ControlTargetMode {
        // Standalone cinema-first default: play directly on the user's phone/tablet unless they explicitly cast to TV!
        return ControlTargetMode.LOCAL_DEVICE
    }

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3-flash-preview"
        val PRESET_GEMINI_MODELS = listOf(
            "gemini-3-flash-preview",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite-preview",
            "gemini-2.5-pro",
            "gemini-2.5-flash"
        )

        fun isPhysicalTvHardware(context: Context): Boolean {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val isTvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
            val pm = context.packageManager
            val hasTelevisionFeature = pm.hasSystemFeature("android.hardware.type.television")
            val hasLeanback = pm.hasSystemFeature("android.software.leanback")
            val hasTouchscreen = pm.hasSystemFeature("android.hardware.touchscreen")
            return isTvUiMode || hasTelevisionFeature || hasLeanback || !hasTouchscreen
        }

        private const val KEY_SUBTITLE_CONFIG = "subtitle_style_config_v2"
        private const val KEY_SUBTITLE_V26_MIGRATED = "subtitle_style_v26_migrated"
        private const val KEY_LEGACY_GEMINI_API = "gemini_api_key"
        private const val KEY_PHONE_PERSISTED_GEMINI_KEY = "phone_controller_gemini_key_b64_v1"
        private const val KEY_MANAGED_TV_DEVICES = "managed_tv_devices_v1"
        private const val KEY_SELECTED_TV_IP = "selected_tv_ip_v1"
        private const val KEY_CONTROL_TARGET_MODE = "control_target_mode_v1"
        private const val KEY_PROMPT_QR_ON_STARTUP = "prompt_qr_on_startup_v1"
        private const val KEY_UI_MODE_V25 = "device_ui_mode_v25"
        private const val KEY_APP_UI_LANGUAGE = "app_ui_language_v1"
        private const val KEY_CLOUD_SESSION_V1 = "nki_cloud_session_v1"
        private const val KEY_WATCH_HISTORY = "watch_history_v1"
        private const val KEY_EPISODE_RESUME_MAP = "episode_resume_map_v1"
        private const val KEY_FAVORITES = "favorite_movie_ids"
        private const val KEY_CUSTOM_PROVIDERS = "custom_vod_providers_v1"
        private const val KEY_SERIES_SYNC_PROFILES = "series_subtitle_profiles_v1"
    }
}
