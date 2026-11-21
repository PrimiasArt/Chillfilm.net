import {
  SUBTITLE_LANGUAGES,
  resolveLanguage,
  parseSubtitleText,
  findActiveCueText,
  fetchOpenSubtitlesV3,
  translateCueBatch,
  buildSubCacheKey,
  loadCachedEpisodeSub,
  saveCachedEpisodeSub,
} from './subtitles.js?v=3';
import { UI_LANGUAGES, resolveUiLang, t } from './i18n.js?v=3';
import {
  normalizeMovieSlug,
  normalizeEpisodeKey,
  getCloudSession,
  clearCloudSession,
  isCloudSignedIn,
  fetchCloudHealth,
  cloudLogin,
  cloudRegister,
  cloudRefreshMe,
  cloudFetchAdminConfig,
  cloudAdminAction,
  cloudPullState,
  cloudPushState,
  cloudFetchSubtitle,
  cloudSaveSubtitle,
  cloudTranslateCueBatch,
  cloudFetchLiveModels,
  fetchLiveCatalogFeed,
  fetchCatalogBrowse,
  fetchCatalogDetail,
} from './sync.js?v=6';

const PHIM_API = 'https://phimapi.com';
const PHIM_CDN = 'https://phimimg.com';
const APK_RELEASE_URL = 'https://github.com/PrimiasArt/Chillfilm.net/releases/download/android-apk-latest/ChillFilm-latest.apk';

const CATEGORIES = [
  { id: 'hot', label: 'Trending', url: `${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=1` },
  { id: 'crawled', label: '🤖 Auto-Crawled', browse: { category: 'crawled' } },
  { id: 'phim-le', label: 'Movies', browse: { category: 'phim-le' }, url: `${PHIM_API}/v1/api/danh-sach/phim-le?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'phim-bo', label: 'TV Series', browse: { category: 'phim-bo' }, url: `${PHIM_API}/v1/api/danh-sach/phim-bo?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'hoat-hinh', label: 'Animation', browse: { category: 'hoat-hinh' }, url: `${PHIM_API}/v1/api/danh-sach/hoat-hinh?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'han-quoc', label: 'Korean', browse: { country: 'han-quoc' }, url: `${PHIM_API}/v1/api/quoc-gia/han-quoc?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'au-my', label: 'US / UK', browse: { country: 'au-my' }, url: `${PHIM_API}/v1/api/quoc-gia/au-my?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'trung-quoc', label: 'Chinese', browse: { country: 'trung-quoc' }, url: `${PHIM_API}/v1/api/quoc-gia/trung-quoc?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'thai-lan', label: 'Thai', browse: { country: 'thai-lan' }, url: `${PHIM_API}/v1/api/quoc-gia/thai-lan?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'nhat-ban', label: 'Japanese', browse: { country: 'nhat-ban' }, url: `${PHIM_API}/v1/api/quoc-gia/nhat-ban?page=1&sort_field=modified.time&sort_type=desc&limit=24` },
  { id: 'archive', label: '🏛 World Archive', browse: { category: 'archive' } },
  { id: 'iptv', label: '📺 Live Cinema', browse: { category: 'iptv' } },
];

const DEFAULT_PREFS = {
  uiLang: 'en',
  primaryLang: 'en',
  secondaryLang: 'vi',
  dualSub: false,
  subEnabled: true,
  primarySize: 22,
  secondarySize: 16,
  primaryColor: '#FFD600',
  secondaryColor: '#FFFFFF',
  boxOpacity: 0.78,
  syncOffsetMs: 0,
  geminiApiKey: '',
  geminiModel: 'gemini-3-flash-preview',
  tvIp: '',
};

const state = {
  prefs: loadPrefs(),
  activeCategory: 'hot',
  movies: [],
  homeRows: [],
  currentMovie: null,
  currentEpisode: null,
  episodesFlat: [],
  hls: null,
  audioCtx: null,
  gainNode: null,
  eqNodes: null,
  volumeScale: 1.0,
  eqPreset: 'FLAT',
  aspectMode: 'fit',
  brightness: 1.0,
  speed: 1.0,
  sleepTimerId: null,
  sleepMinutes: null,
  isLocked: false,
  isMinimized: false,
  minimizedAt: 0,
  controlsVisible: true,
  controlsTimer: null,
  // Subtitles & Resume
  subTracks: [],
  activeSubTrackId: null,
  sourceCues: [],
  primaryCues: [],
  secondaryCues: [],
  lastSubSourceLang: 'auto',
  lastSubTrackLabel: '',
  subTranslationRunId: 0,
  subStatusText: 'AI Sub ready',
  pendingSeekSec: 0,
  lastSavedPosTs: 0,
  // NKI Cloud Auto-Sync
  cloudSession: getCloudSession(),
  cloudHealth: null,
  cloudAuthMode: 'login',
  pendingCloudProgress: new Map(),
  lastCloudPullTs: 0,
  lastCloudPushTs: 0,
  prefsPushTimer: null,
  lastCloudSubPushBatch: 0,
  // Live Hero Carousel & Live Gemini Model Catalog
  heroMovies: [],
  heroIndex: 0,
  heroTimer: null,
  heroPaused: false,
  catalogUpdatedAt: 0,
  liveModels: loadCachedLiveModels(),
  modelsLoading: false,
  adminGlobalModel: '',
  globalCountries: [],
  crawlerStatus: null,
};

function loadCachedLiveModels() {
  try {
    const arr = JSON.parse(localStorage.getItem('chillfilm_nki_live_models_v1') || '[]');
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

function saveCachedLiveModels(models) {
  try {
    localStorage.setItem('chillfilm_nki_live_models_v1', JSON.stringify(models || []));
  } catch {
    // ignore
  }
}

const $ = (id) => document.getElementById(id);
const tr = (key, vars) => t(state.prefs.uiLang, key, vars);

function getCategoryLabel(catId) {
  const key = `cat_${catId}`;
  const translated = tr(key);
  if (translated && translated !== key) return translated;
  const found = CATEGORIES.find((c) => c.id === catId);
  if (found) return found.label;
  const cFound = state.globalCountries.find((c) => c.slug === catId);
  return cFound ? `${cFound.flag || '🌍'} ${cFound.name}` : catId;
}

function renderCountryPicker(countries) {
  if (Array.isArray(countries) && countries.length) {
    state.globalCountries = countries;
  }
  const sel = $('countryPickerSelect');
  if (!sel || !state.globalCountries.length) return;
  const curVal = sel.value || '';
  sel.innerHTML = `<option value="">🌍 All Countries (${state.globalCountries.length})</option>` +
    state.globalCountries
      .map((c) => `<option value="${escapeHtml(c.slug)}" ${c.slug === curVal ? 'selected' : ''}>${escapeHtml(c.flag || '🌍')} ${escapeHtml(c.name)}</option>`)
      .join('');
}

function renderLangMenu() {
  const cur = resolveUiLang(state.prefs.uiLang);
  const curObj = UI_LANGUAGES.find((l) => l.code === cur) || UI_LANGUAGES[0];
  if ($('langBadgeText')) $('langBadgeText').textContent = curObj.badge;
  const menu = $('langMenu');
  if (!menu) return;
  menu.innerHTML = '';
  UI_LANGUAGES.forEach((l) => {
    const btn = document.createElement('button');
    btn.className = `lang-opt ${l.code === cur ? 'active' : ''}`;
    btn.innerHTML = `<span>${l.name}</span><span style="opacity:.85;font-size:11px;">${l.badge}</span>`;
    btn.onclick = (e) => {
      e.stopPropagation();
      setUiLanguage(l.code);
      menu.classList.remove('open');
      $('langSwitchBtn')?.classList.remove('open');
    };
    menu.appendChild(btn);
  });
}

function setUiLanguage(code) {
  state.prefs.uiLang = resolveUiLang(code);
  savePrefs();
  applyUiTranslations();
  renderCategories();
  if (state.heroMovies.length) renderHero(state.heroMovies[state.heroIndex] || state.heroMovies[0]);
  else if (state.movies.length) renderHero(state.movies[0]);
  if (state.activeCategory === 'hot' && state.homeRows.length) {
    renderHomeSwimlanes();
  }
  if ($('detailView').classList.contains('active') && state.currentMovie) {
    renderDetailView(state.currentMovie);
  }
  const cat = CATEGORIES.find((c) => c.id === state.activeCategory);
  if (cat && !$('searchInputDesktop').value.trim()) {
    $('catalogTitle').textContent = getCategoryLabel(cat.id);
  }
}

function applyUiTranslations() {
  document.documentElement.lang = resolveUiLang(state.prefs.uiLang);
  renderLangMenu();
  const setText = (id, key, vars) => { const el = $(id); if (el) el.textContent = tr(key, vars); };
  const setHtml = (id, key, vars) => { const el = $(id); if (el) el.innerHTML = tr(key, vars); };

  if ($('searchInputDesktop')) $('searchInputDesktop').placeholder = tr('searchPlaceholder');
  if ($('searchInputMobile')) $('searchInputMobile').placeholder = tr('searchPlaceholderMobile');
  setText('searchBtnDesktop', 'searchBtn');
  setText('searchBtnMobile', 'searchBtn');
  setText('continueSectionTitle', 'continueWatching');
  setHtml('footerTagline', 'footerTagline');
  setText('footerApkLink', 'downloadApk');
  setText('hudLockBtn', 'lockBtn');
  const isFs = Boolean(document.fullscreenElement || document.webkitFullscreenElement);
  setText('hudFsBtn', isFs ? 'exitFsBtn' : 'fullscreenBtn');
  if ($('miniExpandBtn')) $('miniExpandBtn').title = tr('miniExpandTitle');
  if ($('miniCloseBtn')) $('miniCloseBtn').title = tr('miniCloseTitle');

  // Subtitle modal
  setText('subModalTitle', 'subModalTitle');
  setText('subEnableLabel', 'enableSub');
  setText('dualSubLabel', 'dualSub');
  setText('primaryLangLabel', 'primaryLangLabel');
  if ($('customLangInput')) $('customLangInput').placeholder = tr('customLangPlaceholder');
  setText('applyCustomLangBtn', 'applyBtn');
  setText('secondaryLangLabel', 'secondaryLangLabel');
  setText('onlineTrackLabel', 'onlineTrackLabel');
  setText('uploadSubLabel', 'uploadSubBtn');
  setText('syncLabel', 'syncLabel');
  setText('syncResetBtn', 'syncReset');
  setText('primarySizeLabelText', 'primarySizeLabel');
  setText('secondarySizeLabelText', 'secondarySizeLabel');

  // More modal
  setText('moreModalTitle', 'moreModalTitle');
  setText('boostLabelText', 'boostLabel');
  setText('eqLabelText', 'eqLabel');
  setText('eqFlatBtn', 'eqFlat');
  setText('eqBassBtn', 'eqBass');
  setText('eqVocalBtn', 'eqVocal');
  setText('eqMovieBtn', 'eqMovie');
  setText('speedLabelText', 'speedLabel');
  setText('aspectLabelText', 'aspectLabel');
  setText('aspectFitBtn', 'aspectFit');
  setText('aspectFillBtn', 'aspectFill');
  setText('aspectStretchBtn', 'aspectStretch');
  setText('sleepLabelText', 'sleepLabel');
  setText('sleepOffBtn', 'sleepOff');
  document.querySelectorAll('[data-sleep]').forEach((b) => {
    const mins = Number(b.dataset.sleep);
    if (mins > 0) b.textContent = tr('sleepMin', { m: mins });
  });
  setText('pipBtn', 'pipBtn');

  // Settings & TV Cast modals
  setText('settingsTitle', 'settingsTitle');
  setHtml('settingsPrivacyBox', 'settingsPrivacy');
  setText('uiLangLabelText', 'uiLangLabel');
  setText('defaultSubLangLabelText', 'defaultSubLangLabel');
  setText('geminiKeyLabelText', 'geminiKeyLabel');
  setText('geminiModelLabelText', 'geminiModelLabel');
  setText('saveSettingsBtn', 'saveSettingsBtn');
  setText('directStreamLabelText', 'directStreamLabel');
  if ($('directUrlInput')) $('directUrlInput').placeholder = tr('directUrlPlaceholder');
  if ($('directTitleInput')) $('directTitleInput').placeholder = tr('directTitlePlaceholder');
  setText('playDirectBtn', 'playLinkBtn');
  setText('openVideoLabelText', 'openVideoBtn');
  setText('tvModalTitle', 'tvModalTitle');
  setHtml('tvModalInfoBox', 'tvModalInfo');
  setText('tvIpLabelText', 'tvIpLabel');
  setText('saveTvIpBtn', 'saveIpBtn');
  setText('tvRemoteLabelText', 'tvRemoteLabel');
  setText('syncKeyToTvBtn', 'syncKeyToTvBtn');
  setText('apkDownloadBtn', 'downloadApkModalBtn');
}
const EP_POS_STORAGE_KEY = 'chillfilm_nki_ep_pos_v1';

function loadPrefs() {
  try {
    const saved = JSON.parse(localStorage.getItem('chillfilm_nki_prefs_v1') || '{}');
    return { ...DEFAULT_PREFS, ...saved };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

function savePrefs(skipCloudPush = false) {
  localStorage.setItem('chillfilm_nki_prefs_v1', JSON.stringify(state.prefs));
  if (!skipCloudPush && isCloudSignedIn()) {
    clearTimeout(state.prefsPushTimer);
    state.prefsPushTimer = setTimeout(() => {
      const syncablePrefs = {
        uiLang: state.prefs.uiLang,
        primaryLang: state.prefs.primaryLang,
        secondaryLang: state.prefs.secondaryLang,
        dualSub: state.prefs.dualSub,
        subEnabled: state.prefs.subEnabled,
        primarySize: state.prefs.primarySize,
        secondarySize: state.prefs.secondarySize,
        primaryColor: state.prefs.primaryColor,
        secondaryColor: state.prefs.secondaryColor,
        boxOpacity: state.prefs.boxOpacity,
        geminiModel: state.prefs.geminiModel,
      };
      cloudPushState({ prefs: syncablePrefs, prefsUpdatedAt: Date.now() }).catch(() => {});
    }, 1800);
  }
}

function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem('chillfilm_nki_history_v1') || '[]');
  } catch {
    return [];
  }
}

function buildEpisodePosKey(movieSlug, ep) {
  const m = normalizeMovieSlug(movieSlug);
  const epKey = normalizeEpisodeKey(ep?.name || ep?.slug || '');
  const urlPart = (m === 'direct' || m === 'local')
    ? String(ep?.link_m3u8 || '').split('?')[0].trim().toLowerCase()
    : '';
  return `${m}|${epKey}|${urlPart}`;
}

function loadEpisodePosMap() {
  try {
    return JSON.parse(localStorage.getItem(EP_POS_STORAGE_KEY) || '{}');
  } catch {
    return {};
  }
}

function getSavedEpisodePosition(movieSlug, ep) {
  if (!movieSlug || !ep) return 0;
  const map = loadEpisodePosMap();
  const key = buildEpisodePosKey(movieSlug, ep);
  const legacyKey = `${String(movieSlug || '').trim().toLowerCase()}|${String(ep?.slug || ep?.name || '').trim().toLowerCase()}|`;
  const entry = map[key] || map[legacyKey];
  if (entry && typeof entry.pos === 'number' && entry.pos > 2) {
    if (entry.dur > 30 && entry.pos / entry.dur >= 0.97) return 0;
    return Math.floor(entry.pos);
  }
  const canonicalSlug = normalizeMovieSlug(movieSlug);
  const canonicalEp = normalizeEpisodeKey(ep?.name || ep?.slug || '');
  const hist = loadHistory().find(
    (h) => normalizeMovieSlug(h.slug) === canonicalSlug &&
      (h.epUrl === ep.link_m3u8 || h.epName === ep.name || normalizeEpisodeKey(h.epName || h.epSlug) === canonicalEp)
  );
  if (hist && hist.position > 2) {
    if (hist.duration > 30 && hist.position / hist.duration >= 0.97) return 0;
    return Math.floor(hist.position);
  }
  return 0;
}

function getLatestMovieHistory(movieSlug) {
  if (!movieSlug) return null;
  const target = normalizeMovieSlug(movieSlug);
  return loadHistory().find((h) => normalizeMovieSlug(h.slug) === target) || null;
}

function saveHistoryItem(movie, ep, currentTime, duration, { immediateCloudFlush = false, useBeacon = false } = {}) {
  if (!movie || !ep) return;
  const pos = Math.floor(currentTime || 0);
  const dur = Math.floor(duration || 0);
  if (pos < 2) return;

  const now = Date.now();
  const canonicalSlug = normalizeMovieSlug(movie.slug);
  const canonicalEpKey = normalizeEpisodeKey(ep?.name || ep?.slug || '');

  // 1. Update per-episode resume map (persists across ALL episodes of a series)
  const map = loadEpisodePosMap();
  const epKey = buildEpisodePosKey(movie.slug, ep);
  const isFinished = dur > 30 && pos / dur >= 0.97;
  if (isFinished) {
    delete map[epKey];
  } else {
    map[epKey] = { pos, dur, epName: ep.name, ts: now };
    const keys = Object.keys(map);
    if (keys.length > 200) {
      keys.sort((a, b) => (map[b].ts || 0) - (map[a].ts || 0));
      keys.slice(200).forEach((k) => delete map[k]);
    }
  }
  try {
    localStorage.setItem(EP_POS_STORAGE_KEY, JSON.stringify(map));
  } catch {
    // Ignore storage quota
  }

  // 2. Update "Continue Watching" movie history strip
  if (dur >= 10) {
    const list = loadHistory().filter((h) => normalizeMovieSlug(h.slug) !== canonicalSlug);
    list.unshift({
      slug: canonicalSlug,
      name: movie.name,
      origin_name: movie.origin_name || '',
      poster: movie.poster,
      year: movie.year || '',
      epName: ep.name,
      epSlug: canonicalEpKey,
      epUrl: ep.link_m3u8,
      position: isFinished ? 0 : pos,
      duration: dur,
      progress: Math.min(1, Math.max(0.02, pos / dur)),
      ts: now,
    });
    try {
      localStorage.setItem('chillfilm_nki_history_v1', JSON.stringify(list.slice(0, 30)));
    } catch {
      // Ignore storage quota
    }
  }

  // 3. Queue automatic background Cloud Sync to Neon Postgres (Android <-> Web <-> Smart TV)
  if (isCloudSignedIn() && canonicalSlug !== 'direct' && canonicalSlug !== 'local' && canonicalSlug !== 'unknown') {
    state.pendingCloudProgress.set(`${canonicalSlug}|${canonicalEpKey}`, {
      movieSlug: canonicalSlug,
      episodeKey: canonicalEpKey,
      title: movie.name || canonicalSlug,
      posterUrl: movie.poster || '',
      countryCode: '',
      episodeName: ep.name || 'Full',
      streamUrl: ep.link_m3u8 || '',
      positionMs: (isFinished ? 0 : pos) * 1000,
      durationMs: dur * 1000,
      updatedAt: now,
    });
    if (immediateCloudFlush || now - state.lastCloudPushTs >= 15000) {
      flushPendingCloudProgress(useBeacon);
    }
  }
}

function flushPendingCloudProgress(useBeacon = false) {
  if (!isCloudSignedIn() || state.pendingCloudProgress.size === 0) return;
  const items = Array.from(state.pendingCloudProgress.values());
  state.pendingCloudProgress.clear();
  state.lastCloudPushTs = Date.now();
  cloudPushState({ watchProgress: items, useBeacon }).catch(() => {});
}

async function pullCloudStateAndMerge({ silent = true, force = false } = {}) {
  if (!isCloudSignedIn()) return false;
  const now = Date.now();
  if (!force && now - state.lastCloudPullTs < 15000) return false;
  state.lastCloudPullTs = now;
  try {
    const data = await cloudPullState(0);
    if (!data?.ok) return false;

    const map = loadEpisodePosMap();
    const histList = loadHistory();
    const histBySlug = new Map(histList.map((h) => [normalizeMovieSlug(h.slug), h]));
    let changed = false;

    if (Array.isArray(data.watchProgress)) {
      for (const item of data.watchProgress) {
        if (!item?.movieSlug) continue;
        const slug = normalizeMovieSlug(item.movieSlug);
        const epKey = normalizeEpisodeKey(item.episodeKey || item.episodeName || 'full');
        const mapKey = `${slug}|${epKey}|`;
        const pos = Math.floor((Number(item.positionMs) || 0) / 1000);
        const dur = Math.floor((Number(item.durationMs) || 0) / 1000);
        const remoteTs = Number(item.updatedAt || 0);

        const localEntry = map[mapKey];
        if (!localEntry || remoteTs > (localEntry.ts || 0)) {
          if (dur > 30 && pos / dur >= 0.97) {
            delete map[mapKey];
          } else if (pos >= 2) {
            map[mapKey] = { pos, dur, epName: item.episodeName || epKey, ts: remoteTs };
          }
          changed = true;
        }

        const existingHist = histBySlug.get(slug);
        if (!existingHist || remoteTs > (existingHist.ts || 0)) {
          if (dur >= 10 || pos >= 2) {
            histBySlug.set(slug, {
              slug,
              name: item.title || existingHist?.name || slug,
              origin_name: existingHist?.origin_name || '',
              poster: item.posterUrl || existingHist?.poster || '',
              year: existingHist?.year || '',
              epName: item.episodeName || epKey,
              epSlug: epKey,
              epUrl: item.streamUrl || existingHist?.epUrl || '',
              position: pos,
              duration: dur,
              progress: dur > 0 ? Math.min(1, Math.max(0.02, pos / dur)) : 0.05,
              ts: remoteTs,
            });
            changed = true;
          }
        }
      }
    }

    if (changed) {
      const mergedHist = Array.from(histBySlug.values())
        .sort((a, b) => (b.ts || 0) - (a.ts || 0))
        .slice(0, 30);
      try {
        localStorage.setItem(EP_POS_STORAGE_KEY, JSON.stringify(map));
        localStorage.setItem('chillfilm_nki_history_v1', JSON.stringify(mergedHist));
      } catch {
        // ignore quota
      }
      renderHistorySection();
      if ($('detailView')?.classList.contains('active') && state.currentMovie) {
        renderDetailView(state.currentMovie);
      }
    }

    // Merge synced subtitle preferences if server prefs exist
    if (data.prefs && typeof data.prefs === 'object') {
      const p = data.prefs;
      let prefsChanged = false;
      ['primaryLang', 'secondaryLang', 'dualSub', 'subEnabled', 'primarySize', 'secondarySize', 'primaryColor', 'secondaryColor', 'boxOpacity', 'geminiModel'].forEach((k) => {
        if (p[k] !== undefined && p[k] !== state.prefs[k]) {
          state.prefs[k] = p[k];
          prefsChanged = true;
        }
      });
      if (prefsChanged) savePrefs(true);
    }

    if (!silent) showToast('☁️ Cloud Sync completed!');
    return true;
  } catch (err) {
    if (!silent) showToast(`Cloud sync error: ${err.message}`);
    return false;
  }
}

function showToast(msg) {
  const el = $('toast');
  el.textContent = msg;
  el.style.display = 'block';
  clearTimeout(el._t);
  el._t = setTimeout(() => { el.style.display = 'none'; }, 3200);
}

function normalizeImage(url) {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  return `${PHIM_CDN}/${url.replace(/^\/+/, '')}`;
}

function formatTime(sec) {
  if (!sec || !isFinite(sec) || sec < 0) return '00:00';
  const s = Math.floor(sec);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  const pad = (n) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(r)}` : `${pad(m)}:${pad(r)}`;
}

/* ==================== CATALOG, NETFLIX SWIMLANES & SEARCH ==================== */
function mapPhimItems(json) {
  const rawItems = json?.items || json?.data?.items || [];
  return rawItems.map((m) => ({
    slug: m.slug,
    name: m.name,
    origin_name: m.origin_name || '',
    year: m.year ? String(m.year) : '',
    quality: m.quality || 'FHD',
    episode_current: m.episode_current || '',
    poster: normalizeImage(m.poster_url || m.thumb_url),
    backdrop: normalizeImage(m.thumb_url || m.poster_url),
    genres: Array.isArray(m.category) ? m.category.map((c) => c?.name).filter(Boolean).slice(0, 3) : [],
    tmdbScore: Number(m.tmdb?.vote_average || 0) || 0,
    content: '',
  }));
}

async function fetchCategoryList(cat) {
  try {
    const res = await fetch(cat.url);
    const json = await res.json();
    return mapPhimItems(json);
  } catch {
    return [];
  }
}

async function loadCategory(catId, { forceRefresh = false, silent = false } = {}) {
  state.activeCategory = catId;
  renderCategories();
  if (!silent) closeDetailView();

  const liftEl = document.querySelector('.content-lift');
  const swimWrap = $('homeSwimlanes');
  const gridSec = $('catalogGridSection');

  // HOME ("hot"): Live Multi-Source Aggregated Feed (/api/catalog/feed) + Auto-Rotating Hero Billboard!
  if (catId === 'hot') {
    if (liftEl) liftEl.classList.remove('no-hero');
    $('topbar')?.classList.remove('solid');
    gridSec.style.display = 'none';
    swimWrap.style.display = 'block';
    if (!silent) {
      swimWrap.innerHTML = `<div style="text-align:center;padding:42px;color:#b3b3b3;">${escapeHtml(tr('loadingCatalog'))}</div>`;
    }

    try {
      const feed = await fetchLiveCatalogFeed(forceRefresh);
      if (feed?.ok && Array.isArray(feed.rows) && feed.rows.length > 0) {
        state.catalogUpdatedAt = feed.updatedAt || Date.now();
        state.crawlerStatus = feed.crawlerStatus || null;
        if (Array.isArray(feed.countries) && feed.countries.length) {
          renderCountryPicker(feed.countries);
        }
        state.homeRows = feed.rows;
        state.movies = feed.rows[0]?.movies || [];
        const heroList = Array.isArray(feed.heroMovies) && feed.heroMovies.length
          ? feed.heroMovies
          : state.movies.slice(0, 6);
        setHeroSlides(heroList);
        renderHistorySection();
        renderHomeSwimlanes();
        if (forceRefresh && !silent) showToast('🔥 Updated latest multi-source movies!');
        return;
      }
    } catch {
      // Graceful fallback to direct PhimAPI v3 if local/offline
    }

    const rowCatIds = ['hot', 'phim-le', 'phim-bo', 'han-quoc', 'au-my', 'hoat-hinh', 'trung-quoc', 'thai-lan', 'nhat-ban'];
    const rowCats = rowCatIds.map((id) => CATEGORIES.find((c) => c.id === id)).filter(Boolean);
    const results = await Promise.all(rowCats.map((c) => fetchCategoryList(c)));

    state.catalogUpdatedAt = Date.now();
    state.movies = results[0] || [];
    state.homeRows = rowCats
      .map((c, idx) => ({ id: c.id, isTopTen: idx === 0, movies: results[idx] || [] }))
      .filter((r) => r.movies.length > 0);

    if (state.movies.length) {
      setHeroSlides(state.movies.slice(0, 6));
      renderHistorySection();
      renderHomeSwimlanes();
    } else if (!silent) {
      swimWrap.innerHTML = `<div style="text-align:center;padding:36px;color:#fca5a5;">${escapeHtml(tr('loadError'))}</div>`;
    }
    return;
  }

  // Dedicated Category Grid View
  if (liftEl) liftEl.classList.remove('no-hero');
  $('topbar')?.classList.remove('solid');
  swimWrap.style.display = 'none';
  gridSec.style.display = 'block';
  if (!silent) {
    $('catalogGrid').innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:42px;color:#b3b3b3;">${escapeHtml(tr('loadingCatalog'))}</div>`;
  }

  const cat = CATEGORIES.find((c) => c.id === catId);
  let list = [];
  try {
    const browseParams = cat?.browse || { country: catId };
    const data = await fetchCatalogBrowse(browseParams);
    if (Array.isArray(data?.countries) && data.countries.length && !state.globalCountries.length) {
      renderCountryPicker(data.countries);
    }
    list = Array.isArray(data?.items) ? data.items : [];
  } catch {
    if (cat?.url) list = await fetchCategoryList(cat);
  }
  if (list.length) {
    state.movies = list;
    setHeroSlides(list.slice(0, 5));
    renderHistorySection();
    renderMovieGrid(state.movies, getCategoryLabel(catId));
  } else if (!silent) {
    $('catalogGrid').innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:36px;color:#fca5a5;">${escapeHtml(tr('loadError'))}</div>`;
  }
}

function renderHomeSwimlanes() {
  const wrap = $('homeSwimlanes');
  if (!wrap) return;
  wrap.innerHTML = '';

  state.homeRows.forEach((row) => {
    const sec = document.createElement('section');
    sec.className = 'nf-row-section';

    const head = document.createElement('div');
    head.className = 'section-head';
    let updatedLabel = '';
    if (row.isTopTen && state.catalogUpdatedAt) {
      updatedLabel = `<span class="live-feed-pill">● LIVE ${new Date(state.catalogUpdatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>`;
    } else if (row.id === 'crawled' && state.crawlerStatus?.totalIndexed > 0) {
      const cs = state.crawlerStatus;
      updatedLabel = `<span class="live-feed-pill">⚡ ${cs.totalIndexed} INDEXED • ${cs.healthySources} SOURCES • ${escapeHtml(cs.schedule || 'AUTO')}</span>`;
    }
    const rowLabel = row.customTitle || getCategoryLabel(row.id);
    head.innerHTML = `
      <div class="section-title">
        ${row.isTopTen ? '<span class="top10-pill">TOP 10</span>' : ''}
        <span>${escapeHtml(rowLabel)}</span>
        ${updatedLabel}
      </div>
    `;
    sec.appendChild(head);

    const railWrap = document.createElement('div');
    railWrap.className = 'row-rail-wrap';

    const leftBtn = document.createElement('button');
    leftBtn.className = 'rail-arrow left';
    leftBtn.innerHTML = '‹';
    leftBtn.setAttribute('aria-label', 'Scroll left');

    const rightBtn = document.createElement('button');
    rightBtn.className = 'rail-arrow right';
    rightBtn.innerHTML = '›';
    rightBtn.setAttribute('aria-label', 'Scroll right');

    const rail = document.createElement('div');
    rail.className = 'row-rail';

    const displayList = row.isTopTen ? row.movies.slice(0, 10) : row.movies.slice(0, 18);
    displayList.forEach((movie, idx) => {
      const card = createMovieCardElement(movie);
      card.onclick = () => openMovieDetail(movie, false);
      if (row.isTopTen) {
        const rankWrap = document.createElement('div');
        rankWrap.className = 'rank-card-wrap';
        const num = document.createElement('span');
        num.className = 'rank-num';
        num.textContent = String(idx + 1);
        rankWrap.appendChild(num);
        rankWrap.appendChild(card);
        rail.appendChild(rankWrap);
      } else {
        rail.appendChild(card);
      }
    });

    leftBtn.onclick = () => rail.scrollBy({ left: -Math.max(420, rail.clientWidth * 0.78), behavior: 'smooth' });
    rightBtn.onclick = () => rail.scrollBy({ left: Math.max(420, rail.clientWidth * 0.78), behavior: 'smooth' });

    railWrap.appendChild(leftBtn);
    railWrap.appendChild(rail);
    railWrap.appendChild(rightBtn);
    sec.appendChild(railWrap);
    wrap.appendChild(sec);
  });
}

async function executeSearch(keyword) {
  const q = (keyword || '').trim();
  if (!q) {
    loadCategory(state.activeCategory);
    return;
  }
  closeDetailView();
  $('heroBox').style.display = 'none';
  $('historySection').style.display = 'none';
  $('homeSwimlanes').style.display = 'none';
  $('catalogGridSection').style.display = 'block';
  document.querySelector('.content-lift')?.classList.add('no-hero');
  $('topbar')?.classList.add('solid');
  $('catalogTitle').textContent = tr('searchResultsTitle', { q, n: '...' });
  $('catalogGrid').innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:42px;color:#b3b3b3;">${escapeHtml(tr('loadingCatalog'))}</div>`;

  try {
    let list = [];
    try {
      const data = await fetchCatalogBrowse({ q });
      list = Array.isArray(data?.items) ? data.items : [];
    } catch {
      const res = await fetch(`${PHIM_API}/v1/api/tim-kiem?keyword=${encodeURIComponent(q)}&limit=24`);
      const json = await res.json();
      list = mapPhimItems(json);
    }
    renderMovieGrid(list, tr('searchResultsTitle', { q, n: list.length }));
  } catch {
    showToast(tr('loadError'));
  }
}

function renderCategories() {
  const strip = $('catStrip');
  const nav = $('desktopNavLinks');
  strip.innerHTML = '';
  if (nav) nav.innerHTML = '';

  const desktopFeatured = ['hot', 'phim-bo', 'phim-le', 'hoat-hinh', 'han-quoc', 'au-my'];

  CATEGORIES.forEach((c) => {
    const onPick = () => {
      $('searchInputDesktop').value = '';
      $('searchInputMobile').value = '';
      loadCategory(c.id);
    };

    const btn = document.createElement('button');
    btn.className = `cat-chip ${state.activeCategory === c.id ? 'active' : ''}`;
    btn.textContent = getCategoryLabel(c.id);
    btn.onclick = onPick;
    strip.appendChild(btn);

    if (nav && desktopFeatured.includes(c.id)) {
      const link = document.createElement('button');
      link.className = `nav-link-btn ${state.activeCategory === c.id ? 'active' : ''}`;
      link.textContent = getCategoryLabel(c.id);
      link.onclick = onPick;
      nav.appendChild(link);
    }
  });
}

function setHeroSlides(movies) {
  const valid = (Array.isArray(movies) ? movies : []).filter((m) => m && m.slug);
  state.heroMovies = valid;
  state.heroIndex = 0;
  clearInterval(state.heroTimer);
  if (!valid.length) {
    $('heroBox').style.display = 'none';
    return;
  }
  renderHero(valid[0]);
  if (valid.length > 1) {
    state.heroTimer = setInterval(() => {
      if (state.heroPaused || $('playerModal')?.classList.contains('open') || $('detailView')?.classList.contains('active')) {
        return;
      }
      state.heroIndex = (state.heroIndex + 1) % state.heroMovies.length;
      renderHero(state.heroMovies[state.heroIndex]);
    }, 8000);
  }
}

function renderHero(movie) {
  const box = $('heroBox');
  if (!movie) {
    box.style.display = 'none';
    return;
  }
  box.style.display = 'flex';
  const lang = resolveLanguage(state.prefs.primaryLang);
  const matchText = movie.tmdbScore >= 5 ? `★ ${movie.tmdbScore.toFixed(1)} TMDB` : '🔥 HOT NEW';
  const genreLine = Array.isArray(movie.genres) && movie.genres.length ? movie.genres.join(' • ') : '';
  const epLine = movie.episode_current ? `<span style="color:#86efac;font-weight:700;">${escapeHtml(movie.episode_current)}</span>` : '';
  const descText = movie.content || movie.origin_name || tr('heroDefaultDesc');
  const totalSlides = state.heroMovies.length;
  const controlsHtml = totalSlides > 1
    ? `
      <div class="hero-carousel-controls">
        <button class="hero-nav-btn" id="heroPrevBtn" aria-label="Previous featured">‹</button>
        <div class="hero-dots">
          ${state.heroMovies.map((_, i) => `<button class="hero-dot ${i === state.heroIndex ? 'active' : ''}" data-hero-idx="${i}" aria-label="Slide ${i + 1}"></button>`).join('')}
        </div>
        <button class="hero-nav-btn" id="heroNextBtn" aria-label="Next featured">›</button>
      </div>
    `
    : '';

  box.innerHTML = `
    <img class="hero-bg" src="${movie.backdrop || movie.poster}" alt="${escapeHtml(movie.name)}" />
    <div class="hero-overlay"></div>
    <div class="hero-content">
      <div class="hero-inner">
        <div class="hero-eyebrow">
          <div class="nf-series-mark"><span class="nki-tag">NKI</span><span>AI CINEMA</span></div>
          <span class="live-feed-pill">● AUTO-UPDATED</span>
        </div>
        <h1 class="hero-title">${escapeHtml(movie.name)}</h1>
        <div class="hero-meta">
          <span class="match-score">${escapeHtml(matchText)}</span>
          <span style="color:#e5e5e5;">${escapeHtml(movie.year || '2026')}</span>
          ${epLine}
          <span class="badge-ai">${escapeHtml(tr('heroBadge', { lang: lang.badge }))}</span>
          <span style="border:1px solid rgba(255,255,255,0.35);padding:1px 6px;border-radius:3px;font-size:10.5px;">${escapeHtml(movie.quality || 'FHD')}</span>
        </div>
        ${genreLine ? `<div class="hero-genres">${escapeHtml(genreLine)}</div>` : ''}
        <p class="hero-sub">${escapeHtml(descText)}</p>
        <div class="hero-actions">
          <button class="btn-nf-play" id="heroPlayBtn">${escapeHtml(tr('watchNow'))}</button>
          <button class="btn-nf-info" id="heroDetailBtn">ⓘ ${escapeHtml(tr('detailsBtn'))}</button>
        </div>
      </div>
    </div>
    ${controlsHtml}
  `;
  box.onmouseenter = () => { state.heroPaused = true; };
  box.onmouseleave = () => { state.heroPaused = false; };
  $('heroPlayBtn').onclick = () => openMovieDetail(movie, true);
  $('heroDetailBtn').onclick = () => openMovieDetail(movie, false);
  if (totalSlides > 1) {
    $('heroPrevBtn').onclick = () => {
      state.heroIndex = (state.heroIndex - 1 + totalSlides) % totalSlides;
      renderHero(state.heroMovies[state.heroIndex]);
    };
    $('heroNextBtn').onclick = () => {
      state.heroIndex = (state.heroIndex + 1) % totalSlides;
      renderHero(state.heroMovies[state.heroIndex]);
    };
    box.querySelectorAll('[data-hero-idx]').forEach((dot) => {
      dot.onclick = () => {
        state.heroIndex = Number(dot.dataset.heroIdx) || 0;
        renderHero(state.heroMovies[state.heroIndex]);
      };
    });
  }
}

function renderHistorySection() {
  const history = loadHistory();
  const sec = $('historySection');
  if (!history.length) {
    sec.style.display = 'none';
    return;
  }
  sec.style.display = 'block';
  const grid = $('historyGrid');
  grid.innerHTML = '';
  history.slice(0, 12).forEach((item) => {
    const card = createMovieCardElement({
      slug: item.slug,
      name: item.name,
      year: `${item.epName} • ${formatTime(item.position)}`,
      quality: formatTime(item.position),
      poster: item.poster,
      progress: item.progress,
    });
    card.onclick = () => openMovieDetail(item.slug, true, item.position, item.epUrl);
    grid.appendChild(card);
  });
}

function renderMovieGrid(list, title) {
  $('catalogTitle').textContent = title;
  const grid = $('catalogGrid');
  grid.innerHTML = '';
  if (!list.length) {
    grid.innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:42px;color:#b3b3b3;">${escapeHtml(tr('noResults'))}</div>`;
    return;
  }
  list.forEach((movie) => {
    const card = createMovieCardElement(movie);
    card.onclick = () => openMovieDetail(movie, false);
    grid.appendChild(card);
  });
}

function createMovieCardElement(movie) {
  const el = document.createElement('div');
  const metaBits = [
    movie.year || '',
    Array.isArray(movie.countries) && movie.countries.length ? movie.countries[0] : '',
    movie.sourceName && movie.source !== 'phimapi' ? movie.sourceName.split(' ')[0] : '',
  ].filter(Boolean);
  const metaLine = metaBits.length ? metaBits.join(' • ') : (movie.origin_name || 'HD • AI Sub');
  const posterSrc = movie.poster || './assets/icon-192.png?v=2';

  el.className = 'movie-card';
  el.innerHTML = `
    <div class="poster-wrap">
      <img loading="lazy" src="${escapeHtml(posterSrc)}" alt="${escapeHtml(movie.name)}" />
      <span class="card-nki-mark">NKI</span>
      <span class="card-badge">AI</span>
      <span class="card-quality">${escapeHtml((movie.episode_current || movie.quality || 'HD').slice(0, 12))}</span>
      <div class="card-hover-play"><span>▶</span></div>
      ${movie.progress ? `<div class="card-progress"><div class="card-progress-fill" style="width:${Math.round(movie.progress * 100)}%"></div></div>` : ''}
    </div>
    <div class="card-info">
      <div class="card-title">${escapeHtml(movie.name)}</div>
      <div class="card-meta">${escapeHtml(metaLine)}</div>
    </div>
  `;
  return el;
}

/* ==================== MOVIE DETAIL (MULTI-SOURCE + VN-SAFE) ==================== */
async function openMovieDetail(movieOrSlug, autoPlayFirst = false, resumeSec = 0, resumeUrl = '') {
  const movieObj = typeof movieOrSlug === 'object' && movieOrSlug ? movieOrSlug : null;
  const slug = movieObj ? movieObj.slug : String(movieOrSlug || '').trim();
  const source = movieObj?.source || 'phimapi';
  const sourceId = movieObj?.sourceId || '';

  // Direct stream (e.g. Live Cinema M3U channels or Custom JSON direct video) -> play immediately!
  if (movieObj && movieObj.streamUrl && source === 'direct') {
    const directMovie = {
      slug: movieObj.slug || 'direct',
      source: 'direct',
      name: movieObj.name || 'Live Cinema',
      origin_name: movieObj.origin_name || '',
      year: movieObj.year || 'LIVE',
      quality: movieObj.quality || 'LIVE',
      poster: movieObj.poster || '',
      backdrop: movieObj.backdrop || movieObj.poster || '',
      type: 'single',
      servers: [{ server_name: movieObj.sourceName || 'Live', episodes: [{ name: 'Live', slug: 'full', link_m3u8: movieObj.streamUrl }] }],
    };
    state.currentMovie = directMovie;
    state.episodesFlat = directMovie.servers[0].episodes;
    openPlayer(directMovie, state.episodesFlat[0], 0);
    return;
  }

  showToast('Đang tải thông tin phim...');
  try {
    let detail = null;
    try {
      detail = await fetchCatalogDetail({ source, slug, sourceId });
    } catch (apiErr) {
      if (String(apiErr?.message || '').toLowerCase().includes('blocked')) {
        showToast('🛡️ Nội dung này bị chặn theo chính sách lọc phim sạch.');
        return;
      }
      if (source !== 'phimapi') throw apiErr;
      // Offline fallback for phimapi
      const res = await fetch(`${PHIM_API}/phim/${encodeURIComponent(slug)}`);
      const json = await res.json();
      const m = json.movie || {};
      const servers = (json.episodes || []).map((s) => ({
        server_name: s.server_name || 'Server',
        episodes: (s.server_data || []).filter((e) => e.link_m3u8),
      })).filter((s) => s.episodes.length);
      detail = {
        slug: m.slug || slug,
        source: 'phimapi',
        name: m.name || slug,
        origin_name: m.origin_name || '',
        year: m.year ? String(m.year) : '',
        quality: m.quality || 'FHD',
        time: m.time || '',
        content: (m.content || '').replace(/<[^>]+>/g, '').trim(),
        imdbId: m.imdb?.id || '',
        type: m.type || 'single',
        poster: normalizeImage(m.poster_url || m.thumb_url),
        backdrop: normalizeImage(m.thumb_url || m.poster_url),
        servers,
      };
    }

    if (!detail) throw new Error('Not found');
    state.currentMovie = detail;
    state.episodesFlat = detail.servers?.[0]?.episodes || [];

    if (autoPlayFirst && state.episodesFlat.length) {
      const savedHist = resumeSec <= 0 && !resumeUrl ? getLatestMovieHistory(detail.slug) : null;
      const targetUrl = resumeUrl || savedHist?.epUrl || '';
      const targetEpName = savedHist?.epName || '';
      const targetEp =
        (targetUrl && state.episodesFlat.find((e) => e.link_m3u8 === targetUrl)) ||
        (targetEpName && state.episodesFlat.find((e) => e.name === targetEpName)) ||
        state.episodesFlat[0];
      const targetSec = resumeSec > 2 ? resumeSec : getSavedEpisodePosition(detail.slug, targetEp);
      openPlayer(detail, targetEp, targetSec);
      return;
    }

    renderDetailView(detail);
  } catch (e) {
    showToast(e?.message || 'Không thể tải chi tiết phim này!');
  }
}

function renderDetailView(detail) {
  $('homeView').style.display = 'none';
  $('topbar')?.classList.add('solid');
  const dv = $('detailView');
  dv.classList.add('active');
  const allEps = detail.servers.flatMap((s) => s.episodes);
  const firstEp = detail.servers[0]?.episodes?.[0];
  const savedHist = getLatestMovieHistory(detail.slug);
  const resumeEp = savedHist
    ? (allEps.find((e) => e.link_m3u8 === savedHist.epUrl || e.name === savedHist.epName) || firstEp)
    : firstEp;
  const resumeSec = resumeEp ? getSavedEpisodePosition(detail.slug, resumeEp) : 0;
  const primaryBtnLabel = resumeEp && resumeSec > 5
    ? tr('continueEp', { ep: resumeEp.name, time: formatTime(resumeSec) })
    : (firstEp ? tr('watchNowEp', { ep: firstEp.name }) : '');
  const lang = resolveLanguage(state.prefs.primaryLang);

  dv.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;">
      <button class="btn-glass" id="detailBackBtn">${escapeHtml(tr('backToCatalog'))}</button>
      ${resumeEp ? `<button class="btn-glass" id="detailCastBtn">${escapeHtml(tr('castToTv'))}</button>` : ''}
    </div>
    <div class="detail-hero">
      <img class="detail-poster" src="${detail.poster}" alt="${escapeHtml(detail.name)}" />
      <div style="display:flex;flex-direction:column;gap:10px;min-width:0;">
        <div class="hero-meta">
          <span class="match-score">98% Match</span>
          <span class="badge-ai">AI SUB (${lang.badge})</span>
          <span style="font-size:12px;color:#b3b3b3;font-weight:700;">${escapeHtml(detail.year)} • ${escapeHtml(detail.time || detail.quality)}</span>
        </div>
        <h2 style="font-size:clamp(20px,3.2vw,32px);font-weight:900;">${escapeHtml(detail.name)}</h2>
        ${detail.origin_name ? `<div style="font-size:13px;color:#b3b3b3;">${escapeHtml(detail.origin_name)}</div>` : ''}
        <p style="font-size:13.5px;color:#e5e5e5;line-height:1.6;">${escapeHtml(detail.content || tr('heroDefaultDesc'))}</p>
        <div style="display:flex;flex-wrap:wrap;gap:10px;margin-top:6px;">
          ${resumeEp ? `<button class="btn-nf-play" id="detailPlayPrimary">${escapeHtml(primaryBtnLabel)}</button>` : `<span style="color:#fca5a5;font-size:13px;">${escapeHtml(tr('noStream'))}</span>`}
        </div>
      </div>
    </div>
    <div id="detailServersWrap" style="margin-top:22px;"></div>
  `;

  $('detailBackBtn').onclick = closeDetailView;
  if (resumeEp) {
    $('detailPlayPrimary').onclick = () => openPlayer(detail, resumeEp, resumeSec);
    $('detailCastBtn').onclick = () => castCurrentToTv(detail, resumeEp, resumeSec);
  }

  const wrap = $('detailServersWrap');
  detail.servers.forEach((srv) => {
    const block = document.createElement('div');
    block.style.marginBottom = '18px';
    block.innerHTML = `<div class="section-title" style="margin-bottom:8px;">${escapeHtml(tr('episodesCount', { srv: srv.server_name, n: srv.episodes.length }))}</div>`;
    const grid = document.createElement('div');
    grid.className = 'ep-grid';
    srv.episodes.forEach((ep) => {
      const epSavedSec = getSavedEpisodePosition(detail.slug, ep);
      const b = document.createElement('button');
      b.className = `ep-btn ${epSavedSec > 5 ? 'active' : ''}`;
      b.innerHTML = epSavedSec > 5
        ? `<div>${escapeHtml(ep.name)}</div><div style="font-size:10px;opacity:.88;margin-top:2px;">▶ ${formatTime(epSavedSec)}</div>`
        : escapeHtml(ep.name);
      b.onclick = () => {
        state.episodesFlat = srv.episodes;
        openPlayer(detail, ep, epSavedSec);
      };
      grid.appendChild(b);
    });
    block.appendChild(grid);
    wrap.appendChild(block);
  });
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function closeDetailView() {
  $('detailView').classList.remove('active');
  $('homeView').style.display = 'block';
  if ($('heroBox').style.display !== 'none') {
    $('topbar')?.classList.remove('solid');
  }
}

/* ==================== CHILL-PLAYER ENGINE (HLS + WEB AUDIO 200% BOOST + EQ) ==================== */
function ensureWebAudioDsp(videoEl) {
  if (state.audioCtx) {
    if (state.audioCtx.state === 'suspended') state.audioCtx.resume().catch(() => {});
    return;
  }
  try {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    const ctx = new AC();
    const src = ctx.createMediaElementSource(videoEl);
    const low = ctx.createBiquadFilter();
    low.type = 'lowshelf';
    low.frequency.value = 180;
    const mid = ctx.createBiquadFilter();
    mid.type = 'peaking';
    mid.frequency.value = 1200;
    mid.Q.value = 1.0;
    const high = ctx.createBiquadFilter();
    high.type = 'highshelf';
    high.frequency.value = 4500;
    const gain = ctx.createGain();
    gain.gain.value = state.volumeScale;
    src.connect(low);
    low.connect(mid);
    mid.connect(high);
    high.connect(gain);
    gain.connect(ctx.destination);
    state.audioCtx = ctx;
    state.gainNode = gain;
    state.eqNodes = { low, mid, high };
  } catch {
    // Graceful fallback if browser blocks Web Audio on opaque media
  }
}

function applyVolumeBoost(scale) {
  state.volumeScale = Math.max(0, Math.min(2.0, scale));
  const video = $('videoEl');
  if (state.gainNode) {
    video.volume = 1.0;
    state.gainNode.gain.value = state.volumeScale;
  } else {
    video.volume = Math.min(1.0, state.volumeScale);
  }
  const pct = Math.round(state.volumeScale * 100);
  $('boostSlider').value = pct;
  $('boostValLabel').textContent = pct > 100 ? `${pct}% BOOST` : `${pct}%`;
  $('hudSpeedBoostBtn').textContent = pct > 100 ? `🔊 ${pct}%` : `⚡ ${state.speed}x`;
}

function applyEqPreset(preset) {
  state.eqPreset = preset;
  if (state.eqNodes) {
    const map = {
      FLAT: [0, 0, 0],
      BASS: [8, 1, 0],
      VOCAL: [-3, 7, 2],
      MOVIE: [5, 2, 5],
    };
    const [l, m, h] = map[preset] || [0, 0, 0];
    state.eqNodes.low.gain.value = l;
    state.eqNodes.mid.gain.value = m;
    state.eqNodes.high.gain.value = h;
  }
  document.querySelectorAll('[data-eq]').forEach((b) => {
    b.classList.toggle('active', b.dataset.eq === preset);
  });
}

function persistCurrentPlaybackProgress({ immediate = false, useBeacon = false } = {}) {
  const video = $('videoEl');
  if (!state.currentMovie || !state.currentEpisode || !video) return;
  const cur = video.currentTime || 0;
  const dur = video.duration || 0;
  if (cur > 2 && isFinite(cur)) {
    saveHistoryItem(state.currentMovie, state.currentEpisode, cur, isFinite(dur) ? dur : 0, {
      immediateCloudFlush: immediate,
      useBeacon,
    });
    state.lastSavedPosTs = Date.now();
  }
}

async function togglePlayerFullscreen(lockLandscape = false) {
  const modal = $('playerModal');
  const video = $('videoEl');
  const fsEl = document.fullscreenElement || document.webkitFullscreenElement;
  if (!fsEl) {
    try {
      if (modal.requestFullscreen) {
        await modal.requestFullscreen({ navigationUI: 'hide' });
      } else if (modal.webkitRequestFullscreen) {
        await modal.webkitRequestFullscreen();
      } else if (video.webkitEnterFullscreen) {
        video.webkitEnterFullscreen();
      }
      const isTouch = window.matchMedia?.('(pointer: coarse)')?.matches;
      if ((lockLandscape || isTouch) && screen.orientation?.lock) {
        await screen.orientation.lock('landscape').catch(() => {});
      }
    } catch {
      if (video.webkitEnterFullscreen) {
        try { video.webkitEnterFullscreen(); } catch { /* ignore */ }
      }
    }
  } else {
    try {
      if (document.exitFullscreen) await document.exitFullscreen();
      else if (document.webkitExitFullscreen) await document.webkitExitFullscreen();
    } catch { /* ignore */ }
    try { screen.orientation?.unlock?.(); } catch { /* ignore */ }
  }
}

function openPlayer(movie, episode, startSec = 0) {
  // Save previous episode position if switching mid-session
  if (state.currentMovie && state.currentEpisode && $('playerModal').classList.contains('open')) {
    persistCurrentPlaybackProgress();
  }

  state.currentMovie = movie;
  state.currentEpisode = episode;
  const resolvedStartSec = startSec > 2 ? Math.floor(startSec) : getSavedEpisodePosition(movie.slug, episode);
  state.pendingSeekSec = resolvedStartSec;
  state.lastSavedPosTs = Date.now();

  const modal = $('playerModal');
  const video = $('videoEl');
  state.isMinimized = false;
  modal.classList.remove('minimized');
  modal.classList.add('open');
  document.body.style.overflow = 'hidden';

  $('hudMovieTitle').textContent = `${movie.name} • ${episode.name}`;
  if ($('miniTitle')) $('miniTitle').textContent = `${movie.name} • ${episode.name}`;
  updateSubStatusBadge(tr('checkingSavedSub'));
  setControlsVisible(true);

  // Destroy previous HLS instance
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }

  const streamUrl = episode.link_m3u8;
  video.crossOrigin = 'anonymous';

  const applyPendingResumeSeek = () => {
    if (state.pendingSeekSec > 2) {
      const target = (isFinite(video.duration) && video.duration > 15)
        ? Math.min(state.pendingSeekSec, video.duration - 5)
        : state.pendingSeekSec;
      state.pendingSeekSec = 0;
      if (Math.abs((video.currentTime || 0) - target) > 1.5) {
        try { video.currentTime = target; } catch { /* retry on canplay */ }
      }
      showGestureToast(tr('resumingFrom', { time: formatTime(target) }));
    }
  };

  const startPlayback = () => {
    applyPendingResumeSeek();
    video.playbackRate = state.speed;
    video.play().catch(() => {});
  };

  video.onloadedmetadata = () => {
    ensureWebAudioDsp(video);
    applyPendingResumeSeek();
  };
  video.oncanplay = () => {
    applyPendingResumeSeek();
  };

  if (streamUrl.includes('.m3u8') && window.Hls && window.Hls.isSupported()) {
    const hls = new window.Hls({
      enableWorker: true,
      lowLatencyMode: false,
      startPosition: resolvedStartSec > 2 ? resolvedStartSec : -1,
      xhrSetup: (xhr) => { xhr.withCredentials = false; },
    });
    hls.loadSource(streamUrl);
    hls.attachMedia(video);
    hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
      ensureWebAudioDsp(video);
      applyVolumeBoost(state.volumeScale);
      applyEqPreset(state.eqPreset);
      startPlayback();
    });
    hls.on(window.Hls.Events.ERROR, (_, data) => {
      if (data.fatal && data.type === window.Hls.ErrorTypes.NETWORK_ERROR) {
        hls.startLoad();
      }
    });
    state.hls = hls;
  } else {
    video.src = streamUrl;
    video.onloadedmetadata = () => {
      ensureWebAudioDsp(video);
      startPlayback();
    };
  }

  // Next episode button visibility
  const curIdx = state.episodesFlat.findIndex((e) => e.link_m3u8 === episode.link_m3u8);
  const nextEp = curIdx >= 0 && curIdx + 1 < state.episodesFlat.length ? state.episodesFlat[curIdx + 1] : null;
  $('hudNextOrAspectBtn').textContent = nextEp ? tr('nextEpBtn') : tr('aspectBtn');
  $('hudNextOrAspectBtn').onclick = () => {
    if (nextEp) {
      persistCurrentPlaybackProgress();
      openPlayer(movie, nextEp, 0);
    } else {
      openMoreModal();
    }
  };

  // Auto-discover (or instantly restore from IndexedDB!) subtitles into user's chosen AI language!
  autoLoadSubtitlesForMovie(movie, episode);
}

function minimizePlayer(e) {
  if (e && typeof e.stopPropagation === 'function') {
    e.stopPropagation();
    e.preventDefault();
  }
  const modal = $('playerModal');
  if (!modal.classList.contains('open')) return;
  persistCurrentPlaybackProgress({ immediate: true });
  renderHistorySection();
  if ($('detailView').classList.contains('active') && state.currentMovie && Array.isArray(state.currentMovie.servers) && state.currentMovie.servers.length) {
    renderDetailView(state.currentMovie);
  }
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  else if (document.webkitFullscreenElement) document.webkitExitFullscreen?.();
  try { screen.orientation?.unlock?.(); } catch { /* ignore */ }
  state.isLocked = false;
  state.isMinimized = true;
  state.minimizedAt = Date.now();
  $('lockFab').classList.remove('visible');
  modal.classList.add('minimized');
  document.body.style.overflow = '';
  const video = $('videoEl');
  if ($('miniPlayBtn')) $('miniPlayBtn').textContent = video.paused ? '▶' : '⏸';
}

function restorePlayer(e) {
  if (e && typeof e.stopPropagation === 'function') {
    e.stopPropagation();
  }
  const modal = $('playerModal');
  if (!modal.classList.contains('open')) return;
  state.isMinimized = false;
  modal.classList.remove('minimized');
  document.body.style.overflow = 'hidden';
  setControlsVisible(true);
}

function closePlayer(e) {
  if (e && typeof e.stopPropagation === 'function') {
    e.stopPropagation();
  }
  const video = $('videoEl');
  persistCurrentPlaybackProgress({ immediate: true });
  renderHistorySection();
  if ($('detailView').classList.contains('active') && state.currentMovie && Array.isArray(state.currentMovie.servers) && state.currentMovie.servers.length) {
    renderDetailView(state.currentMovie);
  }
  video.pause();
  video.removeAttribute('src');
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  else if (document.webkitFullscreenElement) document.webkitExitFullscreen?.();
  try { screen.orientation?.unlock?.(); } catch { /* ignore */ }
  $('playerModal').classList.remove('open', 'minimized');
  document.body.style.overflow = '';
  state.isLocked = false;
  state.isMinimized = false;
  $('lockFab').classList.remove('visible');
}

function setControlsVisible(show) {
  state.controlsVisible = show;
  const hud = $('playerHud');
  const modal = $('playerModal');
  hud.classList.toggle('hidden', !show || state.isLocked);
  modal.classList.toggle('controls-visible', show && !state.isLocked);
  clearTimeout(state.controlsTimer);
  if (show && !state.isLocked && !$('videoEl').paused) {
    state.controlsTimer = setTimeout(() => setControlsVisible(false), 4500);
  }
}

/* ==================== UNIVERSAL AI SUBTITLES IN WEB PLAYER ==================== */
function updateSubStatusBadge(text) {
  state.subStatusText = text;
  const lang = resolveLanguage(state.prefs.primaryLang);
  $('hudSubStatus').textContent = text;
  $('hudSubLangPill').textContent = `CC • ${lang.badge}`;
}

async function persistCurrentSubCache(isFullyTranslated, translatedCount, { skipCloudPush = false } = {}) {
  if (!state.currentMovie || !state.currentEpisode || state.primaryCues.length < 3) return;
  const targetLang = resolveLanguage(state.prefs.primaryLang);
  const key = buildSubCacheKey(state.currentMovie.slug, state.currentEpisode, targetLang.code);
  const payload = {
    primaryLangCode: targetLang.code,
    secondaryLangCode: state.prefs.dualSub ? state.prefs.secondaryLang : '',
    sourceLangCode: state.lastSubSourceLang || 'auto',
    trackLabel: state.lastSubTrackLabel || `Sub ${targetLang.badge}`,
    sourceCues: state.sourceCues.length ? state.sourceCues : state.primaryCues,
    primaryCues: state.primaryCues,
    secondaryCues: state.secondaryCues,
    isFullyTranslated,
    translatedCount: translatedCount ?? state.primaryCues.length,
  };
  await saveCachedEpisodeSub(key, payload);

  // Auto-upload translated subtitle to Shared NKI Cloud Pool (Vercel Blob) so Android & TV load it in <150ms!
  if (!skipCloudPush && isCloudSignedIn()) {
    const now = Date.now();
    if (isFullyTranslated || now - state.lastCloudSubPushBatch >= 25000) {
      state.lastCloudSubPushBatch = now;
      cloudSaveSubtitle({
        movieSlug: state.currentMovie.slug,
        episodeNameOrSlug: state.currentEpisode.name || state.currentEpisode.slug,
        ...payload,
      }).catch(() => {});
    }
  }
}

async function autoLoadSubtitlesForMovie(movie, episode) {
  const runId = ++state.subTranslationRunId;
  state.subTracks = [];
  state.sourceCues = [];
  state.primaryCues = [];
  state.secondaryCues = [];
  renderActiveSubtitle(0);

  const targetLang = resolveLanguage(state.prefs.primaryLang);
  const cacheKey = buildSubCacheKey(movie.slug, episode, targetLang.code);

  // STEP 0A: INSTANT RESTORE FROM LOCAL INDEXEDDB (<10ms)
  let cached = await loadCachedEpisodeSub(cacheKey);
  let fromCloud = false;
  if (runId !== state.subTranslationRunId) return;

  // STEP 0B: SHARED NKI CLOUD SUBTITLE POOL ON VERCEL BLOB (<150ms, 0 GEMINI TOKENS!)
  if ((!cached || (cached.primaryCues?.length || 0) < 3) && isCloudSignedIn()) {
    updateSubStatusBadge(`☁️ Checking NKI Cloud Sub (${targetLang.badge})...`);
    const cloudBundle = await cloudFetchSubtitle(movie.slug, episode.name || episode.slug, targetLang.code);
    if (runId !== state.subTranslationRunId) return;
    if (cloudBundle && Array.isArray(cloudBundle.primaryCues) && cloudBundle.primaryCues.length >= 3) {
      cached = {
        ...cloudBundle,
        trackLabel: cloudBundle.sourcePlatform || `☁️ NKI Cloud ${targetLang.badge}`,
        translatedCount: cloudBundle.primaryCues.length,
      };
      fromCloud = true;
      // Save cloud subtitle to local IndexedDB for instant offline reuse next time
      saveCachedEpisodeSub(cacheKey, cached).catch(() => {});
    }
  }

  if (cached && cached.primaryCues?.length >= 3) {
    state.sourceCues = cached.sourceCues?.length ? cached.sourceCues : cached.primaryCues;
    state.primaryCues = cached.primaryCues;
    state.lastSubSourceLang = cached.sourceLangCode || 'auto';
    state.lastSubTrackLabel = cached.trackLabel || `Sub ${targetLang.badge}`;

    const wantSec = state.prefs.dualSub && state.prefs.secondaryLang !== 'off';
    if (wantSec && cached.secondaryCues?.length && cached.secondaryLangCode === state.prefs.secondaryLang) {
      state.secondaryCues = cached.secondaryCues;
    } else if (wantSec) {
      state.secondaryCues = state.sourceCues;
    }

    updateSubStatusBadge(
      fromCloud
        ? `☁️ Sub ${targetLang.badge} • NKI Cloud (${state.primaryCues.length})`
        : `💾 Sub ${targetLang.badge} • Saved (${state.primaryCues.length})`
    );
    renderActiveSubtitle(($('videoEl').currentTime || 0) * 1000);

    // Hydrate OpenSubtitles track list silently in background (non-blocking) for track switcher
    const isSeriesBg = movie.type !== 'single' || state.episodesFlat.length > 1;
    const epMatchBg = (episode.name || '').match(/\d+/);
    const epNumBg = epMatchBg ? Number(epMatchBg[0]) : 1;
    if (movie.imdbId) {
      fetchOpenSubtitlesV3(movie.imdbId, isSeriesBg, 1, epNumBg).then((tracks) => {
        if (state.currentEpisode?.link_m3u8 === episode.link_m3u8) {
          state.subTracks = tracks;
        }
      }).catch(() => {});
    }

    // Resume translating remaining batches ONLY if a previous session closed mid-translation
    if (!cached.isFullyTranslated && (cached.translatedCount || 0) < state.sourceCues.length) {
      await applyLoadedSourceCues(state.sourceCues, state.lastSubSourceLang, {
        resumeWorking: state.primaryCues.slice(),
        resumeStartIdx: cached.translatedCount || 0,
        trackLabel: state.lastSubTrackLabel,
      });
    } else if (wantSec && (!cached.secondaryCues?.length || cached.secondaryLangCode !== state.prefs.secondaryLang)) {
      await translateSecondaryInBackground(runId, state.sourceCues, state.lastSubSourceLang);
    }
    return;
  }

  const isSeries = movie.type !== 'single' || state.episodesFlat.length > 1;
  const epNumMatch = (episode.name || '').match(/\d+/);
  const epNum = epNumMatch ? Number(epNumMatch[0]) : 1;

  // Built-in episode subtitles (e.g. Internet Archive attached .SRT/.VTT tracks)
  const attachedSubs = Array.isArray(episode.subtitles) ? episode.subtitles : [];
  if (attachedSubs.length > 0) {
    state.subTracks = attachedSubs;
    await selectSubtitleTrack(attachedSubs[0]);
    return;
  }

  if (!movie.imdbId) {
    updateSubStatusBadge('Chưa có mã IMDb • Bấm CC để nạp file .SRT');
    return;
  }

  updateSubStatusBadge('Đang tìm phụ đề trên OpenSubtitles...');
  const tracks = await fetchOpenSubtitlesV3(movie.imdbId, isSeries, 1, epNum);
  if (runId !== state.subTranslationRunId) return;
  state.subTracks = tracks;
  if (!tracks.length) {
    updateSubStatusBadge('Không thấy Sub rời • Bấm CC để tải file .SRT lên');
    return;
  }

  const best =
    tracks.find((t) => t.langCode === targetLang.code) ||
    tracks.find((t) => t.langCode === 'en') ||
    tracks.find((t) => t.langCode === 'vi') ||
    tracks[0];

  await selectSubtitleTrack(best);
}

async function selectSubtitleTrack(track) {
  state.activeSubTrackId = track.id;
  state.lastSubTrackLabel = track.label || '';
  updateSubStatusBadge(`Đang tải ${track.label}...`);
  try {
    const res = await fetch(track.url);
    const raw = await res.text();
    const cues = parseSubtitleText(raw);
    if (!cues.length) {
      updateSubStatusBadge('File phụ đề rỗng — thử bản khác');
      return;
    }
    await applyLoadedSourceCues(cues, track.langCode, { trackLabel: track.label });
  } catch {
    updateSubStatusBadge('Không tải được file phụ đề (CORS) — thử nạp .SRT từ máy');
  }
}

async function translateSecondaryInBackground(runId, cues, sourceLangCode) {
  if (!state.prefs.dualSub || state.prefs.secondaryLang === 'off') {
    state.secondaryCues = [];
    return;
  }
  const secCode = state.prefs.secondaryLang;
  if (secCode === 'orig' || secCode === sourceLangCode) {
    state.secondaryCues = cues;
    await persistCurrentSubCache(true, cues.length);
    return;
  }
  const workingSec = cues.slice();
  for (let i = 0; i < cues.length; i += 40) {
    if (runId !== state.subTranslationRunId) return;
    const chunk = cues.slice(i, i + 40);
    const translated = await translateCueBatch({
      cues: chunk,
      targetLang: secCode,
      geminiApiKey: state.prefs.geminiApiKey,
      geminiModel: state.prefs.geminiModel,
      cloudTranslator: isCloudSignedIn() ? cloudTranslateCueBatch : null,
    });
    if (runId !== state.subTranslationRunId) return;
    for (let j = 0; j < translated.length; j++) workingSec[i + j] = translated[j];
    state.secondaryCues = workingSec.slice();
  }
  await persistCurrentSubCache(true, cues.length);
}

async function applyLoadedSourceCues(cues, sourceLangCode = 'auto', opts = {}) {
  const runId = ++state.subTranslationRunId;
  state.sourceCues = cues;
  state.lastSubSourceLang = sourceLangCode;
  if (opts.trackLabel) state.lastSubTrackLabel = opts.trackLabel;

  const targetLang = resolveLanguage(state.prefs.primaryLang);

  // If switching language without explicit resumeWorking, check IndexedDB cache first!
  if (!opts.resumeWorking && !opts.skipCacheLookup && state.currentMovie && state.currentEpisode) {
    const cacheKey = buildSubCacheKey(state.currentMovie.slug, state.currentEpisode, targetLang.code);
    const cached = await loadCachedEpisodeSub(cacheKey);
    if (runId !== state.subTranslationRunId) return;
    if (cached && cached.primaryCues?.length === cues.length && cached.isFullyTranslated) {
      state.primaryCues = cached.primaryCues;
      if (state.prefs.dualSub && cached.secondaryCues?.length && cached.secondaryLangCode === state.prefs.secondaryLang) {
        state.secondaryCues = cached.secondaryCues;
      } else {
        state.secondaryCues = state.prefs.dualSub ? cues : [];
      }
      updateSubStatusBadge(`💾 Sub ${targetLang.badge} • Đã lưu (${cues.length} câu)`);
      renderActiveSubtitle(($('videoEl').currentTime || 0) * 1000);
      if (state.prefs.dualSub && state.prefs.secondaryLang !== 'off' && cached.secondaryLangCode !== state.prefs.secondaryLang) {
        await translateSecondaryInBackground(runId, cues, sourceLangCode);
      }
      return;
    }
  }

  state.primaryCues = opts.resumeWorking ? opts.resumeWorking.slice() : cues;
  state.secondaryCues = state.prefs.dualSub ? cues : [];

  if (sourceLangCode === targetLang.code) {
    updateSubStatusBadge(`Sub ${targetLang.badge} Gốc (${cues.length} câu)`);
    renderActiveSubtitle(($('videoEl').currentTime || 0) * 1000);
    await persistCurrentSubCache(true, cues.length);
  } else {
    updateSubStatusBadge(`AI đang dịch sang ${targetLang.name}...`);
    const working = opts.resumeWorking ? opts.resumeWorking.slice() : cues.slice();
    const startIdx = Math.max(0, opts.resumeStartIdx || 0);
    const batchSize = 35;
    for (let i = startIdx; i < cues.length; i += batchSize) {
      if (runId !== state.subTranslationRunId) return;
      const chunk = cues.slice(i, i + batchSize);
      const translated = await translateCueBatch({
        cues: chunk,
        targetLang: targetLang.code,
        geminiApiKey: state.prefs.geminiApiKey,
        geminiModel: state.prefs.geminiModel,
        cloudTranslator: isCloudSignedIn() ? cloudTranslateCueBatch : null,
      });
      if (runId !== state.subTranslationRunId) return;
      for (let j = 0; j < translated.length; j++) {
        working[i + j] = translated[j];
      }
      state.primaryCues = working.slice();
      const doneCount = Math.min(cues.length, i + batchSize);
      const isDone = doneCount >= cues.length;
      const pct = Math.min(99, Math.round((doneCount / cues.length) * 100));
      updateSubStatusBadge(isDone ? `💾 Sub ${targetLang.badge} AI (${cues.length} câu)` : `Sub ${targetLang.badge} AI (${pct}%)`);
      renderActiveSubtitle(($('videoEl').currentTime || 0) * 1000);
      await persistCurrentSubCache(isDone, doneCount);
    }
    updateSubStatusBadge(`💾 Sub ${targetLang.badge} AI (${cues.length} câu • Đã lưu)`);
    await persistCurrentSubCache(true, cues.length);
  }

  // Secondary subtitle language translation if Dual Sub is ON and differs from source
  await translateSecondaryInBackground(runId, cues, sourceLangCode);
}

function renderActiveSubtitle(currentMs) {
  const pill = $('subPill');
  if (!state.prefs.subEnabled) {
    pill.innerHTML = '';
    return;
  }
  const effectiveMs = currentMs - state.prefs.syncOffsetMs;
  const pText = findActiveCueText(state.primaryCues, effectiveMs);
  const sText = state.prefs.dualSub && state.prefs.secondaryLang !== 'off'
    ? findActiveCueText(state.secondaryCues, effectiveMs)
    : '';

  if (!pText && !sText) {
    pill.innerHTML = '';
    return;
  }

  pill.style.background = `rgba(6, 8, 14, ${state.prefs.boxOpacity})`;
  let html = '';
  if (pText) {
    html += `<div class="sub-primary" style="font-size:${state.prefs.primarySize}px;color:${state.prefs.primaryColor}">${escapeHtml(pText).replace(/\n/g, '<br>')}</div>`;
  }
  if (sText && sText !== pText) {
    html += `<div class="sub-secondary" style="font-size:${state.prefs.secondarySize}px;color:${state.prefs.secondaryColor}">${escapeHtml(sText).replace(/\n/g, '<br>')}</div>`;
  }
  pill.innerHTML = html;
}

/* ==================== OPTIONAL SECONDARY TV STREAM / CAST ==================== */
async function castCurrentToTv(movie, episode, startSec = 0) {
  const ip = (state.prefs.tvIp || '').trim();
  if (!ip) {
    openTvCastModal();
    showToast('Nhập địa chỉ IP của ChillFilm TV (cùng mạng Wi-Fi) để truyền phim!');
    return;
  }
  const base = ip.startsWith('http') ? ip : `http://${ip}${ip.includes(':') ? '' : ':8899'}`;
  try {
    await fetch(`${base}/api/action`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'play_payload',
        movieId: movie.slug,
        movieTitle: movie.name,
        originalTitle: movie.origin_name || '',
        year: movie.year || '',
        posterUrl: movie.poster || '',
        backdropUrl: movie.backdrop || '',
        description: movie.content || '',
        episodeId: episode.slug || episode.name,
        episodeName: episode.name,
        streamUrl: episode.link_m3u8,
        startPositionMs: Math.floor(startSec * 1000),
      }),
    });
    showToast(`📡 Đã truyền "${movie.name}" lên TV (${ip})!`);
  } catch {
    showToast('Không kết nối được TV LAN (Kiểm tra TV cùng Wi-Fi & mở app ChillFilm).');
    openTvCastModal();
  }
}

async function sendTvRemoteKey(key) {
  const ip = (state.prefs.tvIp || '').trim();
  if (!ip) return showToast('Vui lòng nhập IP TV trước!');
  const base = ip.startsWith('http') ? ip : `http://${ip}${ip.includes(':') ? '' : ':8899'}`;
  try {
    await fetch(`${base}/api/touch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'key', key }),
    });
  } catch {
    showToast('Không gửi được lệnh tới TV (kiểm tra IP LAN / HTTP).');
  }
}

async function syncGeminiKeyToTv() {
  const ip = (state.prefs.tvIp || '').trim();
  if (!ip) return showToast('Nhập IP TV trước!');
  if (!state.prefs.geminiApiKey) return showToast('Hãy lưu Gemini API Key trong Cài đặt trước!');
  const base = ip.startsWith('http') ? ip : `http://${ip}${ip.includes(':') ? '' : ':8899'}`;
  try {
    await fetch(`${base}/api/action`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'set_gemini_key',
        apiKey: state.prefs.geminiApiKey,
        modelId: state.prefs.geminiModel,
      }),
    });
    showToast('🔐 Đã đồng bộ Gemini API Key sang RAM của TV!');
  } catch {
    showToast('Không kết nối được tới TV LAN.');
  }
}

/* ==================== MODALS & UI BINDINGS ==================== */
function openSubModal() {
  renderLanguagePickers();
  $('subSizeRange').value = state.prefs.primarySize;
  $('subSizeLabel').textContent = `${state.prefs.primarySize}px`;
  $('secSizeRange').value = state.prefs.secondarySize;
  $('secSizeLabel').textContent = `${state.prefs.secondarySize}px`;
  $('subColorInput').value = state.prefs.primaryColor;
  $('secColorInput').value = state.prefs.secondaryColor;
  $('dualSubToggle').checked = state.prefs.dualSub;
  $('subEnableToggle').checked = state.prefs.subEnabled;
  $('syncValLabel').textContent = `${(state.prefs.syncOffsetMs / 1000).toFixed(1)}s`;
  $('subModal').classList.add('open');
}

function renderLanguagePickers() {
  const pWrap = $('primaryLangChips');
  pWrap.innerHTML = '';
  SUBTITLE_LANGUAGES.forEach((l) => {
    const b = document.createElement('button');
    b.className = `pill-choice ${state.prefs.primaryLang === l.code ? 'active' : ''}`;
    b.textContent = `${l.badge} • ${l.name}`;
    b.onclick = () => {
      state.prefs.primaryLang = l.code;
      savePrefs();
      renderLanguagePickers();
      if (state.sourceCues.length) applyLoadedSourceCues(state.sourceCues, 'auto');
      updateSubStatusBadge(`Đã đổi sang ${l.name}`);
    };
    pWrap.appendChild(b);
  });

  const sWrap = $('secondaryLangChips');
  sWrap.innerHTML = '';
  [{ code: 'orig', badge: 'GỐC', name: 'Ngôn ngữ gốc' }, ...SUBTITLE_LANGUAGES.slice(0, 10)].forEach((l) => {
    const b = document.createElement('button');
    b.className = `pill-choice ${state.prefs.secondaryLang === l.code ? 'active-violet' : ''}`;
    b.textContent = `${l.badge} • ${l.name}`;
    b.onclick = () => {
      state.prefs.secondaryLang = l.code;
      state.prefs.dualSub = true;
      $('dualSubToggle').checked = true;
      savePrefs();
      renderLanguagePickers();
      if (state.sourceCues.length) applyLoadedSourceCues(state.sourceCues, 'auto');
    };
    sWrap.appendChild(b);
  });

  const trackSelect = $('subTrackSelect');
  trackSelect.innerHTML = state.subTracks.length
    ? state.subTracks.map((t) => `<option value="${t.id}" ${t.id === state.activeSubTrackId ? 'selected' : ''}>${escapeHtml(t.label)}</option>`).join('')
    : '<option value="">Chưa có bản Sub online — hãy tải file .SRT từ máy</option>';
}

function openMoreModal() {
  applyVolumeBoost(state.volumeScale);
  applyEqPreset(state.eqPreset);
  $('moreModal').classList.add('open');
}

function updateCloudHeaderBadge() {
  const session = getCloudSession();
  state.cloudSession = session;
  const btn = $('openCloudBtn');
  const txt = $('cloudBtnText');
  if (btn && txt) {
    if (session?.user) {
      btn.classList.add('connected');
      txt.textContent = session.user.username.slice(0, 12);
    } else {
      btn.classList.remove('connected');
      txt.textContent = 'Cloud';
    }
  }
  if ($('aiDot')) {
    $('aiDot').style.display = (session?.serverAiReady || state.prefs.geminiApiKey) ? 'block' : 'none';
  }
}

async function renderCloudCard() {
  updateCloudHeaderBadge();
  const session = getCloudSession();
  const statusEl = $('cloudServerStatus');
  const roleBadge = $('cloudRoleBadge');
  const authWrap = $('cloudAuthFormWrap');
  const connWrap = $('cloudConnectedWrap');
  const adminDetails = $('adminVaultDetails');

  // Check health in background to detect Bootstrap Admin / Server AI readiness
  fetchCloudHealth().then((h) => {
    state.cloudHealth = h;
    if (statusEl) {
      if (!h.ok) {
        statusEl.textContent = '⚠️ Cloud server unreachable';
      } else if (session?.user) {
        statusEl.textContent = h.serverAiReady
          ? '🟢 Server Gemini AI Active • Auto-Sync Neon + Blob'
          : '🟡 Cloud Sync Active • Admin needs to set Gemini API Key';
      } else if (!h.hasAdmin) {
        statusEl.textContent = '👑 First user to create an account becomes ADMIN';
      } else {
        statusEl.textContent = h.registrationMode === 'INVITE_ONLY'
          ? '🔒 Sign in or register with an Admin Invite Code'
          : '🟢 Sign in to auto-sync history, AI subs & centralized Gemini';
      }
    }
    syncCloudAuthFormFields();
  }).catch(() => {
    if (statusEl) statusEl.textContent = 'Offline / Local Mode';
  });

  if (session?.user) {
    if (authWrap) authWrap.style.display = 'none';
    if (connWrap) connWrap.style.display = 'block';
    if (roleBadge) {
      roleBadge.style.display = 'inline-block';
      roleBadge.textContent = session.user.role || 'MEMBER';
    }
    if ($('cloudProfileName')) {
      $('cloudProfileName').textContent = `${session.user.displayName || session.user.username} (@${session.user.username})`;
    }
    const used = session.user.todayUsage?.requests ?? 0;
    const lim = session.user.dailyAiLimit;
    const quotaText = session.user.role === 'ADMIN' || !lim ? `${used} / ∞ AI today` : `${used} / ${lim} AI today`;
    if ($('cloudQuotaSummary')) {
      $('cloudQuotaSummary').textContent = `${session.serverAiReady ? '🟢 Centralized AI Active' : '⚠️ AI Key Pending'} • ${quotaText}`;
    }
    if (adminDetails) {
      adminDetails.style.display = session.user.role === 'ADMIN' ? 'block' : 'none';
      if (session.user.role === 'ADMIN' && adminDetails.open) {
        loadAdminVaultPanel();
      }
    }
  } else {
    if (authWrap) authWrap.style.display = 'block';
    if (connWrap) connWrap.style.display = 'none';
    if (roleBadge) roleBadge.style.display = 'none';
    if (adminDetails) adminDetails.style.display = 'none';
    syncCloudAuthFormFields();
  }
}

function syncCloudAuthFormFields() {
  const isReg = state.cloudAuthMode === 'register';
  $('cloudTabLogin')?.classList.toggle('active', !isReg);
  $('cloudTabRegister')?.classList.toggle('active', isReg);
  if ($('cloudDisplayNameInput')) $('cloudDisplayNameInput').style.display = isReg ? 'block' : 'none';
  const needInvite = isReg && state.cloudHealth?.hasAdmin && state.cloudHealth?.registrationMode === 'INVITE_ONLY';
  if ($('cloudInviteInput')) $('cloudInviteInput').style.display = needInvite ? 'block' : 'none';
  const hint = $('cloudBootstrapHint');
  if (hint) {
    if (isReg && state.cloudHealth && !state.cloudHealth.hasAdmin) {
      hint.style.display = 'block';
      hint.textContent = '👑 No Admin exists yet — creating this account will automatically make you the permanent ADMIN and lock sign-ups to Invite-Only!';
    } else if (needInvite) {
      hint.style.display = 'block';
      hint.textContent = '🔒 Admin enabled Invite-Only mode. Enter the NKI-XXXX-XXXX code shared by the Admin.';
    } else {
      hint.style.display = 'none';
    }
  }
  if ($('cloudAuthSubmitBtn')) {
    $('cloudAuthSubmitBtn').textContent = isReg ? '✨ Create NKI Account & Sync' : '☁️ Sign In & Auto-Sync';
  }
}

async function loadAdminVaultPanel() {
  const box = $('adminVaultStatusBox');
  if (!box) return;
  try {
    const data = await cloudFetchAdminConfig();
    const cfg = data.config || {};
    box.innerHTML = [
      `🔑 <strong>Vercel Env Key:</strong> ${cfg.hasEnvKey ? '✅ Configured' : '❌ Not set'}`,
      `🔐 <strong>Admin Vault Key:</strong> ${cfg.hasVaultKey ? `✅ Override (${escapeHtml(cfg.maskedVaultKey)})` : 'Using Vercel Env'}`,
      `☁️ <strong>Shared AI Subtitles Cached:</strong> ${cfg.cachedSubtitlesCount || 0} episodes`,
    ].join('<br>');

    if (cfg.defaultModel) {
      state.adminGlobalModel = cfg.defaultModel;
    }
    populateModelSelects();
    if ($('adminRegModeSelect') && cfg.registrationMode) {
      $('adminRegModeSelect').value = cfg.registrationMode;
    }
    if (!state.liveModels.length) {
      loadLiveGeminiModels({ silent: true });
    }

    const invWrap = $('adminInvitesList');
    if (invWrap) {
      invWrap.innerHTML = (data.invites || []).length
        ? data.invites.map((inv) => `
            <div class="admin-row">
              <div>
                <div style="font-weight:800;color:${inv.active ? '#86efac' : '#9ca3af'};">${escapeHtml(inv.code)} ${inv.active ? '' : '(Revoked)'}</div>
                <div style="font-size:11px;color:var(--muted);">${escapeHtml(inv.label)} • Used ${inv.usedCount}/${inv.maxUses} • ${inv.dailyAiLimit}/day</div>
              </div>
              <div style="display:flex;gap:5px;">
                <button class="btn-glass" data-copy-invite="${escapeHtml(inv.code)}" style="padding:4px 8px;font-size:11px;">Copy</button>
                ${inv.active ? `<button class="btn-glass" data-revoke-invite="${escapeHtml(inv.code)}" style="padding:4px 8px;font-size:11px;color:#fca5a5;">Revoke</button>` : ''}
              </div>
            </div>
          `).join('')
        : '<div style="font-size:11.5px;color:var(--muted);">No invite codes yet. Click "+ New Invite Code" to invite family/devices.</div>';

      invWrap.querySelectorAll('[data-copy-invite]').forEach((btn) => {
        btn.onclick = () => {
          navigator.clipboard?.writeText(btn.dataset.copyInvite).catch(() => {});
          showToast(`📋 Copied Invite Code: ${btn.dataset.copyInvite}`);
        };
      });
      invWrap.querySelectorAll('[data-revoke-invite]').forEach((btn) => {
        btn.onclick = async () => {
          await cloudAdminAction({ action: 'revokeInvite', code: btn.dataset.revokeInvite });
          showToast(`Revoked ${btn.dataset.revokeInvite}`);
          loadAdminVaultPanel();
        };
      });
    }

    const usrWrap = $('adminUsersList');
    if (usrWrap) {
      usrWrap.innerHTML = (data.users || []).map((u) => `
        <div class="admin-row">
          <div>
            <div style="font-weight:700;">@${escapeHtml(u.username)} <span style="font-size:10px;color:#86efac;">${escapeHtml(u.role)}</span></div>
            <div style="font-size:11px;color:var(--muted);">Status: ${escapeHtml(u.status)} • Today AI: ${u.todayUsage?.requests || 0}/${u.dailyAiLimit || '∞'}</div>
          </div>
          ${u.role !== 'ADMIN' ? `
            <button class="btn-glass" data-toggle-user="${escapeHtml(u.id)}" data-next-status="${u.status === 'ACTIVE' ? 'REVOKED' : 'ACTIVE'}" style="padding:4px 8px;font-size:11px;color:${u.status === 'ACTIVE' ? '#fca5a5' : '#86efac'};">
              ${u.status === 'ACTIVE' ? 'Revoke' : 'Restore'}
            </button>
          ` : ''}
        </div>
      `).join('');

      usrWrap.querySelectorAll('[data-toggle-user]').forEach((btn) => {
        btn.onclick = async () => {
          await cloudAdminAction({
            action: 'updateUser',
            userId: btn.dataset.toggleUser,
            status: btn.dataset.nextStatus,
          });
          showToast(`Updated member status -> ${btn.dataset.nextStatus}`);
          loadAdminVaultPanel();
        };
      });
    }

    // Auto-Crawler Status Box & Dynamic Global Movie Sources with Live Health Badges
    const crawlerBox = $('adminCrawlerStatusBox');
    if (crawlerBox) {
      const cs = data.crawlerStats || {};
      const lr = cs.lastRun;
      const lastRunStr = lr?.finishedAt
        ? `${new Date(lr.finishedAt).toLocaleString()} (${lr.triggerType}: +${lr.itemsNew} new, ${lr.sourcesHealthy}/${lr.sourcesChecked} online, ${lr.itemsBlockedSfw} SFW blocked)`
        : 'Waiting for first crawl — click "🤖 Crawl & Check All Now" to seed immediately!';
      crawlerBox.innerHTML = [
        `⏰ <strong>Auto-Schedule:</strong> ${escapeHtml(cs.schedule || 'Every 3h (VN)')} • <strong>Indexed Films:</strong> ${Number(cs.totalIndexed || 0)} • <strong>Healthy Sources:</strong> ${Number(cs.healthySources || 0)}/${Number(cs.totalSources || 0)}`,
        `📡 <strong>Last Run:</strong> ${escapeHtml(lastRunStr)}`,
      ].join('<br>');
    }

    const srcWrap = $('adminSourcesList');
    if (srcWrap) {
      const sources = Array.isArray(data.sources) ? data.sources : [];
      const fmtRemote = (n) => (n >= 1000 ? `${Math.round(n / 1000)}k catalog` : (n > 0 ? `${n} items` : ''));
      srcWrap.innerHTML = sources.length
        ? sources.map((s) => {
            const st = s.lastStatus || 'PENDING';
            const dot = st === 'ONLINE' ? '🟢' : st === 'DEGRADED' ? '🟡' : st === 'OFFLINE' ? '🔴' : '⚪';
            const lat = s.lastLatencyMs > 0 ? `${s.lastLatencyMs}ms` : '';
            const remoteStr = fmtRemote(s.totalRemote);
            const crawledStr = s.itemsCrawled > 0 ? `${s.itemsCrawled} crawled` : '';
            const metaParts = [dot + ' ' + st, lat, crawledStr, remoteStr, s.lastError ? `⚠️ ${s.lastError}` : ''].filter(Boolean).join(' • ');
            return `
            <div class="admin-row">
              <div style="min-width:0;flex:1;">
                <div style="font-weight:800;color:${s.enabled ? '#86efac' : '#9ca3af'};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                  ${escapeHtml(s.name)}
                  <span style="font-size:10px;opacity:.8;">[${escapeHtml(s.sourceType)}]</span>
                  ${s.isBuiltin ? '<span style="font-size:9.5px;background:rgba(0,200,83,0.18);color:#86efac;padding:1px 5px;border-radius:4px;margin-left:4px;">VERIFIED</span>' : ''}
                </div>
                <div style="font-size:10.5px;color:#cbd5e1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(metaParts)}</div>
                <div style="font-size:10px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(s.baseUrl)}</div>
              </div>
              <div style="display:flex;gap:5px;flex-shrink:0;">
                <button class="btn-glass" data-toggle-src="${escapeHtml(s.id)}" data-src-next="${s.enabled ? '0' : '1'}" style="padding:4px 8px;font-size:11px;">${s.enabled ? 'Pause' : 'Enable'}</button>
                ${s.isBuiltin ? '' : `<button class="btn-glass" data-del-src="${escapeHtml(s.id)}" style="padding:4px 8px;font-size:11px;color:#fca5a5;">Del</button>`}
              </div>
            </div>
          `;
          }).join('')
        : '<div style="font-size:11.5px;color:var(--muted);">Built-in verified sources will auto-seed on next crawl.</div>';

      srcWrap.querySelectorAll('[data-toggle-src]').forEach((btn) => {
        btn.onclick = async () => {
          await cloudAdminAction({
            action: 'toggleSource',
            id: btn.dataset.toggleSrc,
            enabled: btn.dataset.srcNext === '1',
          });
          showToast('Updated movie source status');
          loadAdminVaultPanel();
        };
      });
      srcWrap.querySelectorAll('[data-del-src]').forEach((btn) => {
        btn.onclick = async () => {
          await cloudAdminAction({ action: 'deleteSource', id: btn.dataset.delSrc });
          showToast('Deleted movie source');
          loadAdminVaultPanel();
        };
      });
    }

    const histWrap = $('adminCrawlHistoryList');
    if (histWrap) {
      const runs = Array.isArray(data.crawlRuns) ? data.crawlRuns.slice(0, 5) : [];
      histWrap.innerHTML = runs.length
        ? `<div style="font-size:10.5px;color:var(--muted);font-weight:700;margin-bottom:4px;">RECENT AUTO-CRAWL LOGS</div>` +
          runs.map((r) => `
            <div class="admin-row" style="padding:5px 8px;">
              <div style="font-size:11px;color:#e2e8f0;">
                ${r.triggerType === 'SCHEDULE' ? '⏰' : r.triggerType === 'ADMIN_MANUAL' ? '👆' : '🚀'}
                <strong>${escapeHtml(r.triggerType)}</strong> • ${new Date(r.finishedAt).toLocaleString()}
              </div>
              <div style="font-size:10.5px;color:#86efac;white-space:nowrap;">
                +${r.itemsNew} new • 🟢${r.sourcesHealthy}/${r.sourcesChecked} • 🛡️${r.itemsBlockedSfw}
              </div>
            </div>
          `).join('')
        : '';
    }

    if ($('adminBlockedTermsInput') && Array.isArray(cfg.customBlockedTerms)) {
      $('adminBlockedTermsInput').value = cfg.customBlockedTerms.join(', ');
    }
  } catch (err) {
    box.textContent = `Admin Vault error: ${err.message}`;
  }
}

function populateModelSelects() {
  const userSelect = $('geminiModelSelect');
  const adminSelect = $('adminDefaultModelSelect');
  const statusEl = $('geminiModelsStatus');
  const curUserModel = state.prefs.geminiModel || getCloudSession()?.defaultModel || 'gemini-3-flash-preview';
  const curAdminModel = state.adminGlobalModel || getCloudSession()?.defaultModel || curUserModel;

  const list = Array.isArray(state.liveModels) && state.liveModels.length
    ? state.liveModels.slice()
    : [
        { id: curUserModel, displayName: `${curUserModel} (Click Load Models)` },
      ];

  const ensurePresent = (arr, id) => {
    if (id && !arr.some((m) => m.id === id)) {
      arr.unshift({ id, displayName: `${id} (Selected)` });
    }
  };

  if (userSelect) {
    const uList = list.slice();
    ensurePresent(uList, curUserModel);
    userSelect.innerHTML = uList
      .map((m) => {
        const label = m.displayName && m.displayName !== m.id ? `${m.id} — ${m.displayName}` : m.id;
        return `<option value="${escapeHtml(m.id)}" ${m.id === curUserModel ? 'selected' : ''}>${escapeHtml(label)}</option>`;
      })
      .join('');
    userSelect.value = curUserModel;
  }

  if (adminSelect) {
    const aList = list.slice();
    ensurePresent(aList, curAdminModel);
    adminSelect.innerHTML = aList
      .map((m) => {
        const label = m.displayName && m.displayName !== m.id ? `${m.id} — ${m.displayName}` : m.id;
        return `<option value="${escapeHtml(m.id)}" ${m.id === curAdminModel ? 'selected' : ''}>${escapeHtml(label)}</option>`;
      })
      .join('');
    adminSelect.value = curAdminModel;
  }

  if (statusEl) {
    if (state.modelsLoading) {
      statusEl.textContent = '⏳ Loading live models from Google Gemini API...';
    } else if (state.liveModels.length) {
      statusEl.textContent = `✅ Loaded ${state.liveModels.length} live models from Gemini API`;
    } else if (isCloudSignedIn() || state.prefs.geminiApiKey) {
      statusEl.textContent = 'Click "🔄 Load Models" to fetch live models from Gemini API';
    } else {
      statusEl.textContent = 'Sign in to NKI Cloud (or enter personal key) to load live models';
    }
  }
}

async function loadLiveGeminiModels({ silent = false } = {}) {
  const personalKey = ($('geminiKeyInput')?.value || state.prefs.geminiApiKey || '').trim();
  if (!isCloudSignedIn() && personalKey.length < 15) {
    populateModelSelects();
    if (!silent) showToast('Sign in to NKI Cloud or paste a Gemini API Key to load models');
    return false;
  }
  state.modelsLoading = true;
  populateModelSelects();
  try {
    const data = await cloudFetchLiveModels(isCloudSignedIn() ? '' : personalKey);
    if (Array.isArray(data?.models) && data.models.length > 0) {
      state.liveModels = data.models;
      saveCachedLiveModels(data.models);
      if (data.defaultModel && !state.adminGlobalModel) {
        state.adminGlobalModel = data.defaultModel;
      }
      // If saved model is not in the live list and user never chose a live one, keep selection valid
      state.modelsLoading = false;
      populateModelSelects();
      if (!silent) showToast(`✅ Loaded ${data.models.length} live Gemini models!`);
      return true;
    }
  } catch (err) {
    if (!silent) showToast(`Load models failed: ${err.message}`);
  }
  state.modelsLoading = false;
  populateModelSelects();
  return false;
}

function openSettingsModal() {
  $('geminiKeyInput').value = state.prefs.geminiApiKey || '';
  if ($('uiLangSelect')) {
    const curUi = resolveUiLang(state.prefs.uiLang);
    $('uiLangSelect').innerHTML = UI_LANGUAGES.map(
      (l) => `<option value="${l.code}" ${l.code === curUi ? 'selected' : ''}>${l.badge} — ${l.name}</option>`
    ).join('');
  }
  $('defaultLangSelect').innerHTML = SUBTITLE_LANGUAGES.map(
    (l) => `<option value="${l.code}" ${l.code === state.prefs.primaryLang ? 'selected' : ''}>${l.badge} — ${l.name} (${l.native})</option>`
  ).join('');
  populateModelSelects();
  renderCloudCard();
  if (isCloudSignedIn() || (state.prefs.geminiApiKey || '').trim().length >= 15) {
    loadLiveGeminiModels({ silent: true });
  }
  $('settingsModal').classList.add('open');
}

function openTvCastModal() {
  $('tvIpInput').value = state.prefs.tvIp || '';
  $('apkDownloadBtn').href = APK_RELEASE_URL;
  $('tvModal').classList.add('open');
}

function escapeHtml(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showGestureToast(text) {
  const g = $('gestureToast');
  g.textContent = text;
  g.style.display = 'block';
  clearTimeout(g._t);
  g._t = setTimeout(() => { g.style.display = 'none'; }, 900);
}

/* ==================== BOOTSTRAP & EVENT WIRING ==================== */
function init() {
  applyUiTranslations();
  renderCategories();
  loadCategory('hot');

  // Netflix transparent-to-solid sticky navbar on scroll
  const syncTopbarScroll = () => {
    $('topbar')?.classList.toggle('scrolled', window.scrollY > 24);
  };
  window.addEventListener('scroll', syncTopbarScroll, { passive: true });
  syncTopbarScroll();

  // Topbar Language Switcher Dropdown (EN / VI / TH / ZH / KO)
  $('langSwitchBtn').onclick = (e) => {
    e.stopPropagation();
    const isOpen = $('langMenu').classList.toggle('open');
    $('langSwitchBtn').classList.toggle('open', isOpen);
  };
  document.addEventListener('click', () => {
    $('langMenu')?.classList.remove('open');
    $('langSwitchBtn')?.classList.remove('open');
  });

  // Topbar & Search + All 36 Countries Picker
  $('brandHome').onclick = () => {
    if ($('countryPickerSelect')) $('countryPickerSelect').value = '';
    loadCategory('hot');
  };
  $('countryPickerSelect')?.addEventListener('change', (e) => {
    const slug = e.target.value;
    if (!slug) {
      loadCategory('hot');
    } else {
      $('searchInputDesktop').value = '';
      $('searchInputMobile').value = '';
      loadCategory(slug);
    }
  });
  $('searchBtnDesktop').onclick = () => executeSearch($('searchInputDesktop').value);
  $('searchInputDesktop').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') executeSearch(e.target.value);
  });
  $('mobileSearchToggle').onclick = () => {
    $('mobileSearchBar').classList.toggle('open');
    if ($('mobileSearchBar').classList.contains('open')) $('searchInputMobile').focus();
  };
  $('searchBtnMobile').onclick = () => {
    executeSearch($('searchInputMobile').value);
    $('mobileSearchBar').classList.remove('open');
  };
  $('searchInputMobile').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      executeSearch(e.target.value);
      $('mobileSearchBar').classList.remove('open');
    }
  });

  $('openCloudBtn')?.addEventListener('click', openSettingsModal);
  $('openCastBtn').onclick = openTvCastModal;
  $('openSettingsBtn').onclick = openSettingsModal;
  $('refreshBtn').onclick = () => loadCategory(state.activeCategory, { forceRefresh: true });

  // Live Gemini Model Loader & Instant Picker
  $('loadModelsBtn')?.addEventListener('click', () => loadLiveGeminiModels({ silent: false }));
  $('adminLoadModelsBtn')?.addEventListener('click', () => loadLiveGeminiModels({ silent: false }));
  $('geminiModelSelect')?.addEventListener('change', (e) => {
    const chosen = e.target.value;
    if (chosen) {
      state.prefs.geminiModel = chosen;
      savePrefs();
      showToast(`✨ Active AI Model: ${chosen}`);
    }
  });

  // NKI Cloud Sync & Admin Vault Bindings
  $('cloudTabLogin')?.addEventListener('click', () => {
    state.cloudAuthMode = 'login';
    syncCloudAuthFormFields();
  });
  $('cloudTabRegister')?.addEventListener('click', () => {
    state.cloudAuthMode = 'register';
    syncCloudAuthFormFields();
  });
  $('cloudAuthSubmitBtn')?.addEventListener('click', async () => {
    const username = $('cloudUsernameInput')?.value.trim() || '';
    const password = $('cloudPasswordInput')?.value || '';
    const displayName = $('cloudDisplayNameInput')?.value.trim() || '';
    const inviteCode = $('cloudInviteInput')?.value.trim() || '';
    if (!username || !password) return showToast('Enter username and password');
    const btn = $('cloudAuthSubmitBtn');
    const prevTxt = btn.textContent;
    btn.disabled = true;
    btn.textContent = '⏳ Connecting...';
    try {
      if (state.cloudAuthMode === 'register') {
        await cloudRegister({ username, password, displayName, inviteCode });
        showToast('👑 NKI Cloud account created & synced!');
      } else {
        await cloudLogin(username, password);
        showToast('☁️ Signed into NKI Cloud! Auto-syncing...');
      }
      if ($('cloudPasswordInput')) $('cloudPasswordInput').value = '';
      await pullCloudStateAndMerge({ silent: true, force: true });
      renderCloudCard();
      loadLiveGeminiModels({ silent: true });
    } catch (err) {
      showToast(err.message || 'Authentication failed');
    } finally {
      btn.disabled = false;
      btn.textContent = prevTxt;
    }
  });
  $('cloudSyncNowBtn')?.addEventListener('click', async () => {
    flushPendingCloudProgress(false);
    await cloudRefreshMe().catch(() => {});
    await pullCloudStateAndMerge({ silent: false, force: true });
    renderCloudCard();
  });
  $('cloudLogoutBtn')?.addEventListener('click', () => {
    clearCloudSession();
    renderCloudCard();
    showToast('Signed out of NKI Cloud');
  });
  $('adminVaultDetails')?.addEventListener('toggle', () => {
    if ($('adminVaultDetails').open) loadAdminVaultPanel();
  });
  $('adminSaveVaultBtn')?.addEventListener('click', async () => {
    const keyVal = $('adminVaultKeyInput')?.value.trim() || '';
    if (!keyVal) return showToast('Paste a Gemini API Key first');
    try {
      await cloudAdminAction({ action: 'updateConfig', geminiApiKey: keyVal });
      $('adminVaultKeyInput').value = '';
      showToast('🔐 Server Gemini API Key encrypted & updated!');
      loadAdminVaultPanel();
      loadLiveGeminiModels({ silent: true });
    } catch (err) {
      showToast(err.message || 'Failed to save key');
    }
  });
  $('adminSavePolicyBtn')?.addEventListener('click', async () => {
    try {
      await cloudAdminAction({
        action: 'updateConfig',
        defaultModel: $('adminDefaultModelSelect')?.value,
        registrationMode: $('adminRegModeSelect')?.value,
      });
      showToast('✅ Global AI model & access policy updated!');
      loadAdminVaultPanel();
    } catch (err) {
      showToast(err.message || 'Policy update failed');
    }
  });
  $('adminCreateInviteBtn')?.addEventListener('click', async () => {
    try {
      const res = await cloudAdminAction({
        action: 'createInvite',
        label: 'NKI Member',
        maxUses: 5,
        dailyAiLimit: 150,
      });
      const code = res?.invite?.code;
      if (code) {
        navigator.clipboard?.writeText(code).catch(() => {});
        showToast(`🎟 Created & Copied Invite Code: ${code}`);
      }
      loadAdminVaultPanel();
    } catch (err) {
      showToast(err.message || 'Invite creation failed');
    }
  });
  $('adminRunCrawlBtn')?.addEventListener('click', async () => {
    const btn = $('adminRunCrawlBtn');
    const prevTxt = btn.textContent;
    btn.disabled = true;
    btn.textContent = '⏳ Checking & Crawling...';
    try {
      const res = await cloudAdminAction({ action: 'runCrawlNow' });
      const rep = res?.report || {};
      showToast(`🤖 Crawled ${rep.itemsDiscovered || 0} films (+${rep.itemsNew || 0} new) • 🟢 ${rep.sourcesHealthy || 0}/${rep.sourcesChecked || 0} online!`);
      await loadAdminVaultPanel();
      loadCategory('hot', { forceRefresh: true, silent: true });
    } catch (err) {
      showToast(err.message || 'Auto-crawl failed');
    } finally {
      btn.disabled = false;
      btn.textContent = prevTxt;
    }
  });
  $('adminAddSourceBtn')?.addEventListener('click', async () => {
    const name = $('adminSourceNameInput')?.value.trim() || '';
    const sourceType = $('adminSourceTypeSelect')?.value || 'PHIMAPI_JSON';
    const baseUrl = $('adminSourceUrlInput')?.value.trim() || '';
    if (!name || !baseUrl) return showToast('Enter both source name and URL');
    try {
      await cloudAdminAction({ action: 'addSource', name, sourceType, baseUrl });
      $('adminSourceNameInput').value = '';
      $('adminSourceUrlInput').value = '';
      showToast(`🌐 Added clean movie source: ${name}! Refreshing feed...`);
      loadAdminVaultPanel();
      loadCategory('hot', { forceRefresh: true, silent: true });
    } catch (err) {
      showToast(err.message || 'Source blocked or invalid');
    }
  });
  $('adminSaveBlockedBtn')?.addEventListener('click', async () => {
    try {
      const termsText = $('adminBlockedTermsInput')?.value || '';
      await cloudAdminAction({ action: 'updateBlockedTerms', termsText });
      showToast('🛡️ Updated VN-Safe custom blocklist!');
      loadAdminVaultPanel();
    } catch (err) {
      showToast(err.message || 'Failed to save blocklist');
    }
  });

  // Close backdrops
  document.querySelectorAll('.backdrop').forEach((bd) => {
    bd.addEventListener('click', (e) => {
      if (e.target === bd) bd.classList.remove('open');
    });
  });
  document.querySelectorAll('[data-close]').forEach((btn) => {
    btn.onclick = () => $(btn.dataset.close).classList.remove('open');
  });

  // Video & Player Controls (Back arrow -> Minimizes to Bottom-Right Pop-out; Explicit ✕ -> Closes!)
  const video = $('videoEl');
  $('hudBackBtn').onclick = (e) => minimizePlayer(e);
  $('miniCloseBtn').onclick = (e) => closePlayer(e);
  $('miniExpandBtn').onclick = (e) => restorePlayer(e);
  $('miniPlayBtn').onclick = (e) => {
    e.stopPropagation();
    ensureWebAudioDsp(video);
    if (video.paused) video.play().catch(() => {});
    else video.pause();
  };
  $('hudSubLangPill').onclick = openSubModal;
  $('hudMoreBtn').onclick = openMoreModal;
  $('hudCastBtn').onclick = () => {
    if (state.currentMovie && state.currentEpisode) {
      castCurrentToTv(state.currentMovie, state.currentEpisode, video.currentTime);
    } else {
      openTvCastModal();
    }
  };

  $('playPauseBtn').onclick = () => {
    ensureWebAudioDsp(video);
    if (video.paused) video.play().catch(() => {});
    else video.pause();
    setControlsVisible(true);
  };
  $('rewBtn').onclick = () => {
    video.currentTime = Math.max(0, video.currentTime - 10);
    showGestureToast('⏪ -10s');
    setControlsVisible(true);
  };
  $('fwdBtn').onclick = () => {
    video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
    showGestureToast('+10s ⏩');
    setControlsVisible(true);
  };

  $('hudLockBtn').onclick = () => {
    state.isLocked = true;
    $('lockFab').classList.add('visible');
    setControlsVisible(false);
    showGestureToast(tr('lockedToast'));
  };
  $('lockFab').onclick = () => {
    state.isLocked = false;
    $('lockFab').classList.remove('visible');
    setControlsVisible(true);
    showGestureToast(tr('unlockedToast'));
  };
  $('hudSpeedBoostBtn').onclick = openMoreModal;
  $('hudFsBtn').onclick = () => togglePlayerFullscreen(false);
  if ($('hudRotateBtn')) {
    $('hudRotateBtn').onclick = () => togglePlayerFullscreen(true);
  }
  const onFsChange = () => {
    const active = Boolean(document.fullscreenElement || document.webkitFullscreenElement);
    $('hudFsBtn').textContent = active ? tr('exitFsBtn') : tr('fullscreenBtn');
    $('playerModal').classList.toggle('is-fullscreen', active);
  };
  document.addEventListener('fullscreenchange', onFsChange);
  document.addEventListener('webkitfullscreenchange', onFsChange);

  // Seekbar, timeupdate (auto-save every 3s) & lifecycle save
  video.addEventListener('play', () => {
    $('playPauseBtn').textContent = '⏸';
    if ($('miniPlayBtn')) $('miniPlayBtn').textContent = '⏸';
    setControlsVisible(true);
  });
  video.addEventListener('pause', () => {
    $('playPauseBtn').textContent = '▶';
    if ($('miniPlayBtn')) $('miniPlayBtn').textContent = '▶';
    persistCurrentPlaybackProgress({ immediate: true });
    setControlsVisible(true);
  });
  video.addEventListener('timeupdate', () => {
    const cur = video.currentTime || 0;
    const dur = video.duration || 0;
    $('curTimeLabel').textContent = formatTime(cur);
    $('durTimeLabel').textContent = formatTime(dur);
    if (dur > 0) $('seekRange').value = String((cur / dur) * 1000);
    renderActiveSubtitle(cur * 1000);
    if (!video.paused && cur > 2 && Date.now() - state.lastSavedPosTs >= 3000) {
      persistCurrentPlaybackProgress({ immediate: false });
    }
  });
  const onLeavePage = () => {
    persistCurrentPlaybackProgress({ immediate: true, useBeacon: true });
    flushPendingCloudProgress(true);
  };
  window.addEventListener('pagehide', onLeavePage);
  window.addEventListener('beforeunload', onLeavePage);
  window.addEventListener('focus', () => {
    if (isCloudSignedIn()) pullCloudStateAndMerge({ silent: true });
  });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      onLeavePage();
    } else if (document.visibilityState === 'visible' && isCloudSignedIn()) {
      pullCloudStateAndMerge({ silent: true });
    }
  });
  $('seekRange').addEventListener('input', (e) => {
    const dur = video.duration || 0;
    if (dur > 0) video.currentTime = (Number(e.target.value) / 1000) * dur;
    setControlsVisible(true);
  });

  // Tap & Swipe Gestures on Player Stage (Tap minimized pop-out -> Restore!)
  const stage = $('playerStage');
  let lastTap = 0;
  let touchStart = null;
  stage.addEventListener('click', (e) => {
    if (e.target.closest?.('.mini-close-btn, .mini-ctrl-btn')) return;
    if (state.isMinimized) {
      // Prevent the same click that just clicked ← (#hudBackBtn) from immediately re-expanding!
      if (Date.now() - state.minimizedAt < 350) return;
      restorePlayer(e);
      return;
    }
    const hud = $('playerHud');
    if (e.target !== stage && e.target !== video && e.target !== hud) return;
    if (state.isLocked) {
      showGestureToast(tr('tapLockHint'));
      return;
    }
    const now = Date.now();
    const rect = stage.getBoundingClientRect();
    const relX = (e.clientX - rect.left) / rect.width;
    if (now - lastTap < 280) {
      if (relX < 0.35) {
        video.currentTime = Math.max(0, video.currentTime - 10);
        showGestureToast('⏪ -10s');
      } else if (relX > 0.65) {
        video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
        showGestureToast('+10s ⏩');
      } else {
        video.paused ? video.play() : video.pause();
      }
      lastTap = 0;
    } else {
      lastTap = now;
      setControlsVisible(!state.controlsVisible);
    }
  });

  stage.addEventListener('touchstart', (e) => {
    if (state.isMinimized || state.isLocked || e.touches.length !== 1) return;
    const t = e.touches[0];
    touchStart = { x: t.clientX, y: t.clientY, vol: state.volumeScale, bright: state.brightness, time: video.currentTime, mode: null };
  }, { passive: true });

  stage.addEventListener('touchmove', (e) => {
    if (state.isMinimized || !touchStart || state.isLocked || e.touches.length !== 1) return;
    const t = e.touches[0];
    const dx = t.clientX - touchStart.x;
    const dy = t.clientY - touchStart.y;
    if (!touchStart.mode && (Math.abs(dx) > 18 || Math.abs(dy) > 18)) {
      touchStart.mode = Math.abs(dy) > Math.abs(dx)
        ? (touchStart.x < window.innerWidth * 0.5 ? 'bright' : 'vol')
        : 'seek';
    }
    if (touchStart.mode === 'bright') {
      state.brightness = Math.max(0.25, Math.min(1.0, touchStart.bright - dy / 300));
      video.style.filter = `brightness(${state.brightness})`;
      showGestureToast(`☀ Độ sáng ${Math.round(state.brightness * 100)}%`);
    } else if (touchStart.mode === 'vol') {
      ensureWebAudioDsp(video);
      applyVolumeBoost(touchStart.vol - (dy / 240) * 2.0);
      const pct = Math.round(state.volumeScale * 100);
      showGestureToast(pct > 100 ? `🔊 ${pct}% BOOST` : `🔊 ${pct}%`);
    } else if (touchStart.mode === 'seek') {
      const delta = Math.round((dx / window.innerWidth) * 90);
      touchStart.targetSeek = Math.max(0, Math.min(video.duration || 0, touchStart.time + delta));
      showGestureToast(`${delta >= 0 ? '+' : ''}${delta}s • ${formatTime(touchStart.targetSeek)}`);
    }
  }, { passive: true });

  stage.addEventListener('touchend', () => {
    if (touchStart?.mode === 'seek' && typeof touchStart.targetSeek === 'number') {
      video.currentTime = touchStart.targetSeek;
    }
    touchStart = null;
  });

  // PC Keyboard Shortcuts (Escape minimizes to pop-out instead of killing video!)
  document.addEventListener('keydown', (e) => {
    if (!$('playerModal').classList.contains('open') || state.isMinimized) return;
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) return;
    if (e.code === 'Space') { e.preventDefault(); video.paused ? video.play() : video.pause(); }
    else if (e.key === 'ArrowLeft') { video.currentTime = Math.max(0, video.currentTime - 10); showGestureToast('⏪ -10s'); }
    else if (e.key === 'ArrowRight') { video.currentTime = Math.min(video.duration || 0, video.currentTime + 10); showGestureToast('+10s ⏩'); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); ensureWebAudioDsp(video); applyVolumeBoost(state.volumeScale + 0.1); showGestureToast(`🔊 ${Math.round(state.volumeScale * 100)}%`); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); ensureWebAudioDsp(video); applyVolumeBoost(state.volumeScale - 0.1); showGestureToast(`🔊 ${Math.round(state.volumeScale * 100)}%`); }
    else if (e.key.toLowerCase() === 'f') { $('hudFsBtn').click(); }
    else if (e.key.toLowerCase() === 'c') { openSubModal(); }
    else if (e.key === 'Escape') { minimizePlayer(); }
  });

  // Subtitle Modal Controls
  $('applyCustomLangBtn').onclick = () => {
    const v = $('customLangInput').value.trim();
    if (!v) return;
    state.prefs.primaryLang = v;
    savePrefs();
    $('customLangInput').value = '';
    renderLanguagePickers();
    if (state.sourceCues.length) applyLoadedSourceCues(state.sourceCues, 'auto');
    showToast(`Đã chuyển phụ đề AI sang: ${v}`);
  };
  $('subTrackSelect').onchange = (e) => {
    const tr = state.subTracks.find((t) => t.id === e.target.value);
    if (tr) selectSubtitleTrack(tr);
  };
  $('localSubFile').onchange = async (e) => {
    const f = e.target.files?.[0];
    if (!f) return;
    const raw = await f.text();
    const cues = parseSubtitleText(raw);
    if (cues.length) {
      await applyLoadedSourceCues(cues, 'auto', { skipCacheLookup: true, trackLabel: f.name });
      showToast(`Đã nạp & lưu ${cues.length} câu từ ${f.name}`);
    } else {
      showToast('Không đọc được định dạng file phụ đề!');
    }
  };
  $('subEnableToggle').onchange = (e) => { state.prefs.subEnabled = e.target.checked; savePrefs(); renderActiveSubtitle(video.currentTime * 1000); };
  $('dualSubToggle').onchange = (e) => {
    state.prefs.dualSub = e.target.checked;
    savePrefs();
    if (state.sourceCues.length) applyLoadedSourceCues(state.sourceCues, 'auto');
  };
  $('subSizeRange').oninput = (e) => { state.prefs.primarySize = Number(e.target.value); $('subSizeLabel').textContent = `${state.prefs.primarySize}px`; savePrefs(); renderActiveSubtitle(video.currentTime * 1000); };
  $('secSizeRange').oninput = (e) => { state.prefs.secondarySize = Number(e.target.value); $('secSizeLabel').textContent = `${state.prefs.secondarySize}px`; savePrefs(); renderActiveSubtitle(video.currentTime * 1000); };
  $('subColorInput').oninput = (e) => { state.prefs.primaryColor = e.target.value; savePrefs(); renderActiveSubtitle(video.currentTime * 1000); };
  $('secColorInput').oninput = (e) => { state.prefs.secondaryColor = e.target.value; savePrefs(); renderActiveSubtitle(video.currentTime * 1000); };
  $('syncMinusBtn').onclick = () => { state.prefs.syncOffsetMs -= 500; $('syncValLabel').textContent = `${(state.prefs.syncOffsetMs / 1000).toFixed(1)}s`; savePrefs(); };
  $('syncPlusBtn').onclick = () => { state.prefs.syncOffsetMs += 500; $('syncValLabel').textContent = `${(state.prefs.syncOffsetMs / 1000).toFixed(1)}s`; savePrefs(); };
  $('syncResetBtn').onclick = () => { state.prefs.syncOffsetMs = 0; $('syncValLabel').textContent = '0.0s'; savePrefs(); };

  // Chill-Player More Menu (Speed, Boost 200%, EQ, Aspect, Sleep, PiP)
  document.querySelectorAll('[data-speed]').forEach((b) => {
    b.onclick = () => {
      state.speed = Number(b.dataset.speed);
      video.playbackRate = state.speed;
      document.querySelectorAll('[data-speed]').forEach((x) => x.classList.toggle('active', x === b));
      $('hudSpeedBoostBtn').textContent = `⚡ ${state.speed}x`;
      showGestureToast(`Tốc độ ${state.speed}x`);
    };
  });
  $('boostSlider').oninput = (e) => {
    ensureWebAudioDsp(video);
    applyVolumeBoost(Number(e.target.value) / 100);
  };
  document.querySelectorAll('[data-eq]').forEach((b) => {
    b.onclick = () => { ensureWebAudioDsp(video); applyEqPreset(b.dataset.eq); };
  });
  document.querySelectorAll('[data-aspect]').forEach((b) => {
    b.onclick = () => {
      state.aspectMode = b.dataset.aspect;
      video.className = state.aspectMode === 'fit' ? '' : `aspect-${state.aspectMode}`;
      document.querySelectorAll('[data-aspect]').forEach((x) => x.classList.toggle('active', x === b));
    };
  });
  document.querySelectorAll('[data-sleep]').forEach((b) => {
    b.onclick = () => {
      clearTimeout(state.sleepTimerId);
      const mins = Number(b.dataset.sleep);
      state.sleepMinutes = mins || null;
      document.querySelectorAll('[data-sleep]').forEach((x) => x.classList.toggle('active-violet', x === b));
      if (mins > 0) {
        state.sleepTimerId = setTimeout(() => {
          video.pause();
          showToast('🌙 Đã hết hẹn giờ tắt phim!');
        }, mins * 60000);
        showToast(`🌙 Sẽ tự dừng phim sau ${mins} phút`);
      }
    };
  });
  $('pipBtn').onclick = () => {
    if (document.pictureInPictureElement) document.exitPictureInPicture().catch(() => {});
    else video.requestPictureInPicture?.().catch(() => showToast('Trình duyệt không hỗ trợ PiP'));
  };

  // Settings Modal (UI Language, Gemini Key, Default AI Sub Language, Direct URL / Local Video)
  $('saveSettingsBtn').onclick = () => {
    if ($('uiLangSelect')) {
      state.prefs.uiLang = resolveUiLang($('uiLangSelect').value);
    }
    state.prefs.geminiApiKey = $('geminiKeyInput').value.trim();
    state.prefs.geminiModel = $('geminiModelSelect').value;
    state.prefs.primaryLang = $('defaultLangSelect').value;
    savePrefs();
    applyUiTranslations();
    renderCategories();
    if (state.heroMovies.length) renderHero(state.heroMovies[state.heroIndex] || state.heroMovies[0]);
  else if (state.movies.length) renderHero(state.movies[0]);
    updateCloudHeaderBadge();
    $('settingsModal').classList.remove('open');
    showToast(tr('settingsSavedToast'));
  };
  $('playDirectBtn').onclick = () => {
    const url = $('directUrlInput').value.trim();
    if (!url) return showToast('Nhập link .m3u8 hoặc .mp4 trước!');
    $('settingsModal').classList.remove('open');
    const customMovie = { slug: 'direct', name: $('directTitleInput').value.trim() || 'Luồng Trực Tiếp', origin_name: '', poster: '', backdrop: '', type: 'single', servers: [] };
    openPlayer(customMovie, { name: 'Direct', link_m3u8: url }, 0);
  };
  $('localVideoFile').onchange = (e) => {
    const f = e.target.files?.[0];
    if (!f) return;
    const blobUrl = URL.createObjectURL(f);
    $('settingsModal').classList.remove('open');
    openPlayer({ slug: 'local', name: f.name, origin_name: '', poster: '', backdrop: '', type: 'single', servers: [] }, { name: 'Local', link_m3u8: blobUrl }, 0);
  };

  // Secondary TV Cast Modal
  $('saveTvIpBtn').onclick = () => {
    state.prefs.tvIp = $('tvIpInput').value.trim();
    savePrefs();
    showToast(state.prefs.tvIp ? `📡 Đã lưu TV IP: ${state.prefs.tvIp}` : 'Đã xóa IP TV');
  };
  $('syncKeyToTvBtn').onclick = syncGeminiKeyToTv;
  document.querySelectorAll('[data-tvkey]').forEach((b) => {
    b.onclick = () => sendTvRemoteKey(b.dataset.tvkey);
  });

  updateCloudHeaderBadge();
  if (isCloudSignedIn()) {
    cloudRefreshMe().then(() => updateCloudHeaderBadge()).catch(() => {});
    pullCloudStateAndMerge({ silent: true, force: true });
    loadLiveGeminiModels({ silent: true });
  }

  // Silently auto-refresh Home live movie feed every 10 minutes so new/hot releases stay current!
  setInterval(() => {
    if (
      document.visibilityState === 'visible' &&
      state.activeCategory === 'hot' &&
      !$('playerModal')?.classList.contains('open') &&
      !$('detailView')?.classList.contains('active')
    ) {
      loadCategory('hot', { forceRefresh: false, silent: true });
    }
  }, 10 * 60 * 1000);
}

document.addEventListener('DOMContentLoaded', init);
