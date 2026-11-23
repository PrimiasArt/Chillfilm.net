import {
  SUBTITLE_LANGUAGES,
  resolveLanguage,
  parseSubtitleText,
  findActiveCueText,
  fetchOpenSubtitlesV3,
  translateCueBatch,
  transcribeAudioChunkDirect,
  buildSubCacheKey,
  buildLegacySubCacheKey,
  loadCachedEpisodeSub,
  saveCachedEpisodeSub,
  hasWrongScriptForLang,
  mergeTimeRanges,
  parseHlsMediaSegments,
  buildHlsAudioWindows,
  extractHlsWindowAdtsBase64,
  downloadSrtFile,
} from './subtitles.js?v=21';
import { UI_LANGUAGES, resolveUiLang, t } from './i18n.js?v=16';
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
  cloudUpdateProfile,
  cloudFetchAdminConfig,
  cloudAdminAction,
  cloudPullState,
  cloudPushState,
  cloudFetchSubtitle,
  cloudSaveSubtitle,
  cloudTranslateCueBatch,
  cloudTranscribeAudioChunk,
  cloudFetchLiveModels,
  fetchLiveCatalogFeed,
  fetchCatalogBrowse,
  fetchCatalogDetail,
  fetchCatalogSubtitles,
  fetchSubtitleTextSmart,
  partyListLobby,
  partyCreate,
  partyJoin,
  partyPoll,
  partyControl,
  partyUpdateSettings,
  partyClaimHost,
  partyQueueAdd,
  partyQueueVote,
  partyQueueRemove,
  partyQueuePlay,
  partyReact,
  partySendChat,
  partyLeave,
  fetchMovieGlossary,
  saveMovieGlossary,
  fetchAiVibeSearch,
} from './sync.js?v=21';

const PHIM_API = 'https://phimapi.com';
const PHIM_CDN = 'https://phimimg.com';
const APK_RELEASE_URL = 'https://github.com/PrimiasArt/Chillfilm.net/releases/download/android-apk-latest/ChillFilm-latest.apk';

const SVG_ICONS = {
  playLg: '<svg class="ui-ico" viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><polygon points="7,4 20,12 7,20"/></svg>',
  pauseLg: '<svg class="ui-ico" viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>',
  playSm: '<svg class="ui-ico" viewBox="0 0 24 24" width="19" height="19" fill="currentColor"><polygon points="7,4 20,12 7,20"/></svg>',
  pauseSm: '<svg class="ui-ico" viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>',
  playMini: '<svg class="ui-ico" viewBox="0 0 24 24" width="13" height="13" fill="currentColor"><polygon points="7,4 20,12 7,20"/></svg>',
  pauseMini: '<svg class="ui-ico" viewBox="0 0 24 24" width="13" height="13" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>',
  playCard: '<svg class="ui-ico" viewBox="0 0 24 24" width="19" height="19" fill="currentColor"><polygon points="7,4 20,12 7,20"/></svg>',
  chevLeft: '<svg class="ui-ico" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>',
  chevRight: '<svg class="ui-ico" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>',
  chevLeftSm: '<svg class="ui-ico" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>',
  chevRightSm: '<svg class="ui-ico" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>',
  volHigh: '<svg class="ui-ico" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/></svg>',
  volLow: '<svg class="ui-ico" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>',
  volMute: '<svg class="ui-ico" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/></svg>',
  fsEnter: '<svg class="ui-ico" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/><line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/></svg>',
  fsExit: '<svg class="ui-ico" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 14 10 14 10 20"/><polyline points="20 10 14 10 14 4"/><line x1="14" y1="10" x2="21" y2="3"/><line x1="3" y1="21" x2="10" y2="14"/></svg>',
  aspect: '<svg class="ui-ico" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><polyline points="7 12 7 9 10 9"/><polyline points="17 12 17 15 14 15"/></svg>',
  nextEp: '<svg class="ui-ico" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polygon points="5 4 15 12 5 20 5 4" fill="currentColor"/><line x1="19" y1="5" x2="19" y2="19"/></svg>',
};

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
  skipIntroSec: 85,
  hardsubMask: false,
  hardsubMaskMode: 'blur',
  hardsubMaskHeight: 58,
  hardsubMaskBottom: 3,
  ttsDubbing: false,
  ttsRate: 1.15,
  ambientGlow: true,
  nightAudio: false,
  preferredQuality: 'auto',
  geminiApiKey: '',
  geminiModel: 'gemini-2.5-flash',
  tvIp: '',
};

const state = {
  prefs: loadPrefs(),
  activeCategory: 'hot',
  movies: [],
  homeRows: [],
  currentMovie: null,
  currentEpisode: null,
  currentServerIdx: 0,
  episodesFlat: [],
  hls: null,
  audioCtx: null,
  gainNode: null,
  eqNodes: null,
  audioCaptureDest: null,
  volumeScale: 1.0,
  lastNonZeroVolume: 1.0,
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
  hudPointerInside: false,
  // Phase 1 Cinema Pro (Skip Intro, Auto-Next, Failover, Live AI Audio Sub)
  skipIntroDismissed: false,
  autoNextCancelled: false,
  autoNextTimer: null,
  autoNextCountdownSec: 0,
  stallTimer: null,
  failoverTriedServers: new Set(),
  hlsNetworkRetries: 0,
  audioSubActive: false,
  audioSubRecorder: null,
  audioSubLoopTimer: null,
  audioSubSessionId: 0,
  audioSubChunksDone: 0,
  // Watch Party 2.0 (Lobby • RTT Smooth Sync • Co-Control • Up Next Queue • Reactions • Split Theater)
  party: {
    roomCode: '',
    roomTitle: '',
    visibility: 'PRIVATE',
    controlMode: 'HOST_ONLY',
    hasPasscode: false,
    hostToken: '',
    hostName: '',
    hostInactive: false,
    memberId: '',
    nickname: '',
    isHost: false,
    pollTimer: null,
    lastMsgId: 0,
    lastReactionTs: 0,
    lastActionAt: 0,
    lastStateUpdatedAt: 0,
    chatVisible: false,
    splitMode: typeof window !== 'undefined' ? window.innerWidth >= 860 : false,
    activeSideTab: 'chat',
    applyingRemoteSync: false,
    softSyncActive: false,
    lastMovieSlug: '',
    lastEpisodeUrl: '',
    queue: [],
    members: [],
    countdownTimer: null,
    createPickedMovie: null,
    createPickedEpisode: null,
  },
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
  liveAudioFallbackCue: null,
  detailSelectedEp: null,
  lastLookaheadCheckTs: 0,
  subJob: {
    id: 0,
    active: false,
    mode: '',
    phase: 'idle',
    movieSlug: '',
    episodeKey: '',
    episodeName: '',
    langCode: '',
    scope: 'lookahead10',
    percent: 0,
    doneCount: 0,
    totalCount: 0,
    coveredMs: 0,
    targetMs: 0,
    totalDurationMs: 0,
    engineLabel: '',
    message: '',
    errorMsg: '',
    startedAt: 0,
    abortCtrl: null,
    movie: null,
    episode: null,
    cues: [],
    hlsWindows: [],
    hlsDoneWindowSet: new Set(),
    hlsKeyCache: new Map(),
    hlsAnchorMs: 0,
    hlsFilling: false,
    audioMode: false,
    notifiedReady: false,
  },
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
  // V14 Super-Upgrade Pack (#1-#11)
  cloudSubSlugs: new Set(),
  latestEpisodeMap: {},
  movieIntroMap: loadMovieIntroMap(),
  ttsLastSpokenText: '',
  ttsDucked: false,
  ambientTimer: null,
  scrubHls: null,
  scrubLoadedUrl: '',
  scrubLastHoverTs: 0,
  // V16 Practical UX Pack
  compressorNode: null,
  movieSyncMap: loadMovieSyncMap(),
  watchedMap: loadWatchedEpisodesMap(),
  myListTab: 'all',
  drawerEpQuery: '',
  drawerEpDesc: false,
  nextEpBgStartedKeys: new Set(),
  nextEpBgReadyKeys: new Set(),
  hlsLevels: [],
  currentQualityLevel: -1,
  deferredPwaPrompt: null,
  isPopstateNav: false,
  hold2xActive: false,
  // V17 Turbo Latency Engine (SWR RAM Caches + Hover Prefetch + Deferred Scrub)
  detailMemCache: new Map(), // key -> { detail, ts }
  detailInFlight: new Map(), // key -> Promise<detail>
  browseMemCache: new Map(), // catId -> { items, countries, ts }
  scrubDeferTimer: null,
};

const FEED_CACHE_STORAGE_KEY = 'chillfilm_nki_feed_cache_v1';
const DETAIL_CACHE_TTL_MS = 5 * 60 * 1000;
const BROWSE_CACHE_TTL_MS = 3 * 60 * 1000;

function loadCachedHomeFeed() {
  try {
    const raw = localStorage.getItem(FEED_CACHE_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || !Array.isArray(parsed.rows) || !parsed.rows.length) return null;
    if (Date.now() - Number(parsed._cachedAt || 0) > 60 * 60 * 1000) return null;
    return parsed;
  } catch {
    return null;
  }
}

function saveCachedHomeFeed(feed) {
  if (!feed || !Array.isArray(feed.rows) || !feed.rows.length) return;
  try {
    const compact = {
      ok: true,
      updatedAt: feed.updatedAt || Date.now(),
      crawlerStatus: feed.crawlerStatus || null,
      cloudSubSlugs: Array.isArray(feed.cloudSubSlugs) ? feed.cloudSubSlugs.slice(0, 300) : [],
      latestEpisodeMap: feed.latestEpisodeMap || {},
      countries: Array.isArray(feed.countries) ? feed.countries : [],
      heroMovies: Array.isArray(feed.heroMovies) ? feed.heroMovies.slice(0, 6) : [],
      rows: feed.rows.slice(0, 14).map((r) => ({
        id: r.id,
        customTitle: r.customTitle || '',
        isTopTen: Boolean(r.isTopTen),
        movies: (r.movies || []).slice(0, 14),
      })),
      _cachedAt: Date.now(),
    };
    localStorage.setItem(FEED_CACHE_STORAGE_KEY, JSON.stringify(compact));
  } catch {
    // ignore storage quota
  }
}

function getMovieDetailCacheKey(source, slug, sourceId = '') {
  return `${String(source || 'phimapi').toLowerCase()}::${String(sourceId || '')}::${String(slug || '').trim()}`;
}

function getCachedMovieDetail(source, slug, sourceId = '') {
  const key = getMovieDetailCacheKey(source, slug, sourceId);
  const hit = state.detailMemCache.get(key);
  if (hit && Date.now() - hit.ts < DETAIL_CACHE_TTL_MS) return hit.detail;
  return null;
}

function setCachedMovieDetail(source, slug, sourceId, detail) {
  if (!detail) return;
  const keys = new Set([
    getMovieDetailCacheKey(source, slug, sourceId),
    getMovieDetailCacheKey(detail.source || source, detail.slug || slug, detail.sourceId || sourceId),
  ]);
  const entry = { detail, ts: Date.now() };
  keys.forEach((k) => state.detailMemCache.set(k, entry));
  if (state.detailMemCache.size > 60) {
    const oldestKey = state.detailMemCache.keys().next().value;
    if (oldestKey) state.detailMemCache.delete(oldestKey);
  }
}

function prefetchMovieDetail(movie) {
  if (!movie || !movie.slug || movie.source === 'direct') return;
  const source = movie.source || 'phimapi';
  const sourceId = movie.sourceId || '';
  const key = getMovieDetailCacheKey(source, movie.slug, sourceId);
  if (getCachedMovieDetail(source, movie.slug, sourceId)) return;
  if (state.detailInFlight.has(key)) return;
  const p = fetchCatalogDetail({ source, slug: movie.slug, sourceId })
    .then((detail) => {
      if (detail) setCachedMovieDetail(source, movie.slug, sourceId, detail);
      return detail;
    })
    .catch(() => null)
    .finally(() => {
      state.detailInFlight.delete(key);
    });
  state.detailInFlight.set(key, p);
}

function renderDetailOptimisticShell(movieObj) {
  if (!movieObj) return;
  $('homeView').style.display = 'none';
  $('topbar')?.classList.add('solid');
  const dv = $('detailView');
  dv.classList.add('active');
  const dispTitle = getDisplayTitle(movieObj) || movieObj.slug || 'ChillFilm';
  const posterSrc = movieObj.poster || './assets/icon-192.png?v=2';
  const lang = resolveLanguage(state.prefs.primaryLang);
  dv.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;">
      <button class="btn-glass" id="detailBackBtn">${escapeHtml(tr('backToCatalog'))}</button>
    </div>
    <div class="detail-hero">
      <img class="detail-poster" src="${escapeHtml(posterSrc)}" alt="${escapeHtml(dispTitle)}" onerror="if(!this.dataset.fb){this.dataset.fb='1';this.src='./assets/icon-192.png?v=2';}" />
      <div style="display:flex;flex-direction:column;gap:10px;min-width:0;flex:1;">
        <div class="hero-meta">
          <span class="badge-ai">AI SUB (${lang.badge})</span>
          <span style="font-size:12px;color:#b3b3b3;font-weight:700;">${escapeHtml(movieObj.year || '')} • ${escapeHtml(movieObj.episode_current || movieObj.quality || 'HD')}</span>
        </div>
        <h2 style="font-size:clamp(20px,3.2vw,32px);font-weight:900;">${escapeHtml(dispTitle)}</h2>
        <div class="shimmer-box" style="height:14px;width:92%;"></div>
        <div class="shimmer-box" style="height:14px;width:74%;"></div>
        <div style="display:flex;gap:10px;margin-top:8px;">
          <div class="shimmer-box" style="height:40px;width:155px;border-radius:6px;"></div>
          <div class="shimmer-box" style="height:40px;width:115px;border-radius:6px;"></div>
        </div>
      </div>
    </div>
    <div style="margin-top:22px;">
      <div class="shimmer-box" style="height:18px;width:190px;margin-bottom:12px;"></div>
      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(84px,1fr));gap:8px;">
        ${Array.from({ length: 12 }).map(() => '<div class="shimmer-box" style="height:38px;border-radius:8px;"></div>').join('')}
      </div>
    </div>
  `;
  if ($('detailBackBtn')) $('detailBackBtn').onclick = closeDetailView;
  window.scrollTo({ top: 0, behavior: 'auto' });
}

const MOVIE_INTRO_STORAGE_KEY = 'chillfilm_nki_movie_intro_v1';
const MOVIE_SYNC_STORAGE_KEY = 'chillfilm_nki_movie_sync_v1';
const WATCHED_EPS_STORAGE_KEY = 'chillfilm_nki_watched_eps_v1';
const SEARCH_HISTORY_STORAGE_KEY = 'chillfilm_nki_search_history_v1';

function loadMovieSyncMap() {
  try {
    const obj = JSON.parse(localStorage.getItem(MOVIE_SYNC_STORAGE_KEY) || '{}');
    return obj && typeof obj === 'object' ? obj : {};
  } catch {
    return {};
  }
}

function getMovieSyncOffsetMs(movieSlug) {
  if (!movieSlug) return state.prefs.syncOffsetMs || 0;
  const k = normalizeMovieSlug(movieSlug);
  const v = state.movieSyncMap?.[k];
  return typeof v === 'number' && Number.isFinite(v) ? v : (state.prefs.syncOffsetMs || 0);
}

function setMovieSyncOffsetMs(movieSlug, offsetMs) {
  const clamped = Math.max(-15000, Math.min(15000, Math.round(Number(offsetMs) || 0)));
  state.prefs.syncOffsetMs = clamped;
  if (movieSlug) {
    const k = normalizeMovieSlug(movieSlug);
    state.movieSyncMap[k] = clamped;
    try {
      localStorage.setItem(MOVIE_SYNC_STORAGE_KEY, JSON.stringify(state.movieSyncMap));
    } catch { /* ignore */ }
  }
  savePrefs();
  if ($('syncValLabel')) $('syncValLabel').textContent = `${(clamped / 1000).toFixed(2).replace(/\.00$/, '.0')}s`;
  if ($('syncRangeSlider')) $('syncRangeSlider').value = String(Math.max(-10000, Math.min(10000, clamped)));
  const video = $('videoEl');
  if (video) renderActiveSubtitle((video.currentTime || 0) * 1000);
}

function loadWatchedEpisodesMap() {
  try {
    const obj = JSON.parse(localStorage.getItem(WATCHED_EPS_STORAGE_KEY) || '{}');
    return obj && typeof obj === 'object' ? obj : {};
  } catch {
    return {};
  }
}

function saveWatchedEpisodesMap() {
  try {
    localStorage.setItem(WATCHED_EPS_STORAGE_KEY, JSON.stringify(state.watchedMap));
  } catch { /* ignore */ }
}

function markEpisodeWatched(movieSlug, epOrKey) {
  if (!movieSlug) return;
  const mKey = normalizeMovieSlug(movieSlug);
  const epKey = typeof epOrKey === 'string'
    ? normalizeEpisodeKey(epOrKey)
    : normalizeEpisodeKey(epOrKey?.name || epOrKey?.slug || 'full');
  if (!mKey || mKey === 'unknown' || !epKey) return;
  if (!Array.isArray(state.watchedMap[mKey])) state.watchedMap[mKey] = [];
  if (!state.watchedMap[mKey].includes(epKey)) {
    state.watchedMap[mKey].push(epKey);
    if (state.watchedMap[mKey].length > 500) state.watchedMap[mKey] = state.watchedMap[mKey].slice(-500);
    saveWatchedEpisodesMap();
  }
}

function isEpisodeWatched(movieSlug, epOrKey) {
  if (!movieSlug) return false;
  const mKey = normalizeMovieSlug(movieSlug);
  const list = state.watchedMap?.[mKey];
  if (!Array.isArray(list) || !list.length) return false;
  const epKey = typeof epOrKey === 'string'
    ? normalizeEpisodeKey(epOrKey)
    : normalizeEpisodeKey(epOrKey?.name || epOrKey?.slug || 'full');
  return list.includes(epKey);
}

function loadSearchHistory() {
  try {
    const arr = JSON.parse(localStorage.getItem(SEARCH_HISTORY_STORAGE_KEY) || '[]');
    return Array.isArray(arr) ? arr.filter((x) => typeof x === 'string' && x.trim()) : [];
  } catch {
    return [];
  }
}

function saveSearchHistory(keyword) {
  const q = String(keyword || '').trim();
  if (q.length < 2) return;
  const next = [q, ...loadSearchHistory().filter((x) => x.toLowerCase() !== q.toLowerCase())].slice(0, 8);
  try {
    localStorage.setItem(SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(next));
  } catch { /* ignore */ }
}

function clearSearchHistory() {
  try { localStorage.removeItem(SEARCH_HISTORY_STORAGE_KEY); } catch { /* ignore */ }
}

function removeHistoryItem(slug) {
  const canonical = normalizeMovieSlug(slug);
  const next = loadHistory().filter((h) => normalizeMovieSlug(h.slug) !== canonical);
  try {
    localStorage.setItem('chillfilm_nki_history_v1', JSON.stringify(next));
    const epMap = loadEpisodePosMap();
    Object.keys(epMap).forEach((k) => {
      if (k.startsWith(`${canonical}|`)) delete epMap[k];
    });
    localStorage.setItem(EP_POS_STORAGE_KEY, JSON.stringify(epMap));
  } catch { /* ignore */ }
  if (isCloudSignedIn()) {
    cloudPushState({ watchProgressRemoved: [canonical] }).catch(() => {});
  }
  renderHistorySection();
  renderMyListSection();
  showToast('🗑️ Đã xóa khỏi danh sách Tiếp tục xem');
}

function clearAllHistory() {
  const hist = loadHistory();
  if (!hist.length) return;
  if (!window.confirm('Xóa toàn bộ danh sách "Tiếp tục xem" trên thiết bị này và Cloud?')) return;
  const slugs = hist.map((h) => normalizeMovieSlug(h.slug)).filter(Boolean);
  try {
    localStorage.removeItem('chillfilm_nki_history_v1');
    localStorage.removeItem(EP_POS_STORAGE_KEY);
  } catch { /* ignore */ }
  if (isCloudSignedIn() && slugs.length) {
    cloudPushState({ watchProgressRemoved: slugs }).catch(() => {});
  }
  renderHistorySection();
  renderMyListSection();
  showToast('🗑️ Đã xóa toàn bộ lịch sử Tiếp tục xem');
}

function syncBrowserUrl({ movie = null, episode = null, playing = false } = {}, { push = true } = {}) {
  if (state.isPopstateNav || typeof window === 'undefined' || !window.history?.pushState) return;
  try {
    const url = new URL(window.location.href);
    url.searchParams.delete('movie');
    url.searchParams.delete('sid');
    url.searchParams.delete('ep');
    url.searchParams.delete('play');
    if (movie && movie.slug && movie.slug !== 'direct' && movie.slug !== 'local') {
      const src = (movie.source || 'phimapi').trim().toLowerCase();
      url.searchParams.set('movie', `${src}:${movie.slug}`);
      if (movie.sourceId) url.searchParams.set('sid', String(movie.sourceId));
      if (episode?.name || episode?.slug) {
        url.searchParams.set('ep', String(episode.name || episode.slug));
      }
      if (playing) url.searchParams.set('play', '1');
    }
    const nextHref = `${url.pathname}${url.search}${url.hash}`;
    const curHref = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    if (nextHref === curHref) return;
    const histState = {
      view: playing ? 'player' : (movie ? 'detail' : 'home'),
      slug: movie?.slug || '',
      source: movie?.source || 'phimapi',
      sourceId: movie?.sourceId || '',
      ep: episode?.name || '',
    };
    if (push) window.history.pushState(histState, '', nextHref);
    else window.history.replaceState(histState, '', nextHref);
  } catch { /* ignore */ }
}

async function shareCurrentMovie(movie, ep = null) {
  if (!movie?.slug) return;
  const src = (movie.source || 'phimapi').trim().toLowerCase();
  const qs = new URLSearchParams();
  qs.set('movie', `${src}:${movie.slug}`);
  if (movie.sourceId) qs.set('sid', String(movie.sourceId));
  if (ep?.name) qs.set('ep', ep.name);
  const shareUrl = `${window.location.origin}/?${qs.toString()}`;
  const title = getDisplayTitle(movie);
  const text = `Xem "${title}"${ep?.name ? ` (${ep.name})` : ''} có phụ đề AI trên ChillFilm NKI!`;
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url: shareUrl });
      return;
    } catch { /* user cancelled or unsupported -> fall back to clipboard */ }
  }
  try {
    await navigator.clipboard?.writeText(shareUrl);
    showToast(`🔗 Đã copy link phim "${title}"!`);
  } catch {
    showToast(shareUrl);
  }
}
function loadMovieIntroMap() {
  try {
    const obj = JSON.parse(localStorage.getItem(MOVIE_INTRO_STORAGE_KEY) || '{}');
    return obj && typeof obj === 'object' ? obj : {};
  } catch {
    return {};
  }
}
function saveMovieIntroSec(movieSlug, sec) {
  if (!movieSlug) return;
  const k = normalizeMovieSlug(movieSlug);
  state.movieIntroMap[k] = Math.max(10, Math.min(300, Math.round(Number(sec) || 85)));
  try {
    localStorage.setItem(MOVIE_INTRO_STORAGE_KEY, JSON.stringify(state.movieIntroMap));
  } catch { /* ignore */ }
}

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

const MY_LIST_STORAGE_KEY = 'chillfilm_nki_mylist_v1';
const MY_LIST_TOMBSTONES_KEY = 'chillfilm_nki_mylist_tombstones_v1';

function getDisplayTitle(movie) {
  if (!movie) return '';
  const ui = resolveUiLang(state.prefs.uiLang);
  if (ui === 'vi' && movie.nameVi) return movie.nameVi;
  if (ui !== 'zh' && movie.nameEn) return movie.nameEn;
  return movie.name || movie.nameEn || movie.nameVi || movie.slug || '';
}

function getDisplayDesc(movie) {
  if (!movie) return '';
  const ui = resolveUiLang(state.prefs.uiLang);
  if (ui === 'vi' && movie.contentVi) return movie.contentVi;
  if (ui !== 'zh' && movie.contentEn) return movie.contentEn;
  return movie.content || movie.contentEn || movie.contentVi || '';
}

function makeMyListKey(movie) {
  const src = String(movie?.source || 'phimapi').trim().toLowerCase();
  const sid = String(movie?.sourceId || '').trim();
  const slug = String(movie?.slug || '').trim();
  return `${src}::${sid}::${slug}`;
}

function loadMyListTombstones() {
  try {
    const raw = JSON.parse(localStorage.getItem(MY_LIST_TOMBSTONES_KEY) || '{}');
    if (!raw || typeof raw !== 'object') return {};
    const now = Date.now();
    const clean = {};
    for (const [k, ts] of Object.entries(raw)) {
      if (Number(ts) > 0 && now - Number(ts) < 7 * 24 * 60 * 60 * 1000) {
        clean[k] = Number(ts);
      }
    }
    return clean;
  } catch {
    return {};
  }
}

function markMyListTombstones(slugs = []) {
  try {
    const map = loadMyListTombstones();
    const now = Date.now();
    for (const s of slugs) {
      const n = normalizeMovieSlug(s);
      if (n && n !== 'unknown') map[n] = now;
    }
    localStorage.setItem(MY_LIST_TOMBSTONES_KEY, JSON.stringify(map));
  } catch { /* ignore */ }
}

function clearMyListTombstone(slug) {
  try {
    const n = normalizeMovieSlug(slug);
    if (!n) return;
    const map = loadMyListTombstones();
    if (map[n]) {
      delete map[n];
      localStorage.setItem(MY_LIST_TOMBSTONES_KEY, JSON.stringify(map));
    }
  } catch { /* ignore */ }
}

function loadMyList() {
  try {
    const arr = JSON.parse(localStorage.getItem(MY_LIST_STORAGE_KEY) || '[]');
    if (!Array.isArray(arr)) return [];
    // Deduplicate by normalized slug (keep newest) so stale source-variant duplicates never stick
    const seen = new Set();
    const out = [];
    for (const it of arr) {
      if (!it || !it.slug) continue;
      const ns = normalizeMovieSlug(it.slug);
      if (ns && seen.has(ns)) continue;
      if (ns) seen.add(ns);
      out.push(it);
    }
    return out;
  } catch {
    return [];
  }
}

function saveMyList(list) {
  try {
    localStorage.setItem(MY_LIST_STORAGE_KEY, JSON.stringify((list || []).slice(0, 150)));
  } catch {
    // ignore storage quota
  }
}

function isMyListEntryMatch(entry, movie) {
  if (!entry || !movie) return false;
  const key = makeMyListKey(movie);
  const entryKey = entry.itemKey || makeMyListKey(entry);
  if (entryKey && key && entryKey === key) return true;
  const s1 = normalizeMovieSlug(entry.slug);
  const s2 = normalizeMovieSlug(movie.slug);
  return Boolean(s1 && s2 && s1 !== 'unknown' && s1 === s2);
}

function isInMyList(movie) {
  if (!movie || !movie.slug) return false;
  return loadMyList().some((it) => isMyListEntryMatch(it, movie));
}

function syncMyListActionButtons() {
  const heroBtn = $('heroMyListBtn');
  if (heroBtn) {
    const curHero = state.heroMovies?.[state.heroIndex] || state.movies?.[0];
    if (curHero) {
      const saved = isInMyList(curHero);
      heroBtn.classList.toggle('saved', saved);
      heroBtn.textContent = saved ? tr('inMyListBtn') : tr('addMyListBtn');
    }
  }
  const detBtn = $('detailMyListBtn');
  if (detBtn && state.currentMovie) {
    const saved = isInMyList(state.currentMovie);
    detBtn.classList.toggle('saved', saved);
    detBtn.textContent = saved ? tr('inMyListBtn') : tr('addMyListBtn');
  }
}

function removeFromMyList(movieOrItem, { silent = false } = {}) {
  if (!movieOrItem || !movieOrItem.slug) return false;
  const rawArr = (() => {
    try {
      const a = JSON.parse(localStorage.getItem(MY_LIST_STORAGE_KEY) || '[]');
      return Array.isArray(a) ? a : [];
    } catch { return []; }
  })();
  const normSlug = normalizeMovieSlug(movieOrItem.slug);
  const keysToRemove = new Set();
  if (movieOrItem.itemKey) keysToRemove.add(String(movieOrItem.itemKey));
  keysToRemove.add(makeMyListKey(movieOrItem));
  if (normSlug && normSlug !== 'unknown') keysToRemove.add(normSlug);

  const kept = [];
  let removedTitle = getDisplayTitle(movieOrItem);
  for (const it of rawArr) {
    if (isMyListEntryMatch(it, movieOrItem)) {
      if (it.itemKey) keysToRemove.add(String(it.itemKey));
      keysToRemove.add(makeMyListKey(it));
      if (!removedTitle) removedTitle = getDisplayTitle(it);
    } else {
      kept.push(it);
    }
  }

  saveMyList(kept);
  markMyListTombstones([normSlug, movieOrItem.slug]);
  if (isCloudSignedIn()) {
    cloudPushState({ myListRemoved: Array.from(keysToRemove) }).catch(() => {});
  }
  renderMyListSection();
  syncMyListActionButtons();
  refreshEpisodeReleaseNotifications();
  if (!silent) showToast(`🗑️ Đã xóa "${removedTitle || movieOrItem.slug}" khỏi Danh sách yêu thích`);
  return true;
}

function clearAllMyList() {
  const list = loadMyList();
  if (!list.length) return;
  const keysToRemove = new Set();
  const slugs = [];
  for (const it of list) {
    if (it.itemKey) keysToRemove.add(String(it.itemKey));
    keysToRemove.add(makeMyListKey(it));
    const ns = normalizeMovieSlug(it.slug);
    if (ns && ns !== 'unknown') {
      keysToRemove.add(ns);
      slugs.push(ns);
    }
  }
  saveMyList([]);
  markMyListTombstones(slugs);
  if (isCloudSignedIn() && keysToRemove.size) {
    cloudPushState({ myListRemoved: Array.from(keysToRemove) }).catch(() => {});
  }
  renderMyListSection();
  syncMyListActionButtons();
  refreshEpisodeReleaseNotifications();
  showToast('🗑️ Đã xóa toàn bộ Danh sách yêu thích');
}

function toggleMyList(movie) {
  if (!movie || !movie.slug) return false;
  if (isInMyList(movie)) {
    removeFromMyList(movie);
    return false;
  }
  const key = makeMyListKey(movie);
  const list = loadMyList().filter((it) => !isMyListEntryMatch(it, movie));
  clearMyListTombstone(movie.slug);
  const entry = {
    itemKey: key,
    source: movie.source || 'phimapi',
    sourceId: movie.sourceId || '',
    slug: movie.slug,
    name: getDisplayTitle(movie),
    nameEn: movie.nameEn || '',
    nameVi: movie.nameVi || '',
    origin_name: movie.origin_name || movie.name || '',
    poster: movie.poster || '',
    year: movie.year || '',
    quality: movie.quality || 'HD',
    lastKnownEpisode: movie.episode_current || movie.quality || '',
    addedAt: Date.now(),
  };
  list.unshift(entry);
  saveMyList(list);
  if (isCloudSignedIn()) {
    cloudPushState({ myList: [entry] }).catch(() => {});
  }
  showToast(`❤️ Đã thêm "${entry.name}" vào Danh sách yêu thích!`);
  renderMyListSection();
  syncMyListActionButtons();
  refreshEpisodeReleaseNotifications();
  return true;
}

/* ==================== #10 EPISODE RELEASE TRACKER (MY LIST BELL NOTIFICATIONS) ==================== */
function extractEpNumber(str) {
  const m = String(str || '').match(/(?:tập|tap|ep|episode|第)?\s*(\d{1,4})/i);
  return m ? Number(m[1]) : 0;
}

function refreshEpisodeReleaseNotifications() {
  const dot = $('notifCountDot');
  const listEl = $('notifList');
  if (!dot || !listEl) return;

  const myList = loadMyList();
  const alerts = [];
  for (const item of myList) {
    const latestEp = item.latestEpisode || state.latestEpisodeMap[item.slug] || '';
    const knownEp = item.lastKnownEpisode || item.quality || '';
    const newNum = extractEpNumber(latestEp);
    const oldNum = extractEpNumber(knownEp);
    const isNew = Boolean(
      item.hasNewEpisode ||
      (latestEp && newNum > 0 && ((oldNum > 0 && newNum > oldNum) || (!oldNum && /tập|ep|第/i.test(latestEp) && latestEp !== knownEp)))
    );
    if (isNew) {
      alerts.push({ item, knownEp, latestEp });
    }
  }

  dot.style.display = alerts.length > 0 ? 'inline-flex' : 'none';
  dot.textContent = alerts.length > 9 ? '9+' : String(alerts.length);

  if (!alerts.length) {
    listEl.innerHTML = `<div class="notif-empty">✅ Tất cả phim bộ trong ❤️ My List đều đã cập nhật mới nhất.<br><span style="opacity:.75;font-size:11px;">Auto-Crawler tự kiểm tra tập mới mỗi 3 tiếng.</span></div>`;
    return;
  }

  listEl.innerHTML = '';
  alerts.forEach(({ item, knownEp, latestEp }) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'notif-item';
    btn.innerHTML = `
      <img src="${escapeHtml(item.poster || './assets/icon-192.png?v=2')}" alt="" />
      <div style="min-width:0;flex:1;">
        <div style="font-size:12.5px;font-weight:800;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(getDisplayTitle(item))}</div>
        <div style="font-size:11px;color:#86efac;font-weight:700;margin-top:2px;">🆕 ${escapeHtml(knownEp ? `${knownEp} → ` : '')}${escapeHtml(latestEp)}</div>
      </div>
    `;
    btn.onclick = () => {
      markMyListEpisodeSeen(item.slug, latestEp);
      $('notifMenu')?.classList.remove('open');
      openMovieDetail(item, false);
    };
    listEl.appendChild(btn);
  });
}

function markMyListEpisodeSeen(slug, latestEp) {
  const list = loadMyList();
  let updated = null;
  for (const it of list) {
    if (normalizeMovieSlug(it.slug) === normalizeMovieSlug(slug)) {
      it.lastKnownEpisode = latestEp || it.latestEpisode || it.lastKnownEpisode || '';
      it.hasNewEpisode = false;
      updated = it;
    }
  }
  saveMyList(list);
  if (updated && isCloudSignedIn()) {
    cloudPushState({ myList: [updated] }).catch(() => {});
  }
  refreshEpisodeReleaseNotifications();
}

function markAllNotifsRead() {
  const list = loadMyList();
  const changed = [];
  for (const it of list) {
    const latestEp = it.latestEpisode || state.latestEpisodeMap[it.slug] || '';
    if (latestEp) {
      it.lastKnownEpisode = latestEp;
      it.hasNewEpisode = false;
      changed.push(it);
    }
  }
  saveMyList(list);
  if (changed.length && isCloudSignedIn()) {
    cloudPushState({ myList: changed }).catch(() => {});
  }
  refreshEpisodeReleaseNotifications();
  showToast('🔔 Đã đánh dấu đã xem tất cả thông báo tập mới');
}

function renderMyListSection() {
  const list = loadMyList();
  const countAll = list.length;

  // Always update compact Topbar ❤️ icon badge & modal header counter (zero homepage clutter!)
  const navBtn = $('openMyListBtn');
  const dot = $('myListCountDot');
  if (navBtn) navBtn.classList.toggle('has-items', countAll > 0);
  if (dot) {
    dot.style.display = countAll > 0 ? 'inline-block' : 'none';
    dot.textContent = countAll > 99 ? '99+' : String(countAll);
  }
  if ($('myListModalCount')) $('myListModalCount').textContent = String(countAll);
  if ($('clearAllMyListBtn')) $('clearAllMyListBtn').style.display = countAll > 0 ? 'inline-flex' : 'none';

  const grid = $('myListGrid');
  if (!grid) return;

  const histSlugs = new Set(loadHistory().map((h) => normalizeMovieSlug(h.slug)));
  const isNewEpItem = (item) => {
    const latestEp = item.latestEpisode || state.latestEpisodeMap[item.slug] || '';
    const knownEp = item.lastKnownEpisode || item.quality || '';
    const newNum = extractEpNumber(latestEp);
    const oldNum = extractEpNumber(knownEp);
    return Boolean(item.hasNewEpisode || (latestEp && newNum > 0 && ((oldNum > 0 && newNum > oldNum) || (!oldNum && /tập|ep|第/i.test(latestEp) && latestEp !== knownEp))));
  };

  const countNew = list.filter(isNewEpItem).length;
  const countWatching = list.filter((it) => histSlugs.has(normalizeMovieSlug(it.slug))).length;
  const countUnwatched = list.filter((it) => !histSlugs.has(normalizeMovieSlug(it.slug))).length;

  const filterBar = $('myListFilterBar');
  if (filterBar) {
    const labels = {
      all: `${tr('myListTabAll')} (${countAll})`,
      new: `${tr('myListTabNew')}${countNew ? ` (${countNew})` : ''}`,
      watching: `${tr('myListTabWatching')} (${countWatching})`,
      unwatched: `${tr('myListTabUnwatched')} (${countUnwatched})`,
    };
    filterBar.querySelectorAll('[data-mylist-tab]').forEach((btn) => {
      const tab = btn.dataset.mylistTab;
      btn.textContent = labels[tab] || tab;
      btn.classList.toggle('active', tab === state.myListTab);
      btn.onclick = () => {
        state.myListTab = tab;
        renderMyListSection();
      };
    });
  }

  let filtered = list;
  if (state.myListTab === 'new') filtered = list.filter(isNewEpItem);
  else if (state.myListTab === 'watching') filtered = list.filter((it) => histSlugs.has(normalizeMovieSlug(it.slug)));
  else if (state.myListTab === 'unwatched') filtered = list.filter((it) => !histSlugs.has(normalizeMovieSlug(it.slug)));

  const q = String($('myListSearchInput')?.value || '').trim().toLowerCase();
  if (q) {
    filtered = filtered.filter((it) => {
      const hay = `${getDisplayTitle(it)} ${it.origin_name || ''} ${it.slug || ''}`.toLowerCase();
      return hay.includes(q);
    });
  }

  grid.innerHTML = '';
  if (!list.length) {
    grid.innerHTML = '<div class="mylist-empty-state">❤️ Danh sách yêu thích đang trống.<br><span style="opacity:.75;font-size:12px;">Bấm nút <strong>+ My List</strong> trong bất kỳ bộ phim nào để lưu lại xem sau (tự đồng bộ Web ↔ Android).</span></div>';
    return;
  }
  if (!filtered.length) {
    grid.innerHTML = `<div class="mylist-empty-state">${escapeHtml(tr('noResults'))}</div>`;
    return;
  }

  filtered.slice(0, 120).forEach((item) => {
    const card = createMovieCardElement(item);
    const rmBtn = document.createElement('button');
    rmBtn.type = 'button';
    rmBtn.className = 'card-remove-history-btn';
    rmBtn.title = 'Xóa khỏi Danh sách yêu thích';
    rmBtn.setAttribute('aria-label', 'Xóa khỏi Danh sách yêu thích');
    rmBtn.textContent = '✕';
    rmBtn.onclick = (e) => {
      e.stopPropagation();
      e.preventDefault();
      removeFromMyList(item);
    };
    card.querySelector('.poster-wrap')?.appendChild(rmBtn);
    card.onclick = () => {
      $('myListModal')?.classList.remove('open');
      openMovieDetail(item, false);
    };
    grid.appendChild(card);
  });
}

function openMyListModal() {
  renderMyListSection();
  $('myListModal')?.classList.add('open');
  // Refresh from NKI Cloud when opening so any deletions done on Android sync immediately!
  const now = Date.now();
  if (isCloudSignedIn() && now - (state.lastMyListPullTs || 0) > 8000) {
    state.lastMyListPullTs = now;
    pullCloudStateAndMerge({ silent: true, force: true }).catch(() => {});
  }
}

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
  sel.innerHTML = `<option value="">${escapeHtml(tr('allCountriesOpt', { n: state.globalCountries.length }))}</option>` +
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
  setText('clearAllHistoryBtn', 'clearHistoryBtn');
  setText('myListSectionTitle', 'myListTitle');
  setText('advFilterLabelText', 'filterBtn');
  setText('advFilterApplyBtn', 'applyFiltersBtn');
  setText('advFilterResetBtn', 'resetBtn');
  setText('pwaInstallBtn', 'installPwaBtn');
  setHtml('footerTagline', 'footerTagline');
  setText('footerApkLink', 'downloadApk');
  renderCountryPicker();
  renderMyListSection();
  if ($('hudLockBtn')) $('hudLockBtn').title = tr('lockBtn');
  const isFs = Boolean(document.fullscreenElement || document.webkitFullscreenElement);
  if ($('hudFsBtn')) {
    $('hudFsBtn').title = isFs ? tr('exitFsBtn') : tr('fullscreenBtn');
    $('hudFsBtn').innerHTML = isFs ? SVG_ICONS.fsExit : SVG_ICONS.fsEnter;
  }
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
  setText('openShortcutsBtn', 'shortcutsBtn');
  setText('qualityLabelText', 'qualityLabel');
  setText('nightAudioLabelText', 'nightAudioLabel');
  setText('skipIntroDurationLabel', 'skipIntroDurationLabel');
  syncSkipIntroSettingsUi();

  // Settings & TV Cast modals
  setText('settingsTitle', 'settingsTitle');
  setHtml('settingsPrivacyBox', 'settingsPrivacy');
  setText('cloudSyncNowBtn', 'cloudSyncNowBtn');
  setText('cloudLogoutBtn', 'cloudLogoutBtn');
  setText('loadModelsBtn', 'loadModelsBtn');
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
    const raw = localStorage.getItem('chillfilm_nki_prefs_v1');
    if (!raw) {
      const browserLang = (navigator.language || '').toLowerCase();
      if (browserLang.startsWith('vi')) {
        return { ...DEFAULT_PREFS, uiLang: 'vi', primaryLang: 'vi', secondaryLang: 'en' };
      }
      return { ...DEFAULT_PREFS };
    }
    const saved = JSON.parse(raw);
    return { ...DEFAULT_PREFS, ...saved };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

function savePrefs(skipCloudPush = false, immediateCloud = false) {
  localStorage.setItem('chillfilm_nki_prefs_v1', JSON.stringify(state.prefs));
  if (!skipCloudPush && isCloudSignedIn()) {
    clearTimeout(state.prefsPushTimer);
    const pushNow = () => {
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
        skipIntroSec: state.prefs.skipIntroSec,
        geminiModel: state.prefs.geminiModel,
      };
      cloudPushState({ prefs: syncablePrefs, prefsUpdatedAt: Date.now() }).catch(() => {});
    };
    if (immediateCloud) pushNow();
    else state.prefsPushTimer = setTimeout(pushNow, 1800);
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

  // 2. Update "Continue Watching" movie history strip (preserve source & sourceId for MacCMS / Archive!)
  if (dur >= 10) {
    if (isFinished || (dur > 30 && pos / dur >= 0.85)) {
      markEpisodeWatched(movie.slug || canonicalSlug, ep);
    }
    const list = loadHistory().filter((h) => normalizeMovieSlug(h.slug) !== canonicalSlug);
    list.unshift({
      slug: movie.slug || canonicalSlug,
      source: movie.source || 'phimapi',
      sourceId: movie.sourceId || '',
      name: movie.name,
      nameEn: movie.nameEn || '',
      nameVi: movie.nameVi || '',
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

async function pullCloudStateAndMerge({ silent = true, force = false, migrateLocalMyList = false } = {}) {
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
        if (dur > 30 && pos / dur >= 0.85) {
          markEpisodeWatched(slug, epKey);
        }
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
              slug: existingHist?.slug || slug,
              source: existingHist?.source || 'phimapi',
              sourceId: existingHist?.sourceId || '',
              name: item.title || existingHist?.name || slug,
              nameEn: existingHist?.nameEn || '',
              nameVi: existingHist?.nameVi || '',
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

    // Authoritative Cloud My List Sync:
    // Cloud `data.myList` is always the single source of truth so deletions on Android or other browsers
    // immediately sync to Web and are NEVER resurrected back to Cloud!
    // Pre-login guest favorites are only migrated upward on brand-new account registration (`migrateLocalMyList: true`).
    if (Array.isArray(data.myList)) {
      const tombstones = loadMyListTombstones();
      const nowMs = Date.now();

      const bySlug = new Map();
      for (const it of data.myList) {
        if (!it || !it.slug) continue;
        const ns = normalizeMovieSlug(it.slug);
        // Protect against an in-flight delete that happened locally moments ago
        const tombTs = tombstones[ns] || 0;
        if (tombTs && nowMs - tombTs < 60_000 && tombTs >= Number(it.addedAt || 0)) continue;
        const k = it.itemKey || makeMyListKey(it);
        if (!bySlug.has(ns)) {
          bySlug.set(ns, { ...it, itemKey: k });
        }
      }

      const localOnly = [];
      if (migrateLocalMyList) {
        const localList = loadMyList();
        for (const it of localList) {
          if (!it || !it.slug) continue;
          const ns = normalizeMovieSlug(it.slug);
          if (tombstones[ns]) continue;
          if (!bySlug.has(ns)) {
            const k = it.itemKey || makeMyListKey(it);
            const entry = { ...it, itemKey: k };
            bySlug.set(ns, entry);
            localOnly.push(entry);
          }
        }
      }

      const mergedMy = Array.from(bySlug.values()).sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));
      saveMyList(mergedMy);
      renderMyListSection();
      syncMyListActionButtons();
      refreshEpisodeReleaseNotifications();
      if (localOnly.length) {
        cloudPushState({ myList: localOnly }).catch(() => {});
      }
    }

    // Merge synced subtitle preferences if server prefs exist
    if (data.prefs && typeof data.prefs === 'object') {
      const p = data.prefs;
      let prefsChanged = false;
      ['primaryLang', 'secondaryLang', 'dualSub', 'subEnabled', 'primarySize', 'secondarySize', 'primaryColor', 'secondaryColor', 'boxOpacity', 'skipIntroSec', 'geminiModel'].forEach((k) => {
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

function renderSkeletonRowsHtml(rowCount = 3, cardsPerRow = 7) {
  let html = '';
  for (let r = 0; r < rowCount; r++) {
    let cards = '';
    for (let c = 0; c < cardsPerRow; c++) {
      cards += `
        <div class="skeleton-card">
          <div class="shimmer-box skeleton-poster"></div>
          <div class="shimmer-box skeleton-line"></div>
          <div class="shimmer-box skeleton-line short"></div>
        </div>
      `;
    }
    html += `<div class="skeleton-row-wrap"><div class="shimmer-box skeleton-row-title"></div><div class="skeleton-rail">${cards}</div></div>`;
  }
  return html;
}

function renderSkeletonGridHtml(count = 12) {
  let cards = '';
  for (let i = 0; i < count; i++) {
    cards += `
      <div class="skeleton-card" style="width:100%;">
        <div class="shimmer-box skeleton-poster"></div>
        <div class="shimmer-box skeleton-line"></div>
        <div class="shimmer-box skeleton-line short"></div>
      </div>
    `;
  }
  return cards;
}

function applyFeedPayload(feed, { persistCache = false } = {}) {
  if (!feed?.ok || !Array.isArray(feed.rows) || !feed.rows.length) return false;
  state.catalogUpdatedAt = feed.updatedAt || Date.now();
  state.crawlerStatus = feed.crawlerStatus || null;
  if (Array.isArray(feed.cloudSubSlugs)) {
    feed.cloudSubSlugs.forEach((s) => state.cloudSubSlugs.add(normalizeMovieSlug(s)));
  }
  if (feed.latestEpisodeMap && typeof feed.latestEpisodeMap === 'object') {
    state.latestEpisodeMap = { ...state.latestEpisodeMap, ...feed.latestEpisodeMap };
  }
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
  renderMyListSection();
  refreshEpisodeReleaseNotifications();
  renderHomeSwimlanes();
  if (persistCache) saveCachedHomeFeed(feed);
  return true;
}

async function loadCategory(catId, { forceRefresh = false, silent = false } = {}) {
  state.activeCategory = catId;
  renderCategories();
  if (!silent) closeDetailView();

  const liftEl = document.querySelector('.content-lift');
  const swimWrap = $('homeSwimlanes');
  const gridSec = $('catalogGridSection');

  // HOME ("hot"): 0ms Instant Paint from LocalStorage SWR Cache + Background Live Refresh!
  if (catId === 'hot') {
    if (liftEl) liftEl.classList.remove('no-hero');
    $('topbar')?.classList.remove('solid');
    gridSec.style.display = 'none';
    swimWrap.style.display = 'block';

    let paintedInstant = false;
    if (!forceRefresh) {
      if (state.homeRows.length > 0 && !silent) {
        renderHomeSwimlanes();
        paintedInstant = true;
      } else {
        const cachedFeed = loadCachedHomeFeed();
        if (cachedFeed) {
          paintedInstant = applyFeedPayload(cachedFeed, { persistCache: false });
        }
      }
    }
    if (!silent && !paintedInstant) {
      swimWrap.innerHTML = renderSkeletonRowsHtml(3, 8);
    }

    try {
      const feed = await fetchLiveCatalogFeed(forceRefresh);
      if (state.activeCategory !== 'hot') return;
      if (applyFeedPayload(feed, { persistCache: true })) {
        if (forceRefresh && !silent) showToast('🔥 Đã cập nhật kho phim mới nhất!');
        return;
      }
    } catch {
      if (paintedInstant) return;
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
      renderMyListSection();
      renderHomeSwimlanes();
    } else if (!silent && !paintedInstant) {
      swimWrap.innerHTML = `<div style="text-align:center;padding:36px;color:#fca5a5;">${escapeHtml(tr('loadError'))}</div>`;
    }
    return;
  }

  // Dedicated Category Grid View — 0ms RAM SWR Cache!
  if (liftEl) liftEl.classList.remove('no-hero');
  $('topbar')?.classList.remove('solid');
  swimWrap.style.display = 'none';
  gridSec.style.display = 'block';

  const memHit = !forceRefresh ? state.browseMemCache.get(catId) : null;
  if (memHit && memHit.items?.length && Date.now() - memHit.ts < BROWSE_CACHE_TTL_MS) {
    state.movies = memHit.items;
    setHeroSlides(memHit.items.slice(0, 5));
    renderHistorySection();
    renderMovieGrid(state.movies, getCategoryLabel(catId));
    // Fresh enough (< 60s) -> skip network completely!
    if (Date.now() - memHit.ts < 60_000) return;
  } else if (!silent) {
    $('catalogGrid').innerHTML = renderSkeletonGridHtml(12);
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
  if (state.activeCategory !== catId) return;
  if (list.length) {
    state.browseMemCache.set(catId, { items: list, ts: Date.now() });
    state.movies = list;
    setHeroSlides(list.slice(0, 5));
    renderHistorySection();
    renderMovieGrid(state.movies, getCategoryLabel(catId));
  } else if (!silent && !memHit) {
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
    leftBtn.innerHTML = SVG_ICONS.chevLeft;
    leftBtn.setAttribute('aria-label', 'Scroll left');

    const rightBtn = document.createElement('button');
    rightBtn.className = 'rail-arrow right';
    rightBtn.innerHTML = SVG_ICONS.chevRight;
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

async function executeAdvancedFilter() {
  const country = ($('countryPickerSelect')?.value || '').trim();
  const genre = ($('advGenreSelect')?.value || '').trim();
  const year = ($('advYearSelect')?.value || '').trim();
  const minRating = Number($('advRatingSelect')?.value || 0);

  if (!country && !genre && !year && !minRating) {
    loadCategory('hot');
    return;
  }

  closeDetailView();
  $('heroBox').style.display = 'none';
  $('historySection').style.display = 'none';
  $('homeSwimlanes').style.display = 'none';
  $('catalogGridSection').style.display = 'block';
  document.querySelector('.content-lift')?.classList.add('no-hero');
  $('topbar')?.classList.add('solid');

  const labelParts = [];
  if (country) labelParts.push(getCategoryLabel(country));
  if (genre) labelParts.push(genre.toUpperCase());
  if (year) labelParts.push(year);
  if (minRating > 0) labelParts.push(`★ ${minRating}+`);
  const titleBase = `🎛 ${labelParts.join(' • ')}`;
  $('catalogTitle').textContent = titleBase;
  $('catalogGrid').innerHTML = renderSkeletonGridHtml(12);

  try {
    const data = await fetchCatalogBrowse({ country, genre, year, minRating: minRating || '' });
    const list = Array.isArray(data?.items) ? data.items : [];
    renderMovieGrid(list, `${titleBase} (${list.length})`);
  } catch {
    showToast(tr('loadError'));
  }
}

async function executeSearch(keyword) {
  const q = (keyword || '').trim();
  closeAllSearchDropdowns();
  if ($('aiVibeBanner')) $('aiVibeBanner').style.display = 'none';
  if (!q) {
    loadCategory(state.activeCategory);
    return;
  }
  saveSearchHistory(q);
  closeDetailView();
  $('heroBox').style.display = 'none';
  $('historySection').style.display = 'none';
  $('homeSwimlanes').style.display = 'none';
  $('catalogGridSection').style.display = 'block';
  document.querySelector('.content-lift')?.classList.add('no-hero');
  $('topbar')?.classList.add('solid');
  $('catalogTitle').textContent = tr('searchResultsTitle', { q, n: '...' });
  $('catalogGrid').innerHTML = renderSkeletonGridHtml(12);

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

/* ==================== #9 AI VIBE & MOOD NATURAL-LANGUAGE SEARCH ==================== */
async function executeAiVibeSearch(rawQuery) {
  const q = String(rawQuery || '').trim();
  closeAllSearchDropdowns();
  if (!q) {
    showToast('✨ Gõ mô tả phim bạn muốn xem (vd: "phim tiên hiệp đánh nhau cháy" hoặc "trinh thám Hàn sau 2022")!');
    $('searchInputDesktop')?.focus();
    return;
  }
  saveSearchHistory(q);
  closeDetailView();
  $('heroBox').style.display = 'none';
  $('historySection').style.display = 'none';
  $('homeSwimlanes').style.display = 'none';
  $('catalogGridSection').style.display = 'block';
  document.querySelector('.content-lift')?.classList.add('no-hero');
  $('topbar')?.classList.add('solid');
  const banner = $('aiVibeBanner');
  if (banner) {
    banner.style.display = 'flex';
    banner.innerHTML = `<span>✨ Gemini AI đang phân tích gu phim: "${escapeHtml(q)}"...</span>`;
  }
  $('catalogTitle').textContent = `✨ AI Vibe Search: "${q}"`;
  $('catalogGrid').innerHTML = renderSkeletonGridHtml(12);

  try {
    const res = await fetchAiVibeSearch(q);
    const items = Array.isArray(res?.items) ? res.items : [];
    if (banner) {
      banner.style.display = 'flex';
      banner.innerHTML = `
        <span>✨ ${escapeHtml(res?.vibeSummary || `Đã lọc ${items.length} phim phù hợp với "${q}"`)}</span>
        <button class="btn-glass" id="clearAiVibeBtn" type="button" style="padding:4px 10px;font-size:11px;flex-shrink:0;">✕ Đóng</button>
      `;
      if ($('clearAiVibeBtn')) {
        $('clearAiVibeBtn').onclick = () => {
          banner.style.display = 'none';
          loadCategory('hot');
        };
      }
    }
    renderMovieGrid(items, `✨ AI Gợi Ý cho "${q}" (${items.length})`);
  } catch (err) {
    if (banner) banner.style.display = 'none';
    showToast(`AI Search fallback: ${err?.message || 'error'}`);
    executeSearch(q);
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
  const dispTitle = getDisplayTitle(movie);
  const descText = getDisplayDesc(movie) || movie.origin_name || tr('heroDefaultDesc');
  const savedInMyList = isInMyList(movie);
  const totalSlides = state.heroMovies.length;
  const controlsHtml = totalSlides > 1
    ? `
      <div class="hero-carousel-controls">
        <button class="hero-nav-btn" id="heroPrevBtn" aria-label="Previous featured">${SVG_ICONS.chevLeftSm}</button>
        <div class="hero-dots">
          ${state.heroMovies.map((_, i) => `<button class="hero-dot ${i === state.heroIndex ? 'active' : ''}" data-hero-idx="${i}" aria-label="Slide ${i + 1}"></button>`).join('')}
        </div>
        <button class="hero-nav-btn" id="heroNextBtn" aria-label="Next featured">${SVG_ICONS.chevRightSm}</button>
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
        <h1 class="hero-title">${escapeHtml(dispTitle)}</h1>
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
          <button class="btn-nf-mylist ${savedInMyList ? 'saved' : ''}" id="heroMyListBtn" type="button">${escapeHtml(savedInMyList ? tr('inMyListBtn') : tr('addMyListBtn'))}</button>
        </div>
      </div>
    </div>
    ${controlsHtml}
  `;
  box.onmouseenter = () => { state.heroPaused = true; };
  box.onmouseleave = () => { state.heroPaused = false; };
  $('heroPlayBtn').onclick = () => openMovieDetail(movie, true);
  $('heroDetailBtn').onclick = () => openMovieDetail(movie, false);
  if ($('heroMyListBtn')) {
    $('heroMyListBtn').onclick = () => {
      const nowSaved = toggleMyList(movie);
      $('heroMyListBtn').classList.toggle('saved', nowSaved);
      $('heroMyListBtn').textContent = nowSaved ? tr('inMyListBtn') : tr('addMyListBtn');
    };
  }
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
  history.slice(0, 14).forEach((item) => {
    const card = createMovieCardElement({
      slug: item.slug,
      source: item.source || 'phimapi',
      sourceId: item.sourceId || '',
      name: item.name,
      nameEn: item.nameEn || '',
      nameVi: item.nameVi || '',
      year: `${item.epName} • ${formatTime(item.position)}`,
      quality: formatTime(item.position),
      poster: item.poster,
      progress: item.progress,
    });
    const rmBtn = document.createElement('button');
    rmBtn.type = 'button';
    rmBtn.className = 'card-remove-history-btn';
    rmBtn.title = tr('removeHistoryTitle');
    rmBtn.setAttribute('aria-label', tr('removeHistoryTitle'));
    rmBtn.textContent = '✕';
    rmBtn.onclick = (e) => {
      e.stopPropagation();
      removeHistoryItem(item.slug);
    };
    card.querySelector('.poster-wrap')?.appendChild(rmBtn);
    card.onclick = () => openMovieDetail(item, true, item.position, item.epUrl);
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
  const dispName = getDisplayTitle(movie);
  const metaBits = [
    movie.year || '',
    Array.isArray(movie.countries) && movie.countries.length ? movie.countries[0] : '',
    movie.sourceName && movie.source !== 'phimapi' ? movie.sourceName.split(' ')[0] : '',
  ].filter(Boolean);
  const metaLine = metaBits.length ? metaBits.join(' • ') : (movie.origin_name || 'HD • AI Sub');
  const posterSrc = movie.poster || './assets/icon-192.png?v=2';

  const hasCloudSub = Boolean(movie.hasCloudSub || (movie.slug && state.cloudSubSlugs.has(normalizeMovieSlug(movie.slug))));
  el.className = 'movie-card';
  el.innerHTML = `
    <div class="poster-wrap">
      <img loading="lazy" src="${escapeHtml(posterSrc)}" alt="${escapeHtml(dispName)}" onerror="if(!this.dataset.fb){this.dataset.fb='1';this.classList.add('img-fallback');this.src='./assets/icon-192.png?v=2';}" />
      <span class="card-nki-mark">NKI</span>
      <span class="card-badge">AI</span>
      <span class="card-quality">${escapeHtml((movie.episode_current || movie.quality || 'HD').slice(0, 12))}</span>
      ${hasCloudSub ? '<span class="card-cloud-sub-badge" title="Đã dịch sẵn trên NKI Cloud — mở xem có Sub ngay">☁️ SUB AI</span>' : ''}
      <div class="card-hover-play"><span>${SVG_ICONS.playCard}</span></div>
      ${movie.progress ? `<div class="card-progress"><div class="card-progress-fill" style="width:${Math.round(movie.progress * 100)}%"></div></div>` : ''}
    </div>
    <div class="card-info">
      <div class="card-title">${escapeHtml(dispName)}</div>
      <div class="card-meta">${escapeHtml(metaLine)}</div>
    </div>
  `;
  // Speculative Hover / Touch Prefetch: pre-warms detail into RAM so clicking opens in 0ms!
  let hoverPrefetchTimer = null;
  el.addEventListener('mouseenter', () => {
    hoverPrefetchTimer = setTimeout(() => prefetchMovieDetail(movie), 110);
  });
  el.addEventListener('mouseleave', () => {
    clearTimeout(hoverPrefetchTimer);
  });
  el.addEventListener('touchstart', () => prefetchMovieDetail(movie), { passive: true, once: true });
  return el;
}

/* ==================== MOVIE DETAIL (MULTI-SOURCE + VN-SAFE + 0ms PREFETCH/OPTIMISTIC SHELL) ==================== */
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

  const cacheKey = getMovieDetailCacheKey(source, slug, sourceId);
  let detail = getCachedMovieDetail(source, slug, sourceId);

  if (!detail) {
    if (!autoPlayFirst && movieObj) {
      // Instant 0ms optimistic transition to Detail screen with poster/title + shimmer!
      renderDetailOptimisticShell(movieObj);
    } else {
      showToast('⚡ Đang mở phim...');
    }
  }

  try {
    if (!detail) {
      try {
        const inFlight = state.detailInFlight.get(cacheKey);
        detail = inFlight ? await inFlight : await fetchCatalogDetail({ source, slug, sourceId });
        if (!detail) detail = await fetchCatalogDetail({ source, slug, sourceId });
        if (detail) setCachedMovieDetail(source, slug, sourceId, detail);
      } catch (apiErr) {
        if (String(apiErr?.message || '').toLowerCase().includes('blocked')) {
          closeDetailView();
          showToast('🛡️ Nội dung này bị chặn theo chính sách lọc phim sạch.');
          return;
        }
        if (source !== 'phimapi') throw apiErr;
        // Offline fallback for phimapi (strictly verify movie exists & has streams so MacCMS IDs don't render ghost pages!)
        const res = await fetch(`${PHIM_API}/phim/${encodeURIComponent(slug)}`);
        const json = await res.json();
        const m = json?.movie;
        const servers = (json?.episodes || []).map((s) => ({
          server_name: s.server_name || 'Server',
          episodes: (s.server_data || []).filter((e) => e.link_m3u8),
        })).filter((s) => s.episodes.length);
        if (m && typeof m === 'object' && (m.name || m.slug) && servers.length) {
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
          setCachedMovieDetail(source, slug, sourceId, detail);
        } else if (resumeUrl || movieObj?.epUrl) {
          // Recover directly from saved watch history stream URL so user never loses an in-progress film!
          const savedUrl = resumeUrl || movieObj.epUrl;
          detail = {
            slug,
            source: movieObj?.source || 'direct',
            sourceId: movieObj?.sourceId || '',
            name: movieObj?.name || slug,
            origin_name: movieObj?.origin_name || '',
            year: movieObj?.year || '',
            quality: 'HD',
            time: '',
            content: '',
            imdbId: '',
            type: 'single',
            poster: movieObj?.poster || '',
            backdrop: movieObj?.poster || '',
            servers: [{ server_name: 'Saved Stream', episodes: [{ name: movieObj?.epName || 'Full', slug: 'full', link_m3u8: savedUrl }] }],
          };
        } else {
          throw apiErr;
        }
      }
    }

    if (!detail) throw new Error('Not found');

    // Self-heal older History / My List items if server auto-resolved true source/sourceId/metadata!
    if (detail.source && detail.slug && detail.name && detail.name !== detail.slug) {
      try {
        const hist = loadHistory();
        let histUpdated = false;
        for (const h of hist) {
          if (normalizeMovieSlug(h.slug) === normalizeMovieSlug(detail.slug) && (h.source !== detail.source || h.sourceId !== (detail.sourceId || ''))) {
            h.source = detail.source;
            h.sourceId = detail.sourceId || '';
            if (!h.name || h.name === h.slug) h.name = detail.name;
            if (!h.poster && detail.poster) h.poster = detail.poster;
            histUpdated = true;
          }
        }
        if (histUpdated) localStorage.setItem('chillfilm_nki_history_v1', JSON.stringify(hist));

        const myList = loadMyList();
        let myUpdated = false;
        for (const it of myList) {
          if (normalizeMovieSlug(it.slug) === normalizeMovieSlug(detail.slug) && (!it.poster || it.name === it.slug)) {
            if (!it.name || it.name === it.slug) it.name = getDisplayTitle(detail);
            if (!it.poster && detail.poster) it.poster = detail.poster;
            if (!it.year && detail.year) it.year = detail.year;
            myUpdated = true;
          }
        }
        if (myUpdated) {
          saveMyList(myList);
          renderMyListSection();
        }
      } catch { /* non-fatal */ }
    }
    state.currentMovie = detail;
    state.episodesFlat = detail.servers?.[0]?.episodes || [];

    if (autoPlayFirst && state.episodesFlat.length) {
      const savedHist = resumeSec <= 0 && !resumeUrl ? getLatestMovieHistory(detail.slug) : null;
      const targetUrl = resumeUrl || savedHist?.epUrl || '';
      const targetEpName = movieObj?.epName || savedHist?.epName || '';
      const targetEpKey = targetEpName ? normalizeEpisodeKey(targetEpName) : '';
      const targetEp =
        (targetUrl && state.episodesFlat.find((e) => e.link_m3u8 === targetUrl)) ||
        (targetEpName && state.episodesFlat.find((e) => e.name === targetEpName || normalizeEpisodeKey(e.name || e.slug) === targetEpKey)) ||
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
  syncBrowserUrl({ movie: detail, playing: false }, { push: true });
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
  const dispTitle = getDisplayTitle(detail);
  const dispDesc = getDisplayDesc(detail) || tr('heroDefaultDesc');
  const origSubtitle = dispTitle !== detail.name ? detail.name : detail.origin_name;
  const savedInMyList = isInMyList(detail);
  const primarySrvEps = detail.servers[0]?.episodes || [];
  const defaultSubEp = resumeEp || primarySrvEps[0] || null;
  state.detailSelectedEp = defaultSubEp;

  const langOptionsHtml = SUBTITLE_LANGUAGES.map((l) =>
    `<option value="${escapeHtml(l.code)}" ${l.code === lang.code ? 'selected' : ''}>${escapeHtml(l.badge)} • ${escapeHtml(l.name)}</option>`
  ).join('');
  const epOptionsHtml = primarySrvEps.length > 1
    ? primarySrvEps.map((e, i) => {
        const isSel = defaultSubEp && (e.link_m3u8 === defaultSubEp.link_m3u8 || e.name === defaultSubEp.name);
        return `<option value="${i}" ${isSel ? 'selected' : ''}>Tập: ${escapeHtml(e.name)}</option>`;
      }).join('')
    : '';

  const actors = Array.isArray(detail.actors) ? detail.actors.filter(Boolean).slice(0, 10) : [];
  const directors = Array.isArray(detail.directors) ? detail.directors.filter(Boolean).slice(0, 4) : [];
  const hasScore = Number(detail.tmdbScore || 0) > 0;

  dv.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;">
      <button class="btn-glass" id="detailBackBtn">${escapeHtml(tr('backToCatalog'))}</button>
      ${resumeEp ? `<button class="btn-glass" id="detailCastBtn">${escapeHtml(tr('castToTv'))}</button>` : ''}
    </div>
    <div class="detail-hero">
      <img class="detail-poster" src="${escapeHtml(detail.poster || './assets/icon-192.png?v=2')}" alt="${escapeHtml(dispTitle)}" onerror="if(!this.dataset.fb){this.dataset.fb='1';this.src='./assets/icon-192.png?v=2';}" />
      <div style="display:flex;flex-direction:column;gap:10px;min-width:0;">
        <div class="hero-meta">
          <span class="match-score">${hasScore ? `★ ${Number(detail.tmdbScore).toFixed(1)}` : '98% Match'}</span>
          <span class="badge-ai" id="detailHeroSubBadge">AI SUB (${lang.badge})</span>
          <span style="font-size:12px;color:#b3b3b3;font-weight:700;">${escapeHtml(detail.year)} • ${escapeHtml(detail.time || detail.quality)}</span>
        </div>
        <h2 style="font-size:clamp(20px,3.2vw,32px);font-weight:900;">${escapeHtml(dispTitle)}</h2>
        ${origSubtitle ? `<div style="font-size:13px;color:#b3b3b3;">${escapeHtml(origSubtitle)}</div>` : ''}
        <p style="font-size:13.5px;color:#e5e5e5;line-height:1.6;">${escapeHtml(dispDesc)}</p>
        ${directors.length ? `<div class="detail-people-row"><span>Đạo diễn:</span>${directors.map((d) => `<button type="button" class="person-chip" data-person="${escapeHtml(d)}">${escapeHtml(d)}</button>`).join('')}</div>` : ''}
        ${actors.length ? `<div class="detail-people-row"><span>Diễn viên:</span>${actors.map((a) => `<button type="button" class="person-chip" data-person="${escapeHtml(a)}">${escapeHtml(a)}</button>`).join('')}</div>` : ''}
        <div style="display:flex;flex-wrap:wrap;gap:10px;margin-top:6px;">
          ${resumeEp ? `<button class="btn-nf-play" id="detailPlayPrimary">${escapeHtml(primaryBtnLabel)}</button>` : `<span style="color:#fca5a5;font-size:13px;">${escapeHtml(tr('noStream'))}</span>`}
          ${resumeEp ? `<button class="btn-glass" id="detailPartyBtn" type="button" style="padding:10px 15px;font-size:13.5px;border-color:rgba(0,230,118,0.45);color:#86efac;">🍿 Xem chung</button>` : ''}
          <button class="btn-nf-mylist ${savedInMyList ? 'saved' : ''}" id="detailMyListBtn" type="button">${escapeHtml(savedInMyList ? tr('inMyListBtn') : tr('addMyListBtn'))}</button>
          <button class="btn-glass" id="detailShareBtn" type="button" style="padding:10px 15px;font-size:13.5px;">${escapeHtml(tr('shareMovieBtn'))}</button>
          <button class="btn-glass" id="detailTrailerBtn" type="button" style="padding:10px 15px;font-size:13.5px;">${escapeHtml(tr('trailerBtn'))}</button>
        </div>
      </div>
    </div>

    ${defaultSubEp ? `
    <section class="detail-sub-card" id="detailSubCard" aria-label="AI Subtitle Pre-Translator">
      <div class="detail-sub-head">
        <div class="detail-sub-title-row">
          <span class="detail-sub-title">✨ Tự dịch phụ đề AI (NKI)</span>
          <span class="detail-sub-badge" id="detailSubBadge">Đang kiểm tra Sub...</span>
        </div>
        <div class="detail-sub-controls">
          ${epOptionsHtml ? `<select class="detail-sub-select" id="detailSubEpSelect" title="Chọn tập để dịch trước">${epOptionsHtml}</select>` : ''}
          <select class="detail-sub-select" id="detailSubLangSelect" title="Ngôn ngữ dịch phụ đề">${langOptionsHtml}</select>
          <select class="detail-sub-select" id="detailSubScopeSelect" title="Phạm vi dịch Audio AI (khi chưa có SRT)">
            <option value="lookahead10">⚡ Dịch trước 10 phút (Xem nhanh)</option>
            <option value="full">🎬 Dịch trọn bộ 100% tập</option>
          </select>
        </div>
      </div>

      <div class="detail-sub-actions">
        <button class="btn-ai-sub-primary" id="detailAutoSubBtn" type="button">✨ Tự dịch Sub AI ngay</button>
        <button class="btn-nf-play" id="detailSubWatchNowBtn" type="button" style="display:none;padding:9px 15px;font-size:13px;">▶ Xem ngay với Sub</button>
        <button class="btn-glass" id="detailDownloadSrtBtn" type="button" style="padding:8px 13px;font-size:12.5px;">⬇️ Tải .SRT</button>
        <label class="btn-glass" style="cursor:pointer;padding:8px 13px;font-size:12.5px;">
          📂 Nạp .SRT &amp; Dịch
          <input type="file" id="detailLocalSubFile" accept=".srt,.vtt,.ass,.ssa,.txt" style="display:none;" />
        </label>
        <button class="btn-glass" id="detailSubCloudBtn" type="button" style="display:none;padding:8px 13px;font-size:12.5px;border-color:rgba(0,230,118,0.45);color:#86efac;">☁️ Đăng nhập NKI Cloud</button>
      </div>

      <div class="detail-sub-progress-wrap" id="detailSubProgressWrap">
        <div class="detail-sub-progress-track">
          <div class="detail-sub-progress-fill" id="detailSubProgressFill"></div>
        </div>
        <div class="detail-sub-progress-meta">
          <span id="detailSubStatusText">Bấm "Tự dịch Sub AI ngay" để dịch trước phụ đề (SRT 100% trong ~10s hoặc tự tách âm thanh HLS dịch trước 10 phút).</span>
          <span class="detail-sub-pct" id="detailSubPercent">0%</span>
        </div>
      </div>

      <!-- #2 AI Character & Terminology Glossary (Persistent across episodes) -->
      <details class="detail-glossary-details" id="detailGlossaryDetails">
        <summary id="detailGlossarySummary">🧠 Từ điển nhân vật &amp; Xưng hô AI Glossary (${(detail.glossary?.terms || []).length} thuật ngữ)</summary>
        <div style="margin-top:8px;display:flex;flex-direction:column;gap:8px;">
          <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center;">
            <button class="btn-grad" id="detailGenGlossaryBtn" type="button" style="padding:6px 12px;font-size:11.5px;">✨ AI Tự tạo từ điển phim</button>
            <span style="font-size:11px;color:#9ca3af;">Giữ đồng nhất tên nhân vật, chiêu thức &amp; xưng hô xuyên suốt mọi tập phim.</span>
          </div>
          <div class="glossary-chips" id="detailGlossaryChips"></div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;">
            <input class="field-input" id="glossarySrcInput" placeholder="Tên gốc (vd: 萧炎)" style="flex:1;min-width:110px;padding:6px 9px;font-size:12px;" />
            <input class="field-input" id="glossaryDstInput" placeholder="Dịch cố định (vd: Tiêu Viêm)" style="flex:1;min-width:130px;padding:6px 9px;font-size:12px;" />
            <button class="btn-glass" id="glossaryAddTermBtn" type="button" style="padding:6px 11px;font-size:12px;">+ Thêm</button>
          </div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;">
            <input class="field-input" id="glossaryToneInput" value="${escapeHtml(detail.glossary?.tone || '')}" placeholder="Quy tắc xưng hô / văn phong (vd: cổ trang tiên hiệp: huynh/đệ, tiền bối/tại hạ)" style="flex:1;min-width:180px;padding:6px 9px;font-size:12px;" />
            <button class="btn-glass" id="glossarySaveToneBtn" type="button" style="padding:6px 11px;font-size:12px;">💾 Lưu</button>
          </div>
        </div>
      </details>
    </section>
    ` : ''}

    <div id="detailServersWrap" style="margin-top:22px;"></div>
    <div id="detailSimilarWrap" class="similar-section"></div>
  `;

  $('detailBackBtn').onclick = closeDetailView;
  if (resumeEp) {
    $('detailPlayPrimary').onclick = () => openPlayer(detail, resumeEp, resumeSec);
    $('detailCastBtn').onclick = () => castCurrentToTv(detail, resumeEp, resumeSec);
  }
  if ($('detailMyListBtn')) {
    $('detailMyListBtn').onclick = () => {
      const nowSaved = toggleMyList(detail);
      $('detailMyListBtn').classList.toggle('saved', nowSaved);
      $('detailMyListBtn').textContent = nowSaved ? tr('inMyListBtn') : tr('addMyListBtn');
    };
  }
  if ($('detailShareBtn')) {
    $('detailShareBtn').onclick = () => shareCurrentMovie(detail, resumeEp);
  }
  if ($('detailPartyBtn') && resumeEp) {
    $('detailPartyBtn').onclick = () => openPartyHubModal('create', { movie: detail, episode: resumeEp });
  }
  if ($('detailTrailerBtn')) {
    $('detailTrailerBtn').onclick = () => openTrailerModal(detail);
  }
  dv.querySelectorAll('[data-person]').forEach((chip) => {
    chip.onclick = () => {
      const name = chip.dataset.person || '';
      if (name) {
        if ($('searchInputDesktop')) $('searchInputDesktop').value = name;
        executeSearch(name);
      }
    };
  });

  // Bind Detail Page AI Subtitle Pre-Translator Card
  if (defaultSubEp) {
    const getPickedDetailEp = () => {
      const sel = $('detailSubEpSelect');
      if (sel && primarySrvEps[Number(sel.value)]) {
        state.detailSelectedEp = primarySrvEps[Number(sel.value)];
      }
      return state.detailSelectedEp || defaultSubEp;
    };

    if ($('detailSubEpSelect')) {
      $('detailSubEpSelect').onchange = () => {
        const ep = getPickedDetailEp();
        refreshDetailSubCardForSelection(detail, ep);
      };
    }

    if ($('detailSubLangSelect')) {
      $('detailSubLangSelect').onchange = () => {
        const pickedLang = $('detailSubLangSelect').value || 'vi';
        state.prefs.primaryLang = pickedLang;
        savePrefs();
        const lObj = resolveLanguage(pickedLang);
        if ($('detailHeroSubBadge')) $('detailHeroSubBadge').textContent = `AI SUB (${lObj.badge})`;
        refreshDetailSubCardForSelection(detail, getPickedDetailEp());
      };
    }

    if ($('detailSubScopeSelect') && state.subJob.active && isSubJobFor(detail, defaultSubEp, lang.code)) {
      $('detailSubScopeSelect').value = state.subJob.scope || 'lookahead10';
    }

    if ($('detailAutoSubBtn')) {
      $('detailAutoSubBtn').onclick = () => {
        const ep = getPickedDetailEp();
        const langCode = $('detailSubLangSelect')?.value || state.prefs.primaryLang || 'vi';
        const scope = $('detailSubScopeSelect')?.value || 'lookahead10';
        const epStartSec = getSavedEpisodePosition(detail.slug, ep);
        if (state.subJob.active && isSubJobFor(detail, ep, langCode)) {
          stopActiveSubJob(false, true);
          refreshDetailSubCardForSelection(detail, ep);
          return;
        }
        startUnifiedAiSubJob({
          movie: detail,
          episode: ep,
          langCode,
          scope,
          startSec: epStartSec,
          initiatedFrom: 'detail',
          forceRetranslate: $('detailAutoSubBtn')?.dataset?.retranslate === '1',
        });
      };
    }

    if ($('detailSubWatchNowBtn')) {
      $('detailSubWatchNowBtn').onclick = () => {
        const ep = getPickedDetailEp();
        const epStartSec = getSavedEpisodePosition(detail.slug, ep);
        state.episodesFlat = primarySrvEps.length ? primarySrvEps : state.episodesFlat;
        openPlayer(detail, ep, epStartSec);
      };
    }

    if ($('detailSubCloudBtn')) {
      $('detailSubCloudBtn').onclick = () => openSettingsModal();
    }

    if ($('detailLocalSubFile')) {
      $('detailLocalSubFile').onchange = async (ev) => {
        const f = ev.target.files?.[0];
        if (!f) return;
        const raw = await f.text();
        const cues = parseSubtitleText(raw);
        ev.target.value = '';
        if (!cues.length) {
          showToast('File phụ đề không hợp lệ hoặc rỗng!');
          return;
        }
        const ep = getPickedDetailEp();
        const langCode = $('detailSubLangSelect')?.value || state.prefs.primaryLang || 'vi';
        startUnifiedAiSubJob({
          movie: detail,
          episode: ep,
          langCode,
          scope: 'full',
          startSec: 0,
          initiatedFrom: 'detail',
          forceRetranslate: true,
          localCues: cues,
          localLabel: f.name,
        });
      };
    }

    if ($('detailDownloadSrtBtn')) {
      $('detailDownloadSrtBtn').onclick = async () => {
        const ep = getPickedDetailEp();
        const langCode = $('detailSubLangSelect')?.value || state.prefs.primaryLang || 'vi';
        let cuesToSave = [];
        if (isSubJobFor(detail, ep, langCode) && state.subJob.cues?.length >= 3) {
          cuesToSave = state.subJob.cues;
        } else {
          const cached = await loadCachedEpisodeSub(buildSubCacheKey(detail.slug, ep, langCode));
          if (cached?.primaryCues?.length >= 3) cuesToSave = cached.primaryCues;
        }
        if (!cuesToSave.length) {
          showToast('Chưa có phụ đề đã dịch cho tập này — hãy bấm "✨ Tự dịch Sub AI ngay" trước!');
          return;
        }
        const safeFile = `${normalizeMovieSlug(detail.slug)}-${normalizeEpisodeKey(ep.name || ep.slug)}-${langCode}.srt`;
        downloadSrtFile(cuesToSave, safeFile);
        showToast(`⬇️ Đã tải file ${safeFile} (${cuesToSave.length} câu)!`);
      };
    }

    // #2 AI Glossary Editor & Generator inside Detail Card
    const renderGlossaryBox = () => {
      const gl = detail.glossary || { terms: [], tone: '' };
      const terms = Array.isArray(gl.terms) ? gl.terms : [];
      if ($('detailGlossarySummary')) {
        $('detailGlossarySummary').textContent = `🧠 Từ điển nhân vật & Xưng hô AI Glossary (${terms.length} thuật ngữ)`;
      }
      const chipsWrap = $('detailGlossaryChips');
      if (chipsWrap) {
        chipsWrap.innerHTML = terms.length
          ? terms.map((t, idx) => `<span class="glossary-chip">${escapeHtml(t.src)} → <strong>${escapeHtml(t.dst)}</strong> <button type="button" data-del-gl="${idx}" style="margin-left:4px;color:#fca5a5;font-size:10px;">✕</button></span>`).join('')
          : '<span style="font-size:11.5px;color:#9ca3af;">Chưa có từ điển — bấm "✨ AI Tự tạo từ điển phim" để AI tự trích xuất tên nhân vật &amp; xưng hô!</span>';
        chipsWrap.querySelectorAll('[data-del-gl]').forEach((delBtn) => {
          delBtn.onclick = async () => {
            const idx = Number(delBtn.dataset.delGl);
            terms.splice(idx, 1);
            detail.glossary = { terms, tone: $('glossaryToneInput')?.value.trim() || gl.tone || '' };
            renderGlossaryBox();
            saveMovieGlossary({ slug: detail.slug, ...detail.glossary }).catch(() => {});
          };
        });
      }
      if ($('glossaryToneInput') && gl.tone && !$('glossaryToneInput').value) {
        $('glossaryToneInput').value = gl.tone;
      }
    };
    renderGlossaryBox();

    if ($('detailGenGlossaryBtn')) {
      $('detailGenGlossaryBtn').onclick = async () => {
        const btn = $('detailGenGlossaryBtn');
        const oldTxt = btn.textContent;
        btn.disabled = true;
        btn.textContent = '⏳ AI đang phân tích nhân vật...';
        try {
          const pickedLang = $('detailSubLangSelect')?.value || state.prefs.primaryLang || 'vi';
          const gl = await fetchMovieGlossary({
            slug: detail.slug,
            title: getDisplayTitle(detail),
            originTitle: detail.origin_name || detail.name || '',
            synopsis: getDisplayDesc(detail),
            genres: detail.genres || [],
            lang: pickedLang,
            generate: true,
            force: true,
          });
          detail.glossary = gl;
          if ($('glossaryToneInput')) $('glossaryToneInput').value = gl.tone || '';
          renderGlossaryBox();
          showToast(`🧠 Đã tạo AI Glossary (${(gl.terms || []).length} tên nhân vật / thuật ngữ)!`);
        } catch (err) {
          showToast(`Lỗi tạo từ điển: ${err?.message || 'error'}`);
        } finally {
          btn.disabled = false;
          btn.textContent = oldTxt;
        }
      };
    }

    if ($('glossaryAddTermBtn')) {
      $('glossaryAddTermBtn').onclick = async () => {
        const src = ($('glossarySrcInput')?.value || '').trim();
        const dst = ($('glossaryDstInput')?.value || '').trim();
        if (!src || !dst) return showToast('Nhập cả tên gốc và tên dịch cố định!');
        const gl = detail.glossary || { terms: [], tone: '' };
        gl.terms = [...(gl.terms || []).filter((t) => t.src !== src), { src, dst }].slice(0, 30);
        gl.tone = ($('glossaryToneInput')?.value || gl.tone || '').trim();
        detail.glossary = gl;
        $('glossarySrcInput').value = '';
        $('glossaryDstInput').value = '';
        renderGlossaryBox();
        await saveMovieGlossary({ slug: detail.slug, ...gl }).catch(() => {});
        showToast(`💾 Đã lưu thuật ngữ: ${src} → ${dst}`);
      };
    }

    if ($('glossarySaveToneBtn')) {
      $('glossarySaveToneBtn').onclick = async () => {
        const gl = detail.glossary || { terms: [], tone: '' };
        gl.tone = ($('glossaryToneInput')?.value || '').trim();
        detail.glossary = gl;
        await saveMovieGlossary({ slug: detail.slug, ...gl }).catch(() => {});
        showToast('💾 Đã lưu quy tắc xưng hô cho toàn bộ phim!');
      };
    }

    refreshDetailSubCardForSelection(detail, defaultSubEp);
  }

  const wrap = $('detailServersWrap');
  const CHUNK_SIZE = 50;
  detail.servers.forEach((srv) => {
    const eps = Array.isArray(srv.episodes) ? srv.episodes : [];
    const block = document.createElement('div');
    block.style.marginBottom = '20px';

    const watchedCount = eps.filter((ep) => isEpisodeWatched(detail.slug, ep)).length;
    const headHtml = `<div class="section-title" style="margin-bottom:8px;">${escapeHtml(tr('episodesCount', { srv: srv.server_name, n: eps.length }))}${watchedCount > 0 ? ` <span style="font-size:11.5px;color:#34d399;font-weight:700;">• ✓ ${watchedCount}/${eps.length} ${escapeHtml(tr('watchedMark'))}</span>` : ''}</div>`;
    block.innerHTML = headHtml;

    const grid = document.createElement('div');
    grid.className = 'ep-grid';

    // Smart episode search + chunk range pills + sort order for long series (> 24 eps)
    let epSearchQuery = '';
    let sortDesc = false;
    let activeChunk = 0;

    // Auto-select chunk containing the user's resume episode!
    if (eps.length > CHUNK_SIZE && resumeEp) {
      const rIdx = eps.findIndex((e) => e.link_m3u8 === resumeEp.link_m3u8 || e.name === resumeEp.name);
      if (rIdx >= 0) activeChunk = Math.floor(rIdx / CHUNK_SIZE);
    }

    let chunkBar = null;
    if (eps.length > 24) {
      const toolbar = document.createElement('div');
      toolbar.className = 'ep-toolbar';
      const searchInput = document.createElement('input');
      searchInput.type = 'search';
      searchInput.className = 'ep-search-input';
      searchInput.placeholder = tr('epSearchPlaceholder');
      searchInput.oninput = (ev) => {
        epSearchQuery = ev.target.value.trim().toLowerCase();
        renderServerEpisodes();
      };
      const sortBtn = document.createElement('button');
      sortBtn.type = 'button';
      sortBtn.className = 'btn-glass';
      sortBtn.style.cssText = 'padding:6px 10px;font-size:11.5px;';
      sortBtn.textContent = tr('epSortAsc');
      sortBtn.onclick = () => {
        sortDesc = !sortDesc;
        sortBtn.textContent = sortDesc ? tr('epSortDesc') : tr('epSortAsc');
        renderServerEpisodes();
      };
      toolbar.appendChild(searchInput);
      toolbar.appendChild(sortBtn);
      block.appendChild(toolbar);

      if (eps.length > CHUNK_SIZE) {
        chunkBar = document.createElement('div');
        chunkBar.className = 'ep-chunk-bar';
        block.appendChild(chunkBar);
      }
    }

    const renderServerEpisodes = () => {
      const ordered = sortDesc ? eps.slice().reverse() : eps.slice();
      let visible = ordered;
      if (epSearchQuery) {
        visible = ordered.filter((ep) => String(ep.name || ep.slug || '').toLowerCase().includes(epSearchQuery));
        if (chunkBar) chunkBar.style.display = 'none';
      } else if (ordered.length > CHUNK_SIZE) {
        const totalChunks = Math.ceil(ordered.length / CHUNK_SIZE);
        if (activeChunk >= totalChunks) activeChunk = 0;
        if (chunkBar) {
          chunkBar.style.display = 'flex';
          chunkBar.innerHTML = '';
          for (let c = 0; c < totalChunks; c++) {
            const startIdx = c * CHUNK_SIZE;
            const endIdx = Math.min(ordered.length, startIdx + CHUNK_SIZE);
            const firstLabel = ordered[startIdx]?.name?.replace(/^Tập\s*/i, '') || String(startIdx + 1);
            const lastLabel = ordered[endIdx - 1]?.name?.replace(/^Tập\s*/i, '') || String(endIdx);
            const cb = document.createElement('button');
            cb.type = 'button';
            cb.className = `ep-chunk-btn ${c === activeChunk ? 'active' : ''}`;
            cb.textContent = `${firstLabel} – ${lastLabel}`;
            cb.onclick = () => {
              activeChunk = c;
              renderServerEpisodes();
            };
            chunkBar.appendChild(cb);
          }
        }
        visible = ordered.slice(activeChunk * CHUNK_SIZE, (activeChunk + 1) * CHUNK_SIZE);
      }

      grid.innerHTML = '';
      if (!visible.length) {
        grid.innerHTML = `<div style="grid-column:1/-1;color:#9ca3af;font-size:12px;padding:8px 0;">${escapeHtml(tr('noResults'))}</div>`;
        return;
      }
      visible.forEach((ep) => {
        const epSavedSec = getSavedEpisodePosition(detail.slug, ep);
        const watched = isEpisodeWatched(detail.slug, ep);
        const b = document.createElement('button');
        b.className = `ep-btn ${epSavedSec > 5 ? 'active' : ''} ${watched ? 'watched' : ''}`;
        const checkHtml = watched ? '<span class="ep-watched-check" title="Đã xem">✓</span>' : '';
        b.innerHTML = epSavedSec > 5
          ? `<div>${escapeHtml(ep.name)}${checkHtml}</div><div style="font-size:10px;opacity:.88;margin-top:2px;">▶ ${formatTime(epSavedSec)}</div>`
          : `${escapeHtml(ep.name)}${checkHtml}`;
        b.onclick = () => {
          state.episodesFlat = eps;
          openPlayer(detail, ep, epSavedSec);
        };
        grid.appendChild(b);
      });
    };

    renderServerEpisodes();
    block.appendChild(grid);
    wrap.appendChild(block);
  });

  // More Like This (Similar Movies Row)
  const simWrap = $('detailSimilarWrap');
  if (simWrap && Array.isArray(detail.similar) && detail.similar.length > 0) {
    simWrap.innerHTML = `
      <div class="section-head"><div class="section-title">${escapeHtml(tr('moreLikeThis'))}</div></div>
      <div class="row-rail-wrap"><div class="row-rail" id="detailSimilarRail"></div></div>
    `;
    const rail = $('detailSimilarRail');
    detail.similar.forEach((simMovie) => {
      const card = createMovieCardElement(simMovie);
      card.onclick = () => openMovieDetail(simMovie, false);
      rail.appendChild(card);
    });
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function closeDetailView() {
  $('detailView').classList.remove('active');
  $('homeView').style.display = 'block';
  if ($('heroBox').style.display !== 'none') {
    $('topbar')?.classList.remove('solid');
  }
  syncBrowserUrl({}, { push: true });
}

/* ==================== CHILL-PLAYER ENGINE (LAZY WEB AUDIO 200% BOOST + EQ + TRUE BYPASS + ANTI-CLIP LIMITER) ==================== */
function needsWebAudioDsp() {
  return Boolean(
    state.volumeScale > 1.01
    || (state.eqPreset && state.eqPreset !== 'FLAT')
    || state.prefs.nightAudio
    || state.audioSubActive
  );
}

function disconnectAudioCaptureTap() {
  if (!state.audioCaptureConnected) return;
  try {
    state.audioSourceNode?.disconnect(state.audioCaptureDest);
  } catch { /* ignore */ }
  state.audioCaptureConnected = false;
}

function connectAudioCaptureTap(videoEl) {
  ensureWebAudioDsp(videoEl || $('videoEl'), { force: true });
  if (!state.audioCtx || !state.audioSourceNode) return null;
  if (!state.audioCaptureDest) {
    try {
      state.audioCaptureDest = state.audioCtx.createMediaStreamDestination();
    } catch {
      state.audioCaptureDest = null;
      return null;
    }
  }
  if (!state.audioCaptureConnected && state.audioCaptureDest) {
    try {
      state.audioSourceNode.connect(state.audioCaptureDest);
      state.audioCaptureConnected = true;
    } catch { /* ignore */ }
  }
  return state.audioCaptureDest;
}

function rewireWebAudioChain() {
  const ctx = state.audioCtx;
  const src = state.audioSourceNode;
  const gain = state.gainNode;
  if (!ctx || !src || !gain) return;
  const { low, mid, high } = state.eqNodes || {};
  const nightComp = state.compressorNode;
  const nightMakeup = state.nightMakeupNode;
  const limiter = state.limiterNode;

  // Disconnect internal signal nodes safely (keep optional audioCaptureDest tap intact if live recording)
  [src, low, mid, high, nightComp, nightMakeup, gain, limiter].forEach((n) => {
    try { n?.disconnect(); } catch { /* ignore */ }
  });

  const needEq = Boolean(state.eqPreset && state.eqPreset !== 'FLAT' && low && mid && high);
  const needNight = Boolean(state.prefs.nightAudio && nightComp && nightMakeup);
  const needLimiter = Boolean((state.volumeScale > 1.01 || needEq || needNight) && limiter);

  let cursor = src;
  if (needEq) {
    cursor.connect(low);
    low.connect(mid);
    mid.connect(high);
    cursor = high;
  }
  if (needNight) {
    cursor.connect(nightComp);
    nightComp.connect(nightMakeup);
    cursor = nightMakeup;
  }
  cursor.connect(gain);
  if (needLimiter) {
    gain.connect(limiter);
    limiter.connect(ctx.destination);
  } else {
    // True bit-clean transparent path (zero compressor/EQ coloration!)
    gain.connect(ctx.destination);
  }
  if (state.audioCaptureConnected && state.audioCaptureDest) {
    try { src.connect(state.audioCaptureDest); } catch { /* ignore */ }
  }
}

function ensureWebAudioDsp(videoEl, { force = false } = {}) {
  if (!videoEl) return;
  if (state.audioCtx) {
    if (state.audioCtx.state === 'suspended') state.audioCtx.resume().catch(() => {});
    return;
  }
  // Keep normal playback 100% on native browser/OS <video> audio pipeline to avoid Chromium
  // MediaElementAudioSourceNode + MSE clock-drift / crackle / muffled audio over long episodes!
  if (!force && !needsWebAudioDsp()) return;
  try {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    // Use 'playback' latencyHint for large glitch-free movie buffer instead of tiny 'interactive' buffer!
    let ctx;
    try {
      ctx = new AC({ latencyHint: 'playback' });
    } catch {
      ctx = new AC();
    }
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

    // Musical Night Audio compressor (only wired into graph when Night Mode is ON!)
    const compressor = ctx.createDynamicsCompressor();
    compressor.threshold.value = -20;
    compressor.knee.value = 18;
    compressor.ratio.value = 4.5;
    compressor.attack.value = 0.012;
    compressor.release.value = 0.25;
    const nightMakeup = ctx.createGain();
    nightMakeup.gain.value = 1.22;

    const gain = ctx.createGain();
    gain.gain.value = state.volumeScale;

    // Post-gain Safety Output Limiter prevents digital clipping/crackling at 120%-200% boost or EQ boost!
    const limiter = ctx.createDynamicsCompressor();
    limiter.threshold.value = -1.5;
    limiter.knee.value = 4;
    limiter.ratio.value = 14;
    limiter.attack.value = 0.002;
    limiter.release.value = 0.08;

    state.audioCtx = ctx;
    state.audioSourceNode = src;
    state.gainNode = gain;
    state.compressorNode = compressor;
    state.nightMakeupNode = nightMakeup;
    state.limiterNode = limiter;
    state.eqNodes = { low, mid, high };
    state.audioCaptureDest = null;
    state.audioCaptureConnected = false;

    applyEqPreset(state.eqPreset);
    applyNightAudioMode(state.prefs.nightAudio);
    rewireWebAudioChain();
  } catch {
    // Graceful fallback if browser blocks Web Audio on opaque media
  }
}

function applyNightAudioMode(enabled) {
  state.prefs.nightAudio = Boolean(enabled);
  if (state.prefs.nightAudio) {
    ensureWebAudioDsp($('videoEl'), { force: true });
  }
  if (state.audioCtx) {
    rewireWebAudioChain();
  }
  if ($('nightAudioToggle')) $('nightAudioToggle').checked = state.prefs.nightAudio;
}

function renderHlsQualityPicker(hls) {
  const wrap = $('qualityChipsWrap');
  const statsEl = $('streamStatsBadge');
  if (!wrap) return;
  const levels = Array.isArray(hls?.levels) ? hls.levels : [];
  state.hlsLevels = levels;
  if (!levels.length) {
    wrap.innerHTML = `<button class="pill-choice active" data-quality="-1" type="button">${escapeHtml(tr('qualityAuto'))}</button>`;
    if (statsEl) statsEl.textContent = 'Direct Stream';
    return;
  }

  // Deduplicate by height (keep highest bitrate per resolution)
  const byHeight = new Map();
  levels.forEach((lv, idx) => {
    const h = lv.height || 0;
    const label = h > 0 ? `${h}p` : `L${idx + 1}`;
    const prev = byHeight.get(label);
    if (!prev || (lv.bitrate || 0) > (prev.bitrate || 0)) {
      byHeight.set(label, { idx, height: h, bitrate: lv.bitrate || 0, label });
    }
  });
  const sorted = Array.from(byHeight.values()).sort((a, b) => b.height - a.height);

  // Auto-apply saved preferred quality if matching
  const pref = String(state.prefs.preferredQuality || 'auto');
  if (pref !== 'auto' && hls.currentLevel === -1) {
    const match = sorted.find((x) => x.label === pref);
    if (match) {
      hls.currentLevel = match.idx;
      state.currentQualityLevel = match.idx;
    }
  }

  const curLv = hls.currentLevel;
  const chips = [
    `<button class="pill-choice ${curLv === -1 ? 'active' : ''}" data-quality="-1" type="button">${escapeHtml(tr('qualityAuto'))}</button>`,
    ...sorted.map((item) => `<button class="pill-choice ${curLv === item.idx ? 'active' : ''}" data-quality="${item.idx}" data-qlabel="${escapeHtml(item.label)}" type="button">${escapeHtml(item.label)}</button>`),
  ];
  wrap.innerHTML = chips.join('');
  wrap.querySelectorAll('[data-quality]').forEach((btn) => {
    btn.onclick = () => {
      const targetIdx = Number(btn.dataset.quality);
      const qLabel = btn.dataset.qlabel || 'auto';
      if (state.hls) {
        state.hls.currentLevel = targetIdx;
        state.currentQualityLevel = targetIdx;
      }
      state.prefs.preferredQuality = targetIdx === -1 ? 'auto' : qLabel;
      savePrefs();
      renderHlsQualityPicker(state.hls);
      showGestureToast(`🎬 ${targetIdx === -1 ? tr('qualityAuto') : qLabel}`);
    };
  });
  updateStreamStatsBadge();
}

function updateStreamStatsBadge() {
  const statsEl = $('streamStatsBadge');
  if (!statsEl) return;
  const video = $('videoEl');
  const hls = state.hls;
  let bufAhead = 0;
  try {
    if (video?.buffered?.length) {
      const cur = video.currentTime || 0;
      for (let i = 0; i < video.buffered.length; i++) {
        if (video.buffered.start(i) <= cur + 0.5 && video.buffered.end(i) >= cur) {
          bufAhead = Math.max(0, Math.round(video.buffered.end(i) - cur));
          break;
        }
      }
    }
  } catch { /* ignore */ }
  if (hls && Array.isArray(hls.levels) && hls.levels.length) {
    const activeIdx = hls.currentLevel >= 0 ? hls.currentLevel : hls.loadLevel;
    const lv = hls.levels[activeIdx];
    const resStr = lv?.height ? `${lv.height}p` : (video?.videoHeight ? `${video.videoHeight}p` : 'HLS');
    const mbps = lv?.bitrate ? `${(lv.bitrate / 1_000_000).toFixed(1)} Mbps` : '';
    const modePrefix = hls.currentLevel === -1 ? `${tr('qualityAuto')} (${resStr})` : resStr;
    statsEl.textContent = [modePrefix, mbps, bufAhead > 0 ? `Buffer ${bufAhead}s` : ''].filter(Boolean).join(' • ');
  } else if (video?.videoHeight) {
    statsEl.textContent = [`${video.videoHeight}p`, bufAhead > 0 ? `Buffer ${bufAhead}s` : ''].filter(Boolean).join(' • ');
  }
}

function syncPlayIcons(isPaused) {
  if ($('playPauseBtn')) $('playPauseBtn').innerHTML = isPaused ? SVG_ICONS.playLg : SVG_ICONS.pauseLg;
  if ($('hudPlayBtn')) $('hudPlayBtn').innerHTML = isPaused ? SVG_ICONS.playSm : SVG_ICONS.pauseSm;
  if ($('miniPlayBtn')) $('miniPlayBtn').innerHTML = isPaused ? SVG_ICONS.playMini : SVG_ICONS.pauseMini;
}

function applyVolumeBoost(scale) {
  const prevBoosted = state.volumeScale > 1.01;
  state.volumeScale = Math.max(0, Math.min(2.0, scale));
  const nowBoosted = state.volumeScale > 1.01;
  if (state.volumeScale > 0.02) {
    state.lastNonZeroVolume = state.volumeScale;
  }
  const video = $('videoEl');
  if (nowBoosted && !state.gainNode) {
    ensureWebAudioDsp(video, { force: true });
  }
  if (state.gainNode && state.audioCtx) {
    video.volume = 1.0;
    video.muted = state.volumeScale <= 0.001;
    try {
      state.gainNode.gain.setTargetAtTime(state.volumeScale, state.audioCtx.currentTime, 0.015);
    } catch {
      state.gainNode.gain.value = state.volumeScale;
    }
    if (prevBoosted !== nowBoosted) {
      rewireWebAudioChain();
    }
  } else if (video) {
    video.muted = state.volumeScale <= 0.001;
    video.volume = Math.min(1.0, state.volumeScale);
  }
  const pct = Math.round(state.volumeScale * 100);
  if ($('boostSlider')) $('boostSlider').value = pct;
  if ($('boostValLabel')) $('boostValLabel').textContent = pct > 100 ? `${pct}% BOOST` : `${pct}%`;

  const hudVol = $('hudVolSlider');
  if (hudVol) {
    hudVol.value = pct;
    hudVol.style.setProperty('--pct', `${Math.min(100, pct / 2)}%`);
    hudVol.classList.toggle('boosted', pct > 100);
  }
  const hudPct = $('hudVolPct');
  if (hudPct) {
    hudPct.textContent = `${pct}%`;
    hudPct.classList.toggle('boosted', pct > 100);
  }
  const muteBtn = $('hudMuteBtn');
  if (muteBtn) {
    muteBtn.innerHTML = pct === 0 ? SVG_ICONS.volMute : pct < 55 ? SVG_ICONS.volLow : SVG_ICONS.volHigh;
  }
  if ($('hudSpeedBoostBtn')) {
    $('hudSpeedBoostBtn').textContent = pct > 100 ? `🔊 ${pct}%` : `${state.speed}x`;
  }
}

function toggleMute() {
  if (state.volumeScale > 0.01) {
    state.lastNonZeroVolume = state.volumeScale;
    applyVolumeBoost(0);
    showGestureToast('🔇 Muted');
  } else {
    const restored = state.lastNonZeroVolume > 0.05 ? state.lastNonZeroVolume : 1.0;
    applyVolumeBoost(restored);
    showGestureToast(`🔊 ${Math.round(restored * 100)}%`);
  }
}

function applyEqPreset(preset) {
  state.eqPreset = preset || 'FLAT';
  if (state.eqPreset !== 'FLAT' && !state.eqNodes) {
    ensureWebAudioDsp($('videoEl'), { force: true });
  }
  if (state.eqNodes) {
    const map = {
      FLAT: [0, 0, 0],
      BASS: [5, 0, 0],
      VOCAL: [-2, 4.5, 1.5],
      MOVIE: [3.5, 1.5, 3.5],
    };
    const [l, m, h] = map[state.eqPreset] || [0, 0, 0];
    state.eqNodes.low.gain.value = l;
    state.eqNodes.mid.gain.value = m;
    state.eqNodes.high.gain.value = h;
    rewireWebAudioChain();
  }
  document.querySelectorAll('[data-eq]').forEach((b) => {
    b.classList.toggle('active', b.dataset.eq === state.eqPreset);
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

function getCurrentMovieContext() {
  if (!state.currentMovie) return null;
  return {
    title: state.currentMovie.name || state.currentMovie.origin_name || '',
    genres: Array.isArray(state.currentMovie.genres) ? state.currentMovie.genres : [],
  };
}

function getSkipIntroSec(movie = state.currentMovie) {
  if (movie?.slug) {
    const perMovie = Number(state.movieIntroMap?.[normalizeMovieSlug(movie.slug)]);
    if (Number.isFinite(perMovie) && perMovie >= 10) return Math.min(300, Math.round(perMovie));
  }
  const n = Number(state.prefs.skipIntroSec);
  if (!Number.isFinite(n) || n < 10) return 85;
  return Math.min(300, Math.round(n));
}

function syncSkipIntroSettingsUi() {
  const sec = getSkipIntroSec();
  if ($('skipIntroSettingLabel')) $('skipIntroSettingLabel').textContent = `${sec}s`;
  if ($('customSkipIntroInput')) $('customSkipIntroInput').value = String(sec);
  if ($('skipIntroLabel')) $('skipIntroLabel').textContent = tr('skipIntroLabel', { s: sec });
  document.querySelectorAll('[data-skipintro]').forEach((b) => {
    b.classList.toggle('active', Number(b.dataset.skipintro) === sec);
  });
}

function applySkipIntroPref(seconds) {
  const clamped = Math.max(10, Math.min(300, Math.round(Number(seconds) || 85)));
  state.prefs.skipIntroSec = clamped;
  savePrefs();
  syncSkipIntroSettingsUi();
  showToast(`⏭ ${tr('skipIntroLabel', { s: clamped })}`);
}

function updateSkipIntroUi(curSec, durSec) {
  const btn = $('skipIntroBtn');
  if (!btn) return;
  const skipSec = getSkipIntroSec();
  const maxWindow = Math.max(180, skipSec + 95);
  const canShow =
    !state.skipIntroDismissed &&
    !state.isLocked &&
    !state.isMinimized &&
    curSec >= 3 &&
    curSec <= maxWindow &&
    (durSec === 0 || durSec > skipSec + 50);
  if (canShow) {
    if ($('skipIntroLabel')) $('skipIntroLabel').textContent = tr('skipIntroLabel', { s: skipSec });
    btn.style.display = 'inline-flex';
  } else {
    btn.style.display = 'none';
  }
}

function getNextEpisode() {
  if (!state.currentEpisode || !Array.isArray(state.episodesFlat) || state.episodesFlat.length < 2) return null;
  const curIdx = state.episodesFlat.findIndex((e) => e.link_m3u8 === state.currentEpisode.link_m3u8);
  if (curIdx >= 0 && curIdx + 1 < state.episodesFlat.length) {
    return state.episodesFlat[curIdx + 1];
  }
  return null;
}

function cancelAutoNext(markDismissed = true) {
  clearInterval(state.autoNextTimer);
  state.autoNextTimer = null;
  state.autoNextCountdownSec = 0;
  if (markDismissed) state.autoNextCancelled = true;
  if ($('autoNextCard')) $('autoNextCard').style.display = 'none';
}

function playNextEpisodeNow() {
  const nextEp = getNextEpisode();
  cancelAutoNext(false);
  if (nextEp && state.currentMovie) {
    persistCurrentPlaybackProgress({ immediate: true });
    openPlayer(state.currentMovie, nextEp, 0);
  }
}

function startAutoNextCountdown(nextEp, startSeconds = 5) {
  if (!nextEp || state.autoNextTimer || state.autoNextCancelled) return;
  const card = $('autoNextCard');
  if (!card) return;
  state.autoNextCountdownSec = startSeconds;
  if ($('autoNextTitle')) $('autoNextTitle').textContent = `${state.currentMovie?.name || ''} • ${nextEp.name}`;
  if ($('autoNextCountdown')) $('autoNextCountdown').textContent = `NEXT EPISODE IN ${state.autoNextCountdownSec}s`;
  if ($('autoNextBarFill')) $('autoNextBarFill').style.width = '100%';
  card.style.display = 'flex';

  state.autoNextTimer = setInterval(() => {
    state.autoNextCountdownSec -= 1;
    if (state.autoNextCountdownSec <= 0) {
      playNextEpisodeNow();
      return;
    }
    if ($('autoNextCountdown')) $('autoNextCountdown').textContent = `NEXT EPISODE IN ${state.autoNextCountdownSec}s`;
    if ($('autoNextBarFill')) {
      $('autoNextBarFill').style.width = `${Math.max(0, Math.round((state.autoNextCountdownSec / startSeconds) * 100))}%`;
    }
  }, 1000);
}

function checkAutoNext(curSec, durSec) {
  if (state.autoNextCancelled || state.autoNextTimer || state.isMinimized) return;
  const nextEp = getNextEpisode();
  if (!nextEp) return;
  if (durSec > 45 && durSec - curSec <= 15 && durSec - curSec > 0.5) {
    startAutoNextCountdown(nextEp, 5);
  }
}

/* ==================== RIGHT-SIDE EPISODES & SERVERS DRAWER ==================== */
function renderPlayerDrawer() {
  const movie = state.currentMovie;
  if (!movie) return;
  const servers = Array.isArray(movie.servers) ? movie.servers : [];
  if ($('drawerMovieTitle')) $('drawerMovieTitle').textContent = movie.name || 'Episodes & Servers';
  const curServer = servers[state.currentServerIdx] || servers[0];
  if ($('drawerServerSummary')) {
    $('drawerServerSummary').textContent = `${servers.length} Server${servers.length === 1 ? '' : 's'} • ${(curServer?.episodes || []).length} Ep`;
  }

  const srvBar = $('drawerServersBar');
  if (srvBar) {
    srvBar.innerHTML = '';
    servers.forEach((srv, idx) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `drawer-srv-btn ${idx === state.currentServerIdx ? 'active' : ''}`;
      btn.textContent = srv.server_name || `Server ${idx + 1}`;
      btn.onclick = () => switchPlayerServer(idx);
      srvBar.appendChild(btn);
    });
  }

  const epGrid = $('drawerEpisodesGrid');
  if (epGrid) {
    epGrid.innerHTML = '';
    const rawEps = curServer?.episodes || state.episodesFlat || [];
    const toolsWrap = $('drawerEpTools');
    if (toolsWrap) {
      toolsWrap.style.display = rawEps.length > 16 ? 'flex' : 'none';
    }
    const ordered = state.drawerEpDesc ? rawEps.slice().reverse() : rawEps.slice();
    const q = (state.drawerEpQuery || '').trim().toLowerCase();
    const eps = q ? ordered.filter((ep) => String(ep.name || ep.slug || '').toLowerCase().includes(q)) : ordered;
    eps.forEach((ep) => {
      const isCur = state.currentEpisode && (
        ep.link_m3u8 === state.currentEpisode.link_m3u8 ||
        normalizeEpisodeKey(ep.name || ep.slug) === normalizeEpisodeKey(state.currentEpisode.name || state.currentEpisode.slug)
      );
      const savedSec = !isCur ? getSavedEpisodePosition(movie.slug, ep) : 0;
      const watched = !isCur && isEpisodeWatched(movie.slug, ep);
      const b = document.createElement('button');
      b.type = 'button';
      b.className = `drawer-ep-btn ${isCur ? 'active' : ''} ${watched ? 'watched' : ''}`;
      const checkHtml = watched ? '<span class="ep-watched-check">✓</span>' : '';
      b.innerHTML = savedSec > 10
        ? `<span>${escapeHtml(ep.name)}${checkHtml}</span><span style="font-size:9.5px;opacity:.78;">${formatTime(savedSec)}</span>`
        : `<span>${escapeHtml(ep.name)}${checkHtml}</span>`;
      b.onclick = () => {
        closePlayerDrawer();
        state.episodesFlat = rawEps;
        persistCurrentPlaybackProgress({ immediate: true });
        openPlayer(movie, ep, 0);
      };
      epGrid.appendChild(b);
    });
  }
}

function switchPlayerServer(newIdx, { keepPosition = true, isAutoFailover = false } = {}) {
  const movie = state.currentMovie;
  if (!movie || !Array.isArray(movie.servers) || !movie.servers[newIdx]) return false;
  const targetServer = movie.servers[newIdx];
  const eps = targetServer.episodes || [];
  if (!eps.length) return false;

  const curKey = normalizeEpisodeKey(state.currentEpisode?.name || state.currentEpisode?.slug || 'full');
  const curPos = keepPosition ? Math.floor($('videoEl')?.currentTime || 0) : 0;
  const matchedEp =
    eps.find((e) => normalizeEpisodeKey(e.name || e.slug) === curKey) ||
    eps[Math.min(Math.max(0, state.episodesFlat.findIndex((e) => e.link_m3u8 === state.currentEpisode?.link_m3u8)), eps.length - 1)] ||
    eps[0];

  state.currentServerIdx = newIdx;
  state.episodesFlat = eps;
  if (!isAutoFailover) {
    closePlayerDrawer();
    showToast(`⚡ Switched to ${targetServer.server_name || `Server ${newIdx + 1}`}`);
  }
  openPlayer(movie, matchedEp, curPos, { preserveFailover: isAutoFailover });
  return true;
}

function openPlayerDrawer() {
  renderPlayerDrawer();
  $('playerDrawer')?.classList.add('open');
  $('playerDrawerBackdrop')?.classList.add('open');
  state.hudPointerInside = true;
  clearTimeout(state.controlsTimer);
}

function closePlayerDrawer() {
  $('playerDrawer')?.classList.remove('open');
  $('playerDrawerBackdrop')?.classList.remove('open');
  state.hudPointerInside = false;
  if (!$('videoEl')?.paused && !state.isLocked) {
    setControlsVisible(true, 1600);
  }
}

/* ==================== AUTO-FAILOVER STREAM ENGINE ==================== */
function clearStallWatchdog() {
  clearTimeout(state.stallTimer);
  state.stallTimer = null;
}

function armStallWatchdog() {
  clearStallWatchdog();
  const video = $('videoEl');
  if (!video || video.paused || state.isMinimized || !$('playerModal')?.classList.contains('open')) return;
  state.stallTimer = setTimeout(() => {
    const v = $('videoEl');
    if (v && !v.paused && v.readyState < 3 && $('playerModal')?.classList.contains('open')) {
      triggerAutoFailover('Stream stalled > 6s');
    }
  }, 6500);
}

function triggerAutoFailover(reason = 'Stream error') {
  clearStallWatchdog();
  const movie = state.currentMovie;
  if (!movie) return;
  state.failoverTriedServers.add(state.currentServerIdx);
  const servers = Array.isArray(movie.servers) ? movie.servers : [];
  for (let i = 0; i < servers.length; i++) {
    if (!state.failoverTriedServers.has(i) && (servers[i]?.episodes?.length || 0) > 0) {
      showToast(`⚡ ${reason} — auto-switching to ${servers[i].server_name || `Server ${i + 1}`}...`);
      switchPlayerServer(i, { keepPosition: true, isAutoFailover: true });
      return;
    }
  }
  // All servers for this movie exhausted -> surface compact 1-click failover recovery bar
  if ($('streamFailoverMsg')) {
    $('streamFailoverMsg').textContent = `⚠️ ${reason} — all servers for this movie failed`;
  }
  if ($('streamFailoverBar')) {
    $('streamFailoverBar').style.display = 'flex';
  }
}

/* ==================== HARDWARE MEDIA SESSION API ==================== */
function updateMediaSession(movie, episode) {
  if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return;
  try {
    const artworkUrl = movie?.poster || './assets/icon-192.png?v=2';
    navigator.mediaSession.metadata = new MediaMetadata({
      title: `${movie?.name || 'ChillFilm'} • ${episode?.name || 'Full'}`,
      artist: movie?.origin_name || 'ChillFilm • NKI Cinema',
      album: movie?.sourceName || 'NKI AI Cinema',
      artwork: [
        { src: artworkUrl, sizes: '512x512', type: 'image/jpeg' },
      ],
    });
    const video = $('videoEl');
    navigator.mediaSession.setActionHandler('play', () => video?.play().catch(() => {}));
    navigator.mediaSession.setActionHandler('pause', () => video?.pause());
    navigator.mediaSession.setActionHandler('seekbackward', () => {
      if (video) video.currentTime = Math.max(0, video.currentTime - 10);
    });
    navigator.mediaSession.setActionHandler('seekforward', () => {
      if (video) video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
    });
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (video && typeof details?.seekTime === 'number') video.currentTime = details.seekTime;
    });
    navigator.mediaSession.setActionHandler('nexttrack', () => {
      if (getNextEpisode()) playNextEpisodeNow();
    });
    navigator.mediaSession.setActionHandler('previoustrack', () => {
      if (!state.currentEpisode || !state.episodesFlat.length) return;
      const idx = state.episodesFlat.findIndex((e) => e.link_m3u8 === state.currentEpisode.link_m3u8);
      if (idx > 0 && state.currentMovie) {
        persistCurrentPlaybackProgress({ immediate: true });
        openPlayer(state.currentMovie, state.episodesFlat[idx - 1], 0);
      }
    });
  } catch {
    // Graceful fallback if browser restricts MediaSession
  }
}

/* ==================== UNIFIED AI SUBTITLE ENGINE (PARALLEL SRT + 10-MIN HLS LOOKAHEAD AUDIO + LIVE FALLBACK) ==================== */
const HLS_LOOKAHEAD_TARGET_MS = 10 * 60 * 1000; // Minimum 10 minutes pre-translated ahead of playhead
const HLS_LOOKAHEAD_REFILL_MS = 7 * 60 * 1000;  // Refill back to 10 min whenever ahead-buffer drops < 7 min
const HLS_AUDIO_WINDOW_SEC = 38;                // ~38s ADTS AAC (~380-520KB) -> fast 3-5s Gemini latency, zero 504s
const HLS_AUDIO_CONCURRENCY = 2;                // 2 steady parallel workers -> prevents Gemini burst 429/504 holes!
const SRT_BATCH_SIZE = 55;                      // Within /api/ai/translate 80-line limit
const SRT_CONCURRENCY = 3;                      // 3 parallel SRT batches -> full 1,200-line film in ~10-15s!

function loadCachedSubSmart(movieSlug, episode, langCode) {
  const key = buildSubCacheKey(movieSlug, episode, langCode);
  const legacyKey = buildLegacySubCacheKey(movieSlug, episode, langCode);
  return loadCachedEpisodeSub(key, legacyKey);
}

function normalizeCloudSubBundle(cloudBundle, targetLang) {
  if (!cloudBundle || !Array.isArray(cloudBundle.primaryCues) || cloudBundle.primaryCues.length < 3) return null;
  const audioMode = Boolean(cloudBundle.audioMode || cloudBundle.isAiAudio);
  const translatedWindows = Array.isArray(cloudBundle.translatedWindows)
    ? cloudBundle.translatedWindows
    : Array.isArray(cloudBundle.completedAudioSlots)
      ? cloudBundle.completedAudioSlots
      : [];
  const translatedRanges = mergeTimeRanges(cloudBundle.translatedRanges);
  const maxCueEnd = cloudBundle.primaryCues[cloudBundle.primaryCues.length - 1]?.endMs || 0;
  const maxRangeEnd = translatedRanges.length ? translatedRanges[translatedRanges.length - 1][1] : 0;
  return {
    ...cloudBundle,
    audioMode,
    isAiAudio: audioMode,
    translatedWindows,
    translatedRanges,
    audioTranslatedUntilMs: Math.max(Number(cloudBundle.audioTranslatedUntilMs || 0), maxRangeEnd, audioMode ? maxCueEnd : 0),
    totalDurationMs: Number(cloudBundle.totalDurationMs || 0),
    trackLabel: cloudBundle.sourcePlatform || `☁️ NKI Cloud ${targetLang.badge}`,
    translatedCount: cloudBundle.primaryCues.length,
  };
}

function reconstructDoneWindowsFromCache(windows, cachedEntry) {
  const doneSet = new Set();
  const baseRanges = mergeTimeRanges(cachedEntry?.translatedRanges || []);
  const legacyIndices = Array.isArray(cachedEntry?.translatedWindows)
    ? cachedEntry.translatedWindows
    : Array.isArray(cachedEntry?.completedAudioSlots)
      ? cachedEntry.completedAudioSlots
      : [];
  const cues = Array.isArray(cachedEntry?.primaryCues) ? cachedEntry.primaryCues : [];

  for (const w of windows) {
    const winDur = Math.max(1000, w.endMs - w.startMs);
    // 1. Time-range coverage (100% immune to server switching & segment boundary shifts!)
    let overlapMs = 0;
    for (const [rs, re] of baseRanges) {
      if (re <= w.startMs) continue;
      if (rs >= w.endMs) break;
      overlapMs += Math.max(0, Math.min(w.endMs, re) - Math.max(w.startMs, rs));
    }
    if (overlapMs >= winDur * 0.55) {
      doneSet.add(w.index);
      continue;
    }
    // 2. Legacy index match (for entries saved before v21 time-ranges)
    if (legacyIndices.includes(w.index)) {
      doneSet.add(w.index);
      continue;
    }
    // 3. Actual cue timestamp coverage (never re-translate windows that already have translated dialogue!)
    if (cues.length) {
      const innerStart = w.startMs + 1500;
      const innerEnd = Math.max(innerStart + 500, w.endMs - 1500);
      const hasCueInside = cues.some((c) => {
        const mid = ((c.startMs || 0) + (c.endMs || c.startMs || 0)) / 2;
        return mid >= innerStart && mid <= innerEnd;
      });
      if (hasCueInside) {
        doneSet.add(w.index);
      }
    }
  }

  const rebuiltRanges = mergeTimeRanges([
    ...baseRanges,
    ...windows.filter((w) => doneSet.has(w.index)).map((w) => [w.startMs, w.endMs]),
  ]);
  return { doneSet, doneRanges: rebuiltRanges };
}

function computeTotalCoveredMs(windows, doneSet, doneRanges = [], cues = []) {
  let maxMs = 0;
  for (const w of windows) {
    if (doneSet.has(w.index)) maxMs = Math.max(maxMs, w.endMs);
  }
  if (doneRanges.length) maxMs = Math.max(maxMs, doneRanges[doneRanges.length - 1][1]);
  if (cues.length) maxMs = Math.max(maxMs, cues[cues.length - 1].endMs || 0);
  return maxMs;
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const res = String(reader.result || '');
      const idx = res.indexOf(',');
      resolve(idx >= 0 ? res.slice(idx + 1) : res);
    };
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

function hasAiAudioCredentials() {
  return isCloudSignedIn() || (state.prefs.geminiApiKey || '').trim().length >= 15;
}

function buildMovieContext(movie) {
  const m = movie || state.currentMovie;
  if (!m) return null;
  return {
    slug: m.slug || '',
    title: m.nameEn || m.origin_name || m.name || '',
    genres: Array.isArray(m.genres) ? m.genres.slice(0, 4) : [],
    glossary: m.glossary || null,
  };
}

function isSubJobFor(movie, episode, langCode) {
  const j = state.subJob;
  if (!j || !movie || !episode) return false;
  const mSlug = normalizeMovieSlug(movie.slug);
  const eKey = normalizeEpisodeKey(episode.name || episode.slug || 'full');
  const lCode = resolveLanguage(langCode || state.prefs.primaryLang).code;
  return j.movieSlug === mSlug && j.episodeKey === eKey && j.langCode === lCode;
}

function isCurrentPlayerMatch(movie, episode, langCode = '') {
  if (!$('playerModal')?.classList.contains('open')) return false;
  if (!state.currentMovie || !state.currentEpisode || !movie || !episode) return false;
  const sameMovie = normalizeMovieSlug(state.currentMovie.slug) === normalizeMovieSlug(movie.slug);
  const sameEp = normalizeEpisodeKey(state.currentEpisode.name || state.currentEpisode.slug) ===
    normalizeEpisodeKey(episode.name || episode.slug);
  if (!sameMovie || !sameEp) return false;
  if (langCode && resolveLanguage(state.prefs.primaryLang).code !== resolveLanguage(langCode).code) return false;
  return true;
}

function mergeCuesChronological(existingCues, newCues) {
  const combined = [...(existingCues || []), ...(newCues || [])]
    .filter((c) => c && c.text && Number.isFinite(c.startMs))
    .sort((a, b) => a.startMs - b.startMs);
  const deduped = [];
  for (const c of combined) {
    const prev = deduped[deduped.length - 1];
    if (prev && Math.abs(prev.startMs - c.startMs) < 900 && prev.text === c.text) continue;
    deduped.push({
      id: deduped.length + 1,
      startMs: Math.max(0, Math.round(c.startMs)),
      endMs: Math.max(Math.round(c.endMs || c.startMs + 2500), Math.round(c.startMs) + 450),
      text: String(c.text).trim(),
    });
  }
  return deduped;
}

function computeContiguousAheadMs(windows, doneSet, anchorMs) {
  if (!windows.length || !doneSet.size) return anchorMs;
  let startIdx = windows.findIndex((w) => w.endMs > anchorMs);
  if (startIdx < 0) return anchorMs;
  let frontierMs = anchorMs;
  for (let i = startIdx; i < windows.length; i++) {
    if (!doneSet.has(windows[i].index)) break;
    frontierMs = Math.max(frontierMs, windows[i].endMs);
  }
  return frontierMs;
}

function setHudSubProgress(percent, visible) {
  const wrap = $('hudSubProgressWrap');
  const fill = $('hudSubProgressFill');
  if (!wrap || !fill) return;
  wrap.style.display = visible ? 'block' : 'none';
  if (visible) {
    fill.style.width = `${Math.max(2, Math.min(100, Math.round(percent)))}%`;
  }
}

function syncAudioSubUi(statusMsg = '') {
  const j = state.subJob;
  const isRunning = Boolean(state.audioSubActive || (j.active && j.mode === 'hls-audio'));
  const modalBtn = $('aiAudioSubBtn');
  const modalFullBtn = $('aiAudioSubFullBtn');
  const modalStatus = $('aiAudioSubStatus');
  const modalProgWrap = $('modalSubProgressWrap');
  const modalProgFill = $('modalSubProgressFill');
  const promptBtn = $('hudAudioSubPrompt');
  const promptTxt = $('hudAudioSubPromptText');

  if (modalBtn) {
    modalBtn.textContent = isRunning ? '⏹ Dừng dịch Audio AI' : '🎙 Dịch trước 10 phút';
    modalBtn.style.background = isRunning ? '#dc2626' : '';
    modalBtn.style.color = isRunning ? '#ffffff' : '';
  }
  if (modalFullBtn) {
    modalFullBtn.style.display = isRunning ? 'none' : 'inline-flex';
  }
  if (modalStatus) {
    const txt = statusMsg || (isRunning ? j.message : '');
    if (txt) modalStatus.textContent = txt;
  }
  if (modalProgWrap && modalProgFill) {
    const showProg = isRunning && j.mode === 'hls-audio';
    modalProgWrap.style.display = showProg ? 'block' : 'none';
    modalProgFill.style.width = `${Math.max(4, Math.min(100, Math.round(j.percent || 0)))}%`;
    modalProgFill.classList.toggle('active', showProg);
  }
  if (promptBtn && promptTxt) {
    if (isRunning) {
      promptBtn.style.display = 'inline-flex';
      promptBtn.classList.add('recording');
      promptTxt.textContent = statusMsg || j.message || `🎙 AI đang dịch trước 10 phút (${state.primaryCues.length} câu)...`;
    } else {
      promptBtn.classList.remove('recording');
      promptTxt.textContent = hasAiAudioCredentials()
        ? 'Chưa có SRT • Bấm dịch trước 10 phút Audio AI'
        : 'Chưa có SRT • Đăng nhập Cloud để AI dịch 10 phút';
    }
  }
}

function syncAllSubProgressUi() {
  const j = state.subJob;

  // 1. Update Player Top HUD if player is open on this episode
  if (j.movie && j.episode && isCurrentPlayerMatch(j.movie, j.episode, j.langCode)) {
    if (j.active) {
      updateSubStatusBadge(j.message || `✨ AI đang dịch (${j.percent}%)...`);
      setHudSubProgress(j.percent, true);
    } else if (j.phase === 'ready' || j.phase === 'partial_ready') {
      updateSubStatusBadge(j.message || `✅ Sub ${resolveLanguage(j.langCode).badge} sẵn sàng`);
      setHudSubProgress(100, false);
    } else if (j.phase === 'error' && j.errorMsg) {
      updateSubStatusBadge(`⚠️ ${j.errorMsg}`);
      setHudSubProgress(0, false);
    }
    syncAudioSubUi(j.message || j.errorMsg || '');
  }

  // 2. Update Movie Detail Page #detailSubCard if visible and matches selected episode/lang
  const card = $('detailSubCard');
  if (!card || !$('detailView')?.classList.contains('active') || !state.currentMovie) return;
  const pickedEp = state.detailSelectedEp || state.currentMovie.servers?.[0]?.episodes?.[0];
  const pickedLang = $('detailSubLangSelect')?.value || state.prefs.primaryLang || 'vi';
  if (!pickedEp || !isSubJobFor(state.currentMovie, pickedEp, pickedLang)) return;

  applyDetailSubCardVisualState({
    phase: j.active ? 'translating' : j.phase,
    percent: j.percent,
    badgeText: j.active
      ? `⏳ Đang dịch ${j.percent}%`
      : j.phase === 'ready'
        ? '✅ Đã dịch xong 100%'
        : j.phase === 'partial_ready'
          ? `✅ Sẵn sàng (${formatTime(Math.floor((j.coveredMs || 0) / 1000))})`
          : j.phase === 'error'
            ? '⚠️ Cần lưu ý'
            : '⚡ Sẵn sàng dịch',
    statusText: j.errorMsg || j.message || 'Bấm "Tự dịch Sub AI ngay" để bắt đầu.',
    isRunning: j.active,
    canWatchNow: (j.cues?.length || 0) >= 3 || j.phase === 'ready' || j.phase === 'partial_ready',
    needsCloud: Boolean(j.errorMsg && j.errorMsg.toLowerCase().includes('cloud')),
    buttonLabel: j.active
      ? '⏹ Dừng dịch'
      : j.phase === 'partial_ready'
        ? '🎬 Dịch tiếp 100% tập'
        : j.phase === 'ready'
          ? '🔄 Dịch lại'
          : '✨ Tự dịch Sub AI ngay',
  });
}

function applyDetailSubCardVisualState({
  phase = 'idle',
  percent = 0,
  badgeText = '',
  statusText = '',
  isRunning = false,
  canWatchNow = false,
  needsCloud = false,
  buttonLabel = '✨ Tự dịch Sub AI ngay',
} = {}) {
  const card = $('detailSubCard');
  const badge = $('detailSubBadge');
  const fill = $('detailSubProgressFill');
  const txt = $('detailSubStatusText');
  const pctEl = $('detailSubPercent');
  const btn = $('detailAutoSubBtn');
  const watchBtn = $('detailSubWatchNowBtn');
  const cloudBtn = $('detailSubCloudBtn');
  if (!card) return;

  const clampedPct = Math.max(0, Math.min(100, Math.round(percent || 0)));
  card.classList.toggle('is-ready', phase === 'ready' || phase === 'partial_ready');
  card.classList.toggle('is-working', isRunning);

  if (badge) {
    badge.textContent = badgeText || '⚡ Sẵn sàng dịch';
    badge.className = 'detail-sub-badge ' + (
      isRunning ? 'working' : (phase === 'ready' || phase === 'partial_ready') ? 'ready' : phase === 'error' ? 'warn' : ''
    );
  }
  if (fill) {
    fill.style.width = `${clampedPct}%`;
    fill.classList.toggle('active', isRunning);
  }
  if (pctEl) pctEl.textContent = `${clampedPct}%`;
  if (txt && statusText) txt.textContent = statusText;
  if (btn) {
    btn.textContent = buttonLabel;
    btn.classList.toggle('stop-mode', isRunning);
    btn.dataset.retranslate = phase === 'ready' && !isRunning ? '1' : '0';
  }
  if (watchBtn) {
    watchBtn.style.display = canWatchNow ? 'inline-flex' : 'none';
  }
  if (cloudBtn) {
    cloudBtn.style.display = needsCloud ? 'inline-flex' : 'none';
  }
}

async function refreshDetailSubCardForSelection(detail, episode) {
  if (!detail || !episode || !$('detailSubCard')) return;
  const langCode = $('detailSubLangSelect')?.value || state.prefs.primaryLang || 'vi';
  const lang = resolveLanguage(langCode);

  // 1. If unified job is already active/finished for this exact episode + language -> sync live!
  if (isSubJobFor(detail, episode, lang.code) && (state.subJob.active || state.subJob.phase !== 'idle')) {
    syncAllSubProgressUi();
    return;
  }

  applyDetailSubCardVisualState({
    phase: 'checking',
    percent: 0,
    badgeText: '🔍 Đang kiểm tra...',
    statusText: `Đang kiểm tra Sub ${lang.name} đã lưu trên máy & NKI Cloud...`,
    isRunning: false,
    canWatchNow: false,
  });

  const cacheKey = buildSubCacheKey(detail.slug, episode, lang.code);
  let cached = await loadCachedSubSmart(detail.slug, episode, lang.code);
  let fromCloud = false;

  if ((!cached || !cached.isFullyTranslated) && isCloudSignedIn()) {
    const cloudBundle = await cloudFetchSubtitle(detail.slug, episode.name || episode.slug, lang.code);
    const normCloud = cloudBundle && !hasWrongScriptForLang(cloudBundle.primaryCues, lang.code)
      ? normalizeCloudSubBundle(cloudBundle, lang)
      : null;
    if (normCloud) {
      const localCount = cached?.primaryCues?.length || 0;
      if (!cached || normCloud.isFullyTranslated || normCloud.primaryCues.length > localCount) {
        cached = normCloud;
        fromCloud = true;
        saveCachedEpisodeSub(cacheKey, normCloud).catch(() => {});
      }
    }
  }

  // Abort UI update if user switched movie/ep/lang while checking
  if (!$('detailSubCard') || state.currentMovie?.slug !== detail.slug) return;
  const curPickedLang = $('detailSubLangSelect')?.value || state.prefs.primaryLang || 'vi';
  if (resolveLanguage(curPickedLang).code !== lang.code) return;

  if (cached && cached.primaryCues?.length >= 3) {
    const count = cached.primaryCues.length;
    if (cached.isFullyTranslated) {
      applyDetailSubCardVisualState({
        phase: 'ready',
        percent: 100,
        badgeText: fromCloud ? `☁️ Sẵn sàng 100% (Cloud)` : `✅ Sẵn sàng 100%`,
        statusText: `✅ Sub ${lang.name} đã dịch xong 100% (${count} câu • ${fromCloud ? 'NKI Cloud' : 'Đã lưu máy'}) — Bấm Xem ngay để thưởng thức!`,
        isRunning: false,
        canWatchNow: true,
        buttonLabel: '🔄 Dịch lại',
      });
      return;
    }
    const covSec = Math.floor((cached.audioTranslatedUntilMs || 0) / 1000);
    const totSec = Math.floor((cached.totalDurationMs || 0) / 1000);
    const partialPct = totSec > 0
      ? Math.min(99, Math.max(8, Math.round((covSec / totSec) * 100)))
      : Math.min(95, Math.max(15, Math.round(((cached.translatedCount || count) / Math.max(count, 1)) * 100)));
    applyDetailSubCardVisualState({
      phase: 'partial_ready',
      percent: partialPct,
      badgeText: covSec > 0 ? `✅ Đã dịch ${formatTime(covSec)}` : `⚡ Đã dịch ${partialPct}%`,
      statusText: covSec > 0
        ? `✅ Đã dịch sẵn ${formatTime(covSec)}${totSec > 0 ? ` / ${formatTime(totSec)}` : ''} (${count} câu) — Bấm Xem ngay (AI tự dịch gối tiếp 10 phút) hoặc bấm dịch tiếp 100%!`
        : `⚡ Đã dịch ${count} câu (${partialPct}%) — Bấm xem ngay hoặc tiếp tục dịch 100%.`,
      isRunning: false,
      canWatchNow: true,
      buttonLabel: '⚡ Tiếp tục dịch Sub AI',
    });
    return;
  }

  applyDetailSubCardVisualState({
    phase: 'idle',
    percent: 0,
    badgeText: `⚡ Chưa có Sub ${lang.badge}`,
    statusText: `Chưa có Sub ${lang.name} cho ${episode.name || 'tập này'}. Bấm "Tự dịch Sub AI ngay" — nếu có SRT sẽ dịch 100% trong ~10s; nếu chưa có SRT sẽ tự tách âm thanh HLS dịch trước 10 phút!`,
    isRunning: false,
    canWatchNow: false,
    buttonLabel: '✨ Tự dịch Sub AI ngay',
  });
}

function stopActiveSubJob(silent = false, keepCues = true) {
  const j = state.subJob;
  const wasActive = j.active;
  // Flush any in-progress translated audio/SRT cues to IndexedDB & Cloud BEFORE aborting so closing never loses work!
  if (j.movie && j.episode && Array.isArray(j.cues) && j.cues.length >= 3) {
    const snapDoneWins = j.hlsDoneWindowSet ? [...j.hlsDoneWindowSet] : [];
    const snapRanges = Array.isArray(j.hlsDoneRanges) ? j.hlsDoneRanges.slice() : [];
    const snapAllDone = Boolean(j.audioMode && j.hlsWindows?.length && j.hlsDoneWindowSet?.size >= j.hlsWindows.length);
    const snapCovered = j.audioMode
      ? computeTotalCoveredMs(j.hlsWindows || [], j.hlsDoneWindowSet || new Set(), snapRanges, j.cues)
      : 0;
    persistJobSubCache({
      movie: j.movie,
      episode: j.episode,
      langCode: j.langCode || state.prefs.primaryLang,
      sourceLangCode: 'auto',
      trackLabel: j.audioMode ? `🎙 AI Audio (${resolveLanguage(j.langCode || state.prefs.primaryLang).badge})` : (state.lastSubTrackLabel || 'AI Sub'),
      sourceCues: j.cues.slice(),
      primaryCues: j.cues.slice(),
      isFullyTranslated: snapAllDone,
      translatedCount: j.cues.length,
      audioMode: Boolean(j.audioMode),
      audioTranslatedUntilMs: snapCovered,
      totalDurationMs: Number(j.totalDurationMs || 0),
      translatedWindows: snapDoneWins,
      translatedRanges: snapRanges,
      pushCloud: true,
    }).catch(() => {});
  }
  j.id += 1;
  j.active = false;
  j.hlsFilling = false;
  if (j.abortCtrl) {
    try { j.abortCtrl.abort(); } catch { /* ignore */ }
    j.abortCtrl = null;
  }
  stopWebAiAudioSub(true);
  if (!keepCues) {
    j.cues = [];
    j.phase = 'idle';
    j.percent = 0;
  } else if (wasActive) {
    const hasSome = (j.cues?.length || 0) >= 3;
    j.phase = hasSome ? 'partial_ready' : 'idle';
    j.message = hasSome
      ? `⏹ Đã tạm dừng • Đã lưu ${j.cues.length} câu Sub`
      : '⏹ Đã dừng dịch Sub AI';
  }
  setHudSubProgress(0, false);
  syncAllSubProgressUi();
  if (!silent && wasActive) showToast('⏹ Đã dừng dịch Sub AI (đã lưu các câu đã dịch)');
}

async function persistJobSubCache({ movie, episode, langCode, sourceLangCode, trackLabel, sourceCues, primaryCues, secondaryCues = [], isFullyTranslated = false, translatedCount = 0, audioMode = false, audioTranslatedUntilMs = 0, totalDurationMs = 0, translatedWindows = [], translatedRanges = [], pushCloud = false, forceOverwrite = false }) {
  if (!movie || !episode || !Array.isArray(primaryCues) || primaryCues.length < 3) return;
  const lang = resolveLanguage(langCode || state.prefs.primaryLang);
  // Never pollute local cache or Shared NKI Cloud Pool with wrong-script text (e.g. Chinese Hanzi under 'vi')!
  if (hasWrongScriptForLang(primaryCues, lang.code)) return;
  const key = buildSubCacheKey(movie.slug, episode, lang.code);
  const cleanRanges = mergeTimeRanges(translatedRanges);
  const payload = {
    primaryLangCode: lang.code,
    secondaryLangCode: state.prefs.dualSub ? state.prefs.secondaryLang : '',
    sourceLangCode: sourceLangCode || 'auto',
    trackLabel: trackLabel || `Sub ${lang.badge}`,
    sourceCues: sourceCues?.length ? sourceCues : primaryCues,
    primaryCues,
    secondaryCues,
    isFullyTranslated: Boolean(isFullyTranslated),
    translatedCount: translatedCount || primaryCues.length,
    audioMode: Boolean(audioMode),
    audioTranslatedUntilMs: Number(audioTranslatedUntilMs || 0),
    totalDurationMs: Number(totalDurationMs || 0),
    translatedWindows: Array.isArray(translatedWindows) ? translatedWindows : [],
    translatedRanges: cleanRanges,
  };
  await saveCachedEpisodeSub(key, payload, { forceOverwrite: Boolean(forceOverwrite) });

  if (pushCloud && isCloudSignedIn()) {
    state.lastCloudSubPushBatch = Date.now();
    state.cloudSubSlugs.add(normalizeMovieSlug(movie.slug));
    cloudSaveSubtitle({
      movieSlug: movie.slug,
      episodeNameOrSlug: episode.name || episode.slug,
      forceOverwrite: Boolean(forceOverwrite || state.subJob?.forceRetranslate),
      ...payload,
    }).catch(() => {});
  }
}

async function runParallelSrtTranslation({ job, jobId, movie, episode, cues, sourceLangCode, targetLang, startSec = 0, trackLabel = '', resumeWorking = null }) {
  job.mode = 'srt';
  job.phase = 'translating';
  job.audioMode = false;
  job.totalCount = cues.length;
  job.doneCount = 0;
  job.engineLabel = isCloudSignedIn() || (state.prefs.geminiApiKey || '').length > 10 ? 'Gemini AI' : 'Free Neural';

  // Only skip translation if track metadata matches targetLang AND actual text is not wrong-script (catches mislabeled OpenSubtitles files!)
  if (sourceLangCode === targetLang.code && !hasWrongScriptForLang(cues, targetLang.code)) {
    job.cues = cues;
    job.doneCount = cues.length;
    job.percent = 100;
    job.active = false;
    job.phase = 'ready';
    job.message = `✅ Sub ${targetLang.badge} gốc sẵn sàng (${cues.length} câu)`;
    if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
      state.sourceCues = cues;
      state.primaryCues = cues;
      state.lastSubSourceLang = sourceLangCode;
      state.lastSubTrackLabel = trackLabel || `Sub ${targetLang.badge}`;
      renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
    }
    await persistJobSubCache({
      movie, episode, langCode: targetLang.code, sourceLangCode, trackLabel,
      sourceCues: cues, primaryCues: cues, isFullyTranslated: true, translatedCount: cues.length, pushCloud: true,
    });
    syncAllSubProgressUi();
    showToast(`✅ Đã nạp Sub ${targetLang.name} gốc (${cues.length} câu) — Bấm Xem ngay!`);
    return;
  }

  const working = resumeWorking && resumeWorking.length === cues.length ? resumeWorking.slice() : cues.slice();
  const batches = [];
  for (let i = 0; i < cues.length; i += SRT_BATCH_SIZE) {
    batches.push({ start: i, end: Math.min(cues.length, i + SRT_BATCH_SIZE) });
  }

  // Playhead-first priority ordering: translate current playback position + next ~10 minutes FIRST!
  const anchorMs = Math.max(0, Math.round((startSec || 0) * 1000));
  let anchorCueIdx = anchorMs > 1000 ? cues.findIndex((c) => c.endMs >= anchorMs) : 0;
  if (anchorCueIdx < 0) anchorCueIdx = 0;
  const anchorBatchIdx = Math.floor(anchorCueIdx / SRT_BATCH_SIZE);
  const orderedBatches = anchorBatchIdx > 0
    ? [...batches.slice(anchorBatchIdx), ...batches.slice(0, anchorBatchIdx)]
    : batches;

  let cursor = 0;
  let completedCues = 0;
  let completedBatches = 0;
  const startedAt = Date.now();
  const movieCtx = buildMovieContext(movie);

  job.percent = 6;
  job.message = `✨ Đang dịch SRT sang ${targetLang.name} (0/${cues.length} câu)...`;
  syncAllSubProgressUi();

  const workerFn = async () => {
    while (cursor < orderedBatches.length) {
      if (job.id !== jobId || !job.active) return;
      const b = orderedBatches[cursor++];
      const chunk = cues.slice(b.start, b.end);
      const translated = await translateCueBatch({
        cues: chunk,
        targetLang: targetLang.code,
        geminiApiKey: state.prefs.geminiApiKey,
        geminiModel: state.prefs.geminiModel,
        cloudTranslator: isCloudSignedIn() ? cloudTranslateCueBatch : null,
        movieContext: movieCtx,
        onEngine: (eng) => {
          job.engineLabel = eng === 'cloud-gemini' ? 'Gemini AI' : eng === 'local-gemini' ? 'Gemini Key' : 'Free Neural';
        },
      });
      if (job.id !== jobId || !job.active) return;

      for (let k = 0; k < translated.length; k++) {
        working[b.start + k] = translated[k];
      }
      completedCues += chunk.length;
      completedBatches += 1;
      job.cues = working.slice();
      job.doneCount = Math.min(cues.length, completedCues);
      job.percent = Math.min(99, Math.max(8, Math.round((job.doneCount / cues.length) * 100)));

      const elapsedSec = Math.max(1, (Date.now() - startedAt) / 1000);
      const rate = job.doneCount / elapsedSec;
      const etaSec = rate > 0 ? Math.max(1, Math.round((cues.length - job.doneCount) / rate)) : 0;
      job.message = `✨ ${job.engineLabel} (SRT ${targetLang.badge}) • ${job.percent}% (${job.doneCount}/${cues.length} câu)${etaSec > 0 ? ` • ~${etaSec}s` : ''}`;

      if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
        state.sourceCues = cues;
        state.primaryCues = job.cues;
        state.lastSubSourceLang = sourceLangCode;
        state.lastSubTrackLabel = trackLabel || `Sub ${targetLang.badge}`;
        renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
      }
      syncAllSubProgressUi();

      if (completedBatches % 3 === 0 || completedBatches === 1) {
        await persistJobSubCache({
          movie, episode, langCode: targetLang.code, sourceLangCode, trackLabel,
          sourceCues: cues, primaryCues: job.cues, isFullyTranslated: false, translatedCount: job.doneCount, pushCloud: false,
        });
      }
    }
  };

  await Promise.all(Array.from({ length: Math.min(SRT_CONCURRENCY, orderedBatches.length) }, () => workerFn()));
  if (job.id !== jobId) return;

  job.active = false;
  job.phase = 'ready';
  job.percent = 100;
  job.doneCount = cues.length;
  job.cues = working.slice();
  job.message = `✅ Đã dịch xong 100% Sub ${targetLang.name} (${cues.length} câu • ${job.engineLabel}) — Bấm Xem ngay!`;

  if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
    state.sourceCues = cues;
    state.primaryCues = job.cues;
    state.lastSubSourceLang = sourceLangCode;
    state.lastSubTrackLabel = trackLabel || `Sub ${targetLang.badge}`;
    renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
  }
  await persistJobSubCache({
    movie, episode, langCode: targetLang.code, sourceLangCode, trackLabel,
    sourceCues: cues, primaryCues: job.cues, isFullyTranslated: true, translatedCount: cues.length, pushCloud: true,
  });
  syncAllSubProgressUi();
  showToast(`✅ Đã dịch xong 100% Sub ${targetLang.name} (${cues.length} câu)! Bấm Xem ngay!`);
}

async function transcribeSingleHlsAudioWindow({ win, job, targetLang, movieCtx, signal }) {
  const { audioBase64, mimeType, byteLength } = await extractHlsWindowAdtsBase64(win, job.hlsKeyCache, signal);
  if (!audioBase64 || byteLength < 512) return [];

  const attemptCall = async () => {
    if (isCloudSignedIn()) {
      const resp = await cloudTranscribeAudioChunk({
        audioBase64,
        mimeType,
        chunkStartOffsetMs: win.startMs,
        chunkDurationMs: win.durationMs,
        targetLang: targetLang.code,
        model: state.prefs.geminiModel,
        movieContext: movieCtx,
      });
      return Array.isArray(resp?.cues) ? resp.cues : [];
    }
    return transcribeAudioChunkDirect({
      audioBase64,
      mimeType,
      chunkStartOffsetMs: win.startMs,
      chunkDurationMs: win.durationMs,
      targetLang: targetLang.code,
      apiKey: state.prefs.geminiApiKey,
      modelId: state.prefs.geminiModel,
      movieContext: movieCtx,
    });
  };

  const backoffs = [1200, 2600];
  let lastErr = null;
  for (let attempt = 0; attempt <= backoffs.length; attempt++) {
    try {
      return await attemptCall();
    } catch (err) {
      lastErr = err;
      if (err?.name === 'AbortError' || signal?.aborted) throw err;
      const status = Number(err?.status || 0);
      const msgLow = String(err?.message || '').toLowerCase();
      // Only stop immediately on auth failure, missing server key, or real daily user quota limit
      const isHardQuota = status === 429 && msgLow.includes('quota exceeded');
      if (status === 401 || status === 403 || status === 503 || isHardQuota) throw err;
      if (attempt < backoffs.length) {
        await new Promise((r) => setTimeout(r, backoffs[attempt]));
      }
    }
  }
  throw lastErr || new Error('Audio chunk transcription failed');
}

async function fillHlsAudioRange({ job, jobId, anchorMs, targetEndMs, isFullEpisode = false }) {
  if (job.id !== jobId || !job.hlsWindows.length) return;
  job.hlsFilling = true;
  job.active = true;
  job.phase = 'translating';
  job.hlsAnchorMs = anchorMs;
  job.targetMs = targetEndMs;
  if (!Array.isArray(job.hlsDoneRanges)) job.hlsDoneRanges = [];

  const targetLang = resolveLanguage(job.langCode);
  const movie = job.movie;
  const episode = job.episode;
  const movieCtx = buildMovieContext(movie);
  const signal = job.abortCtrl?.signal;

  // Select untranslated windows covering [anchorMs, targetEndMs], ordered starting from anchorMs!
  const pending = job.hlsWindows.filter(
    (w) => !job.hlsDoneWindowSet.has(w.index) && w.endMs > anchorMs && w.startMs < targetEndMs
  );
  // If full episode mode, also pick any earlier windows before anchorMs after the ahead range finishes
  if (isFullEpisode && anchorMs > 1000) {
    const earlier = job.hlsWindows.filter((w) => !job.hlsDoneWindowSet.has(w.index) && w.endMs <= anchorMs);
    pending.push(...earlier);
  }

  const totalRangeWindows = job.hlsWindows.filter((w) =>
    isFullEpisode ? true : (w.endMs > anchorMs && w.startMs < targetEndMs)
  ).length || 1;
  const alreadyInRange = totalRangeWindows - pending.length;
  let doneInThisPass = 0;
  let fatalError = null;
  let cursor = 0;
  const failedWins = [];
  const startedAt = Date.now();

  const updateProgressText = () => {
    const contiguous = computeContiguousAheadMs(job.hlsWindows, job.hlsDoneWindowSet, anchorMs);
    const totalCov = computeTotalCoveredMs(job.hlsWindows, job.hlsDoneWindowSet, job.hlsDoneRanges, job.cues);
    job.coveredMs = Math.max(contiguous, totalCov);
    const completedTotal = isFullEpisode ? job.hlsDoneWindowSet.size : (alreadyInRange + doneInThisPass);
    const pct = Math.min(99, Math.max(5, Math.round((completedTotal / totalRangeWindows) * 100)));
    job.percent = pct;
    const covStr = formatTime(Math.floor(job.coveredMs / 1000));
    const tgtStr = formatTime(Math.floor(Math.max(targetEndMs, job.coveredMs) / 1000));
    const elapsedSec = Math.max(1, (Date.now() - startedAt) / 1000);
    const leftWin = Math.max(0, pending.length - doneInThisPass);
    const etaSec = doneInThisPass > 0 ? Math.max(1, Math.round((elapsedSec / doneInThisPass) * leftWin)) : 0;
    job.message = `🎙 AI Audio ${targetLang.badge} • Đã dịch tới ${covStr} / ${tgtStr} (${job.cues.length} câu)${etaSec > 0 ? ` • ~${etaSec}s` : ''}`;
    syncAllSubProgressUi();
  };

  if (!pending.length) {
    const contiguous = computeContiguousAheadMs(job.hlsWindows, job.hlsDoneWindowSet, anchorMs);
    const totalCov = computeTotalCoveredMs(job.hlsWindows, job.hlsDoneWindowSet, job.hlsDoneRanges, job.cues);
    const covered = Math.max(contiguous, totalCov);
    job.coveredMs = covered;
    const allDone = job.hlsDoneWindowSet.size >= job.hlsWindows.length;
    job.active = false;
    job.hlsFilling = false;
    job.phase = allDone ? 'ready' : 'partial_ready';
    job.percent = allDone ? 100 : Math.min(100, Math.round(((covered - anchorMs) / Math.max(1, targetEndMs - anchorMs)) * 100));
    job.message = allDone
      ? `✅ Đã dịch xong 100% Sub Audio ${targetLang.name} (${job.cues.length} câu)`
      : `✅ Đã dịch sẵn tới ${formatTime(Math.floor(covered / 1000))} (${job.cues.length} câu) — AI tự dịch gối tiếp khi xem!`;
    syncAllSubProgressUi();
    return;
  }

  updateProgressText();

  const processWindow = async (win, isRepair = false) => {
    const newCues = await transcribeSingleHlsAudioWindow({ win, job, targetLang, movieCtx, signal });
    if (job.id !== jobId || !job.active) return;

    job.hlsDoneWindowSet.add(win.index);
    job.hlsDoneRanges = mergeTimeRanges([...(job.hlsDoneRanges || []), [win.startMs, win.endMs]]);
    if (!isRepair) doneInThisPass += 1;
    if (newCues.length) {
      job.cues = mergeCuesChronological(job.cues, newCues);
    }
    job.doneCount = job.cues.length;

    // Immediately surface translated cues to Player if playing!
    if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
      state.primaryCues = job.cues;
      state.sourceCues = job.cues;
      state.lastSubTrackLabel = `🎙 AI Audio (${targetLang.badge})`;
      renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
      if ($('hudAudioSubPrompt') && job.cues.length >= 3) {
        $('hudAudioSubPrompt').style.display = 'none';
      }
    }
    updateProgressText();

    // Persist incremental cache monotonically every window so closing/refreshing NEVER loses progress!
    const allDoneNow = job.hlsDoneWindowSet.size >= job.hlsWindows.length;
    const shouldPushCloud = allDoneNow || (doneInThisPass % 4 === 0 && job.cues.length >= 6);
    await persistJobSubCache({
      movie, episode, langCode: targetLang.code, sourceLangCode: 'auto',
      trackLabel: `🎙 AI Audio (${targetLang.badge})`,
      sourceCues: job.cues, primaryCues: job.cues,
      isFullyTranslated: allDoneNow, translatedCount: job.cues.length,
      audioMode: true, audioTranslatedUntilMs: job.coveredMs,
      totalDurationMs: job.totalDurationMs,
      translatedWindows: [...job.hlsDoneWindowSet],
      translatedRanges: job.hlsDoneRanges,
      pushCloud: shouldPushCloud,
    });
  };

  const workerFn = async () => {
    while (cursor < pending.length) {
      if (job.id !== jobId || !job.active || fatalError) return;
      const win = pending[cursor++];
      try {
        await processWindow(win, false);
      } catch (err) {
        if (err?.name === 'AbortError' || job.id !== jobId) return;
        const status = Number(err?.status || 0);
        const msgLow = String(err?.message || '').toLowerCase();
        const isHardQuota = status === 429 && msgLow.includes('quota exceeded');
        console.warn('[HLS Audio AI] window failed (queued for gap-repair):', win.index, err?.message || err);
        if (status === 401 || status === 403 || status === 503 || isHardQuota) {
          fatalError = err?.message || `AI HTTP ${status}`;
          return;
        }
        failedWins.push(win);
        doneInThisPass += 1;
        updateProgressText();
      }
    }
  };

  await Promise.all(Array.from({ length: Math.min(HLS_AUDIO_CONCURRENCY, pending.length) }, () => workerFn()));
  if (job.id !== jobId) return;

  // Dedicated sequential 1-by-1 gap-repair pass so transient hiccups NEVER leave missing 38s holes!
  if (!fatalError && failedWins.length > 0 && job.active) {
    for (const win of failedWins) {
      if (job.id !== jobId || !job.active || fatalError) break;
      if (job.hlsDoneWindowSet.has(win.index)) continue;
      await new Promise((r) => setTimeout(r, 1200));
      if (job.id !== jobId || !job.active) break;
      try {
        await processWindow(win, true);
      } catch (err) {
        if (err?.name === 'AbortError' || job.id !== jobId) return;
        console.warn('[HLS Audio AI] gap-repair skipped:', win.index, err?.message || err);
      }
    }
  }
  if (job.id !== jobId) return;

  job.hlsFilling = false;
  job.active = false;

  if (fatalError) {
    job.phase = job.cues.length >= 3 ? 'partial_ready' : 'error';
    job.errorMsg = `Lỗi AI: ${String(fatalError).slice(0, 95)}`;
    if (job.cues.length >= 3) {
      await persistJobSubCache({
        movie, episode, langCode: targetLang.code, sourceLangCode: 'auto',
        trackLabel: `🎙 AI Audio (${targetLang.badge})`,
        sourceCues: job.cues, primaryCues: job.cues,
        isFullyTranslated: false, translatedCount: job.cues.length,
        audioMode: true, audioTranslatedUntilMs: job.coveredMs,
        totalDurationMs: job.totalDurationMs,
        translatedWindows: [...job.hlsDoneWindowSet],
        translatedRanges: job.hlsDoneRanges,
        pushCloud: true,
      });
    }
    syncAllSubProgressUi();
    showToast(`⚠️ ${job.errorMsg}`);
    return;
  }

  const allDone = job.hlsDoneWindowSet.size >= job.hlsWindows.length;
  const contiguous = computeContiguousAheadMs(job.hlsWindows, job.hlsDoneWindowSet, anchorMs);
  const totalCov = computeTotalCoveredMs(job.hlsWindows, job.hlsDoneWindowSet, job.hlsDoneRanges, job.cues);
  const covered = Math.max(contiguous, totalCov);
  job.coveredMs = covered;
  job.phase = allDone ? 'ready' : 'partial_ready';
  job.percent = allDone ? 100 : (isFullEpisode ? Math.round((job.hlsDoneWindowSet.size / Math.max(1, job.hlsWindows.length)) * 100) : 100);
  const covStr = formatTime(Math.floor(covered / 1000));
  const totStr = formatTime(Math.floor((job.totalDurationMs || covered) / 1000));
  job.message = allDone
    ? `✅ Đã dịch xong 100% Sub Audio ${targetLang.name} (${job.cues.length} câu • Lưu máy & Cloud)!`
    : `✅ Đã dịch trước tới ${covStr} / ${totStr} (${job.cues.length} câu) — Bấm Xem ngay, AI tự dịch gối tiếp 10 phút phía trước!`;

  await persistJobSubCache({
    movie, episode, langCode: targetLang.code, sourceLangCode: 'auto',
    trackLabel: `🎙 AI Audio (${targetLang.badge})`,
    sourceCues: job.cues, primaryCues: job.cues,
    isFullyTranslated: allDone, translatedCount: job.cues.length,
    audioMode: true, audioTranslatedUntilMs: covered,
    totalDurationMs: job.totalDurationMs,
    translatedWindows: [...job.hlsDoneWindowSet],
    translatedRanges: job.hlsDoneRanges,
    pushCloud: job.cues.length >= 3,
  });
  syncAllSubProgressUi();

  if (!job.notifiedReady) {
    job.notifiedReady = true;
    showToast(allDone
      ? `✅ Đã dịch xong 100% Sub ${targetLang.name} (${job.cues.length} câu)!`
      : `✅ Đã dịch sẵn tới ${covStr} Sub ${targetLang.name} (${job.cues.length} câu)! Xem ngay siêu mượt!`);
  }
}

async function runHlsLookaheadAudioJob({ job, jobId, movie, episode, targetLang, startSec = 0, scope = 'lookahead10', cachedEntry = null, initiatedFrom = 'player' }) {
  job.mode = 'hls-audio';
  job.audioMode = true;
  job.phase = 'discovering';
  job.percent = 4;
  job.message = '🎙 Không có file SRT — Đang đọc luồng âm thanh HLS để dịch trước 10 phút...';
  syncAllSubProgressUi();

  const { segments, totalDurationMs } = await parseHlsMediaSegments(episode.link_m3u8, job.abortCtrl?.signal);
  if (job.id !== jobId) return;

  const windows = buildHlsAudioWindows(segments, HLS_AUDIO_WINDOW_SEC);
  job.hlsWindows = windows;
  job.totalDurationMs = totalDurationMs;
  job.hlsKeyCache = new Map();
  // Reconstruct already-translated windows from BOTH saved time-ranges, window indices, AND actual cue timestamps
  // so already-translated minutes (e.g. 00:00 -> 20:00) are NEVER re-translated on re-entry!
  const { doneSet, doneRanges } = reconstructDoneWindowsFromCache(windows, cachedEntry);
  job.hlsDoneWindowSet = doneSet;
  job.hlsDoneRanges = doneRanges;
  if (cachedEntry?.primaryCues?.length) {
    job.cues = mergeCuesChronological(job.cues, cachedEntry.primaryCues);
  }

  let anchorMs = Math.max(0, Math.round((startSec || 0) * 1000));
  const isFull = scope === 'full';
  // If user clicked "Tiếp tục dịch" on Detail Page and the initial 10-min block is already done,
  // advance anchor directly to the first untranslated window so it extends 10 MORE minutes forward!
  if (!isFull && initiatedFrom === 'detail' && doneSet.size > 0) {
    const firstMissing = windows.find((w) => !doneSet.has(w.index) && w.endMs > anchorMs);
    if (firstMissing) {
      anchorMs = firstMissing.startMs;
    }
  }
  const targetEndMs = isFull
    ? totalDurationMs
    : Math.min(totalDurationMs, anchorMs + HLS_LOOKAHEAD_TARGET_MS);

  await fillHlsAudioRange({ job, jobId, anchorMs, targetEndMs, isFullEpisode: isFull });
}

/**
 * Rolling 10-minute lookahead watchdog during live playback:
 * ensures the viewer ALWAYS has 7–10 minutes of audio subtitles pre-translated ahead,
 * and immediately jumps to [curSec -> curSec + 10 min] whenever the user seeks!
 */
async function maybeRefillHlsLookahead(curSec, { forceJump = false } = {}) {
  const j = state.subJob;
  if (!state.currentMovie || !state.currentEpisode) return;
  if (!j.audioMode || !j.hlsWindows.length) return;
  if (!isSubJobFor(state.currentMovie, state.currentEpisode, state.prefs.primaryLang)) return;
  if (j.hlsDoneWindowSet.size >= j.hlsWindows.length) return; // 100% episode done!
  if (!hasAiAudioCredentials()) return;

  const curMs = Math.max(0, Math.round((curSec || 0) * 1000));
  const curWin = j.hlsWindows.find((w) => curMs >= w.startMs && curMs < w.endMs);
  const curUntranslated = Boolean(curWin && !j.hlsDoneWindowSet.has(curWin.index));
  const aheadFrontierMs = computeContiguousAheadMs(j.hlsWindows, j.hlsDoneWindowSet, curMs);
  const bufferedAheadMs = Math.max(0, aheadFrontierMs - curMs);

  // Already actively filling near playhead and current window is either done or in-flight
  if (j.hlsFilling && !forceJump) return;
  if (j.hlsFilling && forceJump && !curUntranslated && bufferedAheadMs >= HLS_LOOKAHEAD_REFILL_MS) return;

  if (!curUntranslated && bufferedAheadMs >= HLS_LOOKAHEAD_REFILL_MS) return;

  // If user seeked far outside current filling range, abort old range and prioritize new playhead!
  if (j.hlsFilling && forceJump && curUntranslated) {
    if (j.abortCtrl) {
      try { j.abortCtrl.abort(); } catch { /* ignore */ }
    }
    j.id += 1;
    j.abortCtrl = new AbortController();
  } else if (j.hlsFilling) {
    return;
  } else {
    j.abortCtrl = new AbortController();
  }

  const jobId = j.id;
  const targetEndMs = Math.min(j.totalDurationMs, curMs + HLS_LOOKAHEAD_TARGET_MS);
  fillHlsAudioRange({ job: j, jobId, anchorMs: curMs, targetEndMs, isFullEpisode: false }).catch((err) => {
    console.warn('[HLS Lookahead Refill] error:', err?.message || err);
  });
}

/**
 * Unified Entry Point for BOTH Movie Detail Page ("✨ Tự dịch Sub AI ngay") AND Player Auto-Sub!
 */
async function startUnifiedAiSubJob({
  movie,
  episode,
  langCode = '',
  scope = 'lookahead10',
  startSec = 0,
  initiatedFrom = 'detail',
  forceRetranslate = false,
  localCues = null,
  localLabel = '',
  preferAudioOnly = false,
} = {}) {
  if (!movie || !episode) return;
  const targetLang = resolveLanguage(langCode || state.prefs.primaryLang || 'vi');

  // Stop any previous running job cleanly
  stopActiveSubJob(true, true);

  const j = state.subJob;
  const jobId = ++j.id;
  j.active = true;
  j.mode = '';
  j.phase = 'checking';
  j.movieSlug = normalizeMovieSlug(movie.slug);
  j.episodeKey = normalizeEpisodeKey(episode.name || episode.slug || 'full');
  j.episodeName = episode.name || 'Full';
  j.langCode = targetLang.code;
  j.scope = scope || 'lookahead10';
  j.percent = 3;
  j.doneCount = 0;
  j.totalCount = 0;
  j.coveredMs = 0;
  j.targetMs = 0;
  j.totalDurationMs = 0;
  j.engineLabel = '';
  j.message = `🔍 Đang kiểm tra kho Sub ${targetLang.name}...`;
  j.errorMsg = '';
  j.startedAt = Date.now();
  j.abortCtrl = new AbortController();
  j.movie = movie;
  j.episode = episode;
  j.cues = [];
  j.hlsWindows = [];
  j.hlsDoneWindowSet = new Set();
  j.hlsDoneRanges = [];
  j.hlsKeyCache = new Map();
  j.hlsAnchorMs = Math.max(0, Math.round((startSec || 0) * 1000));
  j.hlsFilling = false;
  j.audioMode = false;
  j.notifiedReady = false;
  j.forceRetranslate = Boolean(forceRetranslate);
  syncAllSubProgressUi();

  // #2 Auto-prepare AI Character & Terminology Glossary in background (non-blocking) for consistent series translation!
  if (movie.slug && (!movie.glossary || !(movie.glossary.terms || []).length) && isCloudSignedIn()) {
    fetchMovieGlossary({
      slug: movie.slug,
      title: getDisplayTitle(movie),
      originTitle: movie.origin_name || movie.name || '',
      synopsis: getDisplayDesc(movie),
      genres: movie.genres || [],
      lang: targetLang.code,
      generate: true,
    }).then((gl) => {
      if (gl && ((gl.terms || []).length > 0 || gl.tone)) {
        movie.glossary = gl;
      }
    }).catch(() => {});
  }

  try {
    // 0. Direct local .SRT file uploaded by user
    if (Array.isArray(localCues) && localCues.length >= 1) {
      await runParallelSrtTranslation({
        job: j, jobId, movie, episode, cues: localCues, sourceLangCode: 'auto',
        targetLang, startSec, trackLabel: localLabel || 'Local .SRT',
      });
      return;
    }

    // 1. Check local IndexedDB + Shared NKI Cloud Pool first (instant <150ms, 0 Gemini tokens!)
    const cacheKey = buildSubCacheKey(movie.slug, episode, targetLang.code);
    let cached = !forceRetranslate ? await loadCachedSubSmart(movie.slug, episode, targetLang.code) : null;
    let fromCloud = false;
    if (j.id !== jobId) return;

    if (!forceRetranslate && (!cached || !cached.isFullyTranslated) && isCloudSignedIn()) {
      j.message = `☁️ Đang kiểm tra kho Sub NKI Cloud (${targetLang.badge})...`;
      syncAllSubProgressUi();
      const cloudBundle = await cloudFetchSubtitle(movie.slug, episode.name || episode.slug, targetLang.code);
      if (j.id !== jobId) return;
      const normCloud = cloudBundle && !hasWrongScriptForLang(cloudBundle.primaryCues, targetLang.code)
        ? normalizeCloudSubBundle(cloudBundle, targetLang)
        : null;
      if (normCloud) {
        if (!cached) {
          cached = normCloud;
          fromCloud = true;
        } else {
          // Merge local + Cloud monotonically so whichever device translated further is preserved!
          cached = {
            ...cached,
            ...normCloud,
            audioMode: Boolean(cached.audioMode || normCloud.audioMode),
            isFullyTranslated: Boolean(cached.isFullyTranslated || normCloud.isFullyTranslated),
            primaryCues: mergeCuesChronological(cached.primaryCues, normCloud.primaryCues),
            sourceCues: mergeCuesChronological(cached.sourceCues || cached.primaryCues, normCloud.sourceCues || normCloud.primaryCues),
            translatedWindows: [...new Set([...(cached.translatedWindows || []), ...(normCloud.translatedWindows || [])])],
            translatedRanges: mergeTimeRanges([...(cached.translatedRanges || []), ...(normCloud.translatedRanges || [])]),
            audioTranslatedUntilMs: Math.max(Number(cached.audioTranslatedUntilMs || 0), Number(normCloud.audioTranslatedUntilMs || 0)),
            totalDurationMs: Math.max(Number(cached.totalDurationMs || 0), Number(normCloud.totalDurationMs || 0)),
          };
          fromCloud = normCloud.primaryCues.length > (cached.translatedCount || 0);
        }
        saveCachedEpisodeSub(cacheKey, cached).catch(() => {});
      }
    }

    // If 100% cached -> instant ready!
    if (cached && cached.primaryCues?.length >= 3 && cached.isFullyTranslated && !forceRetranslate) {
      j.cues = cached.primaryCues;
      j.active = false;
      j.phase = 'ready';
      j.percent = 100;
      j.doneCount = cached.primaryCues.length;
      j.totalCount = cached.primaryCues.length;
      j.message = fromCloud
        ? `☁️ Sub ${targetLang.name} sẵn sàng 100% từ NKI Cloud (${cached.primaryCues.length} câu)!`
        : `💾 Sub ${targetLang.name} sẵn sàng 100% (${cached.primaryCues.length} câu • Đã lưu)!`;
      if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
        state.sourceCues = cached.sourceCues?.length ? cached.sourceCues : cached.primaryCues;
        state.primaryCues = cached.primaryCues;
        state.lastSubSourceLang = cached.sourceLangCode || 'auto';
        state.lastSubTrackLabel = cached.trackLabel || `Sub ${targetLang.badge}`;
        renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
      }
      syncAllSubProgressUi();
      if (initiatedFrom === 'detail') showToast(j.message);
      return;
    }

    // If partial Audio AI cache exists and user wants to continue (or play with 10-min lookahead)
    if (cached && cached.audioMode && cached.primaryCues?.length >= 3 && episode.link_m3u8?.includes('.m3u8')) {
      j.cues = cached.primaryCues.slice();
      if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
        state.primaryCues = j.cues;
        state.sourceCues = j.cues;
        renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
      }
      if (hasAiAudioCredentials()) {
        await runHlsLookaheadAudioJob({
          job: j, jobId, movie, episode, targetLang, startSec, scope, cachedEntry: cached, initiatedFrom,
        });
        return;
      }
    }

    // 2. Auto-discover online .SRT tracks (built-in Archive subs + Server Cinemeta IMDb + OpenSubtitles v3!)
    if (!preferAudioOnly) {
      j.phase = 'discovering';
      j.percent = 5;
      j.message = '🔎 Đang tìm file .SRT online (Cinemeta + OpenSubtitles)...';
      syncAllSubProgressUi();

      let tracks = Array.isArray(episode.subtitles) && episode.subtitles.length ? episode.subtitles.slice() : [];
      if (!tracks.length) {
        const isSeries = movie.type !== 'single' || (movie.servers?.[0]?.episodes?.length || 1) > 1;
        const epNumMatch = String(episode.name || '').match(/\d+/);
        const epNum = epNumMatch ? Number(epNumMatch[0]) : 1;
        const disc = await fetchCatalogSubtitles({
          imdbId: movie.imdbId || '',
          title: movie.nameEn || movie.name || '',
          originTitle: movie.origin_name || '',
          year: movie.year || '',
          isSeries,
          season: 1,
          episode: epNum,
        });
        if (j.id !== jobId) return;
        if (disc.imdbId && !movie.imdbId) movie.imdbId = disc.imdbId;
        tracks = disc.tracks || [];
      }

      if (isCurrentPlayerMatch(movie, episode)) {
        state.subTracks = tracks;
      }

      if (tracks.length > 0) {
        // Rank best candidates: target language first -> English -> Vietnamese -> others
        const ranked = [
          ...tracks.filter((t) => t.langCode === targetLang.code),
          ...tracks.filter((t) => t.langCode === 'en' && t.langCode !== targetLang.code),
          ...tracks.filter((t) => t.langCode === 'vi' && t.langCode !== targetLang.code && t.langCode !== 'en'),
          ...tracks.filter((t) => t.langCode !== targetLang.code && t.langCode !== 'en' && t.langCode !== 'vi'),
        ];

        for (const cand of ranked.slice(0, 4)) {
          if (j.id !== jobId || !j.active) return;
          try {
            j.message = `📥 Đang tải ${cand.label || 'SRT'}...`;
            syncAllSubProgressUi();
            const raw = await fetchSubtitleTextSmart(cand, j.abortCtrl?.signal);
            if (j.id !== jobId || !j.active) return;
            const parsedCues = parseSubtitleText(raw);
            if (parsedCues.length >= 3) {
              if (isCurrentPlayerMatch(movie, episode)) {
                state.activeSubTrackId = cand.id;
                state.lastSubTrackLabel = cand.label || '';
              }
              await runParallelSrtTranslation({
                job: j, jobId, movie, episode, cues: parsedCues,
                sourceLangCode: cand.langCode || 'auto',
                targetLang, startSec, trackLabel: cand.label || 'OpenSubtitles',
              });
              return;
            }
          } catch (e) {
            console.warn('[Sub Track] retry next candidate:', cand.label, e?.message || e);
          }
        }
      }
    }

    // 3. No online .SRT found -> Launch 10-Minute HLS Lookahead Audio AI Engine!
    if (!hasAiAudioCredentials()) {
      j.active = false;
      j.phase = 'error';
      j.percent = 0;
      j.errorMsg = 'Phim chưa có file .SRT online — Đăng nhập NKI Cloud (hoặc thêm Gemini Key) để AI tự nghe & dịch trước 10 phút từ âm thanh phim, hoặc nạp file .SRT từ máy!';
      if (isCurrentPlayerMatch(movie, episode) && $('hudAudioSubPrompt')) {
        $('hudAudioSubPrompt').style.display = 'inline-flex';
      }
      syncAllSubProgressUi();
      return;
    }

    if (episode.link_m3u8 && episode.link_m3u8.includes('.m3u8')) {
      try {
        await runHlsLookaheadAudioJob({ job: j, jobId, movie, episode, targetLang, startSec, scope });
        return;
      } catch (hlsErr) {
        if (hlsErr?.name === 'AbortError' || j.id !== jobId) return;
        console.warn('[HLS Lookahead] fallback check:', hlsErr?.message || hlsErr);
        // If inside Player, gracefully fall back to fixed 8s live MediaRecorder!
        if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
          j.active = false;
          j.mode = 'live-audio';
          startLiveMediaRecorderFallback();
          return;
        }
        j.active = false;
        j.phase = 'error';
        j.errorMsg = `Không tách được luồng HLS (${String(hlsErr?.message || 'CORS').slice(0, 60)}) — Bấm Xem ngay rồi bật AI Nghe trực tiếp trong Player hoặc nạp .SRT!`;
        syncAllSubProgressUi();
        return;
      }
    }

    // Non-HLS direct video stream (e.g. MP4)
    j.active = false;
    if (isCurrentPlayerMatch(movie, episode, targetLang.code)) {
      j.mode = 'live-audio';
      startLiveMediaRecorderFallback();
    } else {
      j.phase = 'error';
      j.errorMsg = 'Link phim này là MP4 trực tiếp (không phải HLS .m3u8) và chưa có SRT — Bấm Xem ngay rồi bật AI Audio trong Player hoặc nạp .SRT!';
      syncAllSubProgressUi();
    }
  } catch (err) {
    if (err?.name === 'AbortError' || j.id !== jobId) return;
    j.active = false;
    j.phase = 'error';
    j.errorMsg = `Lỗi dịch Sub AI: ${String(err?.message || err).slice(0, 95)}`;
    syncAllSubProgressUi();
  }
}

/* ==================== FIXED 8s LIVE MEDIARECORDER FALLBACK (ONLY FOR NON-HLS / MP4 STREAMS) ==================== */
function mergeAudioSubCues(newCues) {
  if (!Array.isArray(newCues) || !newCues.length) return;
  const deduped = mergeCuesChronological(state.primaryCues, newCues);
  state.primaryCues = deduped;
  state.sourceCues = deduped;
  state.lastSubTrackLabel = `🎙 AI Audio (${resolveLanguage(state.prefs.primaryLang).badge})`;

  // Live rolling grace buffer: show latest transcribed line immediately even if video passed chunk end!
  const latestCue = newCues[newCues.length - 1];
  if (latestCue?.text) {
    state.liveAudioFallbackCue = { text: latestCue.text, expiresAt: Date.now() + 5500 };
  }
  renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
  if (deduped.length >= 3) {
    persistCurrentSubCache(false, deduped.length).catch(() => {});
  }
}

function stopWebAiAudioSub(silent = false) {
  state.audioSubActive = false;
  state.audioSubSessionId += 1;
  state.liveAudioFallbackCue = null;
  clearTimeout(state.audioSubLoopTimer);
  state.audioSubLoopTimer = null;
  if (state.audioSubRecorder && state.audioSubRecorder.state !== 'inactive') {
    try { state.audioSubRecorder.stop(); } catch { /* ignore */ }
  }
  state.audioSubRecorder = null;
  disconnectAudioCaptureTap();
  if ($('hudAudioSubPrompt')) {
    $('hudAudioSubPrompt').classList.remove('recording');
    if (state.primaryCues.length >= 3) {
      $('hudAudioSubPrompt').style.display = 'none';
    }
  }
  syncAudioSubUi(
    state.primaryCues.length
      ? `Đã lưu ${state.primaryCues.length} câu AI Sub vào máy & NKI Cloud Pool`
      : 'Tự tách âm thanh HLS & dịch gối đầu trước 10 phút bằng Gemini AI'
  );
  if (state.primaryCues.length >= 3) {
    persistCurrentSubCache(false, state.primaryCues.length).catch(() => {});
  }
  if (!silent) showToast('⏹ Đã dừng AI Audio Sub');
}

async function captureNextAudioSubChunk(sessionId) {
  if (!state.audioSubActive || sessionId !== state.audioSubSessionId) return;
  const video = $('videoEl');
  if (!video || !$('playerModal')?.classList.contains('open')) {
    stopWebAiAudioSub(true);
    return;
  }
  if (video.paused) {
    state.audioSubLoopTimer = setTimeout(() => captureNextAudioSubChunk(sessionId), 1200);
    return;
  }

  const captureDest = connectAudioCaptureTap(video);
  const stream = captureDest?.stream || (typeof video.captureStream === 'function' ? video.captureStream() : null);
  const audioTracks = stream?.getAudioTracks?.() || [];
  if (!stream || !audioTracks.length || typeof MediaRecorder === 'undefined') {
    stopWebAiAudioSub(true);
    showToast('⚠️ Trình duyệt hoặc CORS chặn ghi âm trực tiếp — hãy dùng Chrome/Edge hoặc nạp .SRT');
    return;
  }

  const audioOnlyStream = new MediaStream(audioTracks);
  const mimeCandidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];
  const pickedMime = mimeCandidates.find((m) => MediaRecorder.isTypeSupported?.(m)) || '';
  const chunkStartMs = Math.round((video.currentTime || 0) * 1000);
  const CHUNK_MS = 8000; // Short 8s rolling chunk for fast non-HLS live fallback!
  const parts = [];

  try {
    const rec = pickedMime ? new MediaRecorder(audioOnlyStream, { mimeType: pickedMime, audioBitsPerSecond: 48000 }) : new MediaRecorder(audioOnlyStream);
    state.audioSubRecorder = rec;
    rec.ondataavailable = (ev) => {
      if (ev.data && ev.data.size > 0) parts.push(ev.data);
    };
    rec.onstop = async () => {
      if (sessionId !== state.audioSubSessionId) return;
      const blob = new Blob(parts, { type: rec.mimeType || pickedMime || 'audio/webm' });
      if (state.audioSubActive && sessionId === state.audioSubSessionId) {
        captureNextAudioSubChunk(sessionId);
      }
      if (blob.size < 1000) return;

      const targetLang = resolveLanguage(state.prefs.primaryLang);
      syncAudioSubUi(`🎙 AI đang nghe trực tiếp → ${targetLang.badge} (${state.primaryCues.length} câu)...`);
      updateSubStatusBadge(`🎙 AI Nghe Live → ${targetLang.badge} (${state.primaryCues.length})...`);

      try {
        const audioBase64 = await blobToBase64(blob);
        const cleanMime = (blob.type || 'audio/webm').split(';')[0];
        let cues = [];
        if (isCloudSignedIn()) {
          const resp = await cloudTranscribeAudioChunk({
            audioBase64,
            mimeType: cleanMime,
            chunkStartOffsetMs: chunkStartMs,
            chunkDurationMs: CHUNK_MS,
            targetLang: targetLang.code,
            model: state.prefs.geminiModel,
            movieContext: buildMovieContext(state.currentMovie),
          });
          cues = Array.isArray(resp?.cues) ? resp.cues : [];
        } else {
          cues = await transcribeAudioChunkDirect({
            audioBase64,
            mimeType: cleanMime,
            chunkStartOffsetMs: chunkStartMs,
            chunkDurationMs: CHUNK_MS,
            targetLang: targetLang.code,
            apiKey: state.prefs.geminiApiKey,
            modelId: state.prefs.geminiModel,
            movieContext: buildMovieContext(state.currentMovie),
          });
        }
        if (sessionId !== state.audioSubSessionId) return;
        state.audioSubChunksDone += 1;
        mergeAudioSubCues(cues);
        updateSubStatusBadge(`🎙 AI Audio ${targetLang.badge} • ${state.primaryCues.length} câu`);
        syncAudioSubUi(`🎙 Đang nghe Live (8s) • ${state.primaryCues.length} câu`);
      } catch (err) {
        console.warn('[AI Audio Sub] chunk failed:', err?.message || err);
        syncAudioSubUi(`⚠️ Lỗi AI Audio: ${String(err?.message || 'đang thử lại').slice(0, 55)}`);
      }
    };

    rec.start();
    state.audioSubLoopTimer = setTimeout(() => {
      if (rec.state === 'recording') {
        try { rec.stop(); } catch { /* ignore */ }
      }
    }, CHUNK_MS);
  } catch (err) {
    stopWebAiAudioSub(true);
    showToast(`⚠️ Không bật được Live Audio Sub: ${err?.message || 'unsupported'}`);
  }
}

function startLiveMediaRecorderFallback() {
  const video = $('videoEl');
  connectAudioCaptureTap(video);
  if (video.paused) video.play().catch(() => {});
  state.audioSubActive = true;
  state.audioSubChunksDone = 0;
  const sessionId = ++state.audioSubSessionId;
  const targetLang = resolveLanguage(state.prefs.primaryLang);
  updateSubStatusBadge(`🎙 AI Nghe Live (${targetLang.badge})...`);
  syncAudioSubUi(`🎙 Đang nghe trực tiếp từng đoạn 8s → ${targetLang.badge}...`);
  showToast(`🎙 Đã bật AI Nghe Live (${targetLang.badge}) — câu đầu sẽ hiện sau ~10s!`);
  captureNextAudioSubChunk(sessionId);
}

function startWebAiAudioSub(scope = 'lookahead10') {
  if (state.audioSubActive || (state.subJob.active && state.subJob.mode === 'hls-audio')) {
    stopActiveSubJob(false, true);
    return;
  }
  if (!hasAiAudioCredentials()) {
    showToast('☁️ Đăng nhập NKI Cloud (hoặc thêm Gemini API Key trong Cài đặt) để bật AI Dịch Audio 10 phút!');
    openSettingsModal();
    return;
  }
  if (!state.currentMovie || !state.currentEpisode) return;

  const curSec = Math.floor($('videoEl')?.currentTime || 0);
  if (state.currentEpisode.link_m3u8?.includes('.m3u8')) {
    const label = scope === 'full' ? 'toàn bộ tập (100%)' : 'trước 10 phút';
    showToast(`🎙 Đang tách âm thanh HLS & dịch ${label} bằng Gemini AI...`);
    startUnifiedAiSubJob({
      movie: state.currentMovie,
      episode: state.currentEpisode,
      langCode: state.prefs.primaryLang,
      scope,
      startSec: curSec,
      initiatedFrom: 'player',
      preferAudioOnly: true,
    });
    return;
  }
  startLiveMediaRecorderFallback();
}

/* ==================== WATCH PARTY 2.0 ENGINE (LOBBY • RTT SOFT-SYNC • CO-CONTROL • QUEUE • REACTIONS • SPLIT THEATER) ==================== */
const PARTY_SESSION_KEY = 'chillfilm_nki_party_session_v2';

function getDefaultPartyNick() {
  const session = getCloudSession();
  if (session?.user?.displayName) return session.user.displayName;
  if (session?.user?.username) return session.user.username;
  return localStorage.getItem('chillfilm_nki_party_nick_v1') || 'Khách';
}

function canControlParty() {
  const p = state.party;
  return Boolean(p.roomCode && (p.isHost || p.controlMode === 'EVERYONE'));
}

function savePartySessionStorage() {
  const p = state.party;
  if (!p.roomCode) {
    try { sessionStorage.removeItem(PARTY_SESSION_KEY); } catch { /* ignore */ }
    return;
  }
  try {
    sessionStorage.setItem(PARTY_SESSION_KEY, JSON.stringify({
      roomCode: p.roomCode,
      hostToken: p.hostToken || '',
      memberId: p.memberId || '',
      nickname: p.nickname || '',
      ts: Date.now(),
    }));
  } catch { /* ignore */ }
}

function loadPartySessionStorage() {
  try {
    const raw = sessionStorage.getItem(PARTY_SESSION_KEY);
    if (!raw) return null;
    const obj = JSON.parse(raw);
    if (!obj?.roomCode || Date.now() - Number(obj.ts || 0) > 6 * 60 * 60 * 1000) return null;
    return obj;
  } catch {
    return null;
  }
}

function applyPartySplitLayout() {
  const stage = $('playerStage');
  const p = state.party;
  const activeSplit = Boolean(p.roomCode && p.chatVisible && p.splitMode && !state.isMinimized);
  stage?.classList.toggle('party-split-mode', activeSplit);
  if ($('partySplitToggleBtn')) {
    $('partySplitToggleBtn').title = p.splitMode ? 'Đổi sang khung nổi (Overlay)' : 'Đổi sang chế độ Rạp chia đôi (Split)';
  }
}

function setPartySideTab(tabName) {
  const clean = ['chat', 'queue', 'room'].includes(tabName) ? tabName : 'chat';
  state.party.activeSideTab = clean;
  document.querySelectorAll('#partySideTabs [data-ptab]').forEach((b) => {
    b.classList.toggle('active', b.dataset.ptab === clean);
  });
  $('partyPaneChat')?.classList.toggle('active', clean === 'chat');
  $('partyPaneQueue')?.classList.toggle('active', clean === 'queue');
  $('partyPaneRoom')?.classList.toggle('active', clean === 'room');
  if (clean === 'queue') renderPartyQueue();
  if (clean === 'room') renderPartyRoomTab();
}

function renderPartyHeaderAndButton() {
  const p = state.party;
  const hudBtn = $('hudPartyBtn');
  const panel = $('partyChatPanel');
  if (hudBtn) {
    hudBtn.classList.toggle('active', Boolean(p.roomCode));
    hudBtn.textContent = p.roomCode ? `🍿 ${p.roomCode}` : '🍿 Party';
  }
  if (panel) {
    panel.style.display = p.roomCode && p.chatVisible && !state.isMinimized ? 'flex' : 'none';
  }
  if ($('partyQueueTabBtn')) {
    const n = (p.queue || []).length;
    $('partyQueueTabBtn').textContent = n > 0 ? `🎬 Hàng chờ (${n})` : '🎬 Hàng chờ';
  }
  if ($('partyRoomTabBtn')) {
    const mCount = Math.max(1, (p.members || []).length);
    $('partyRoomTabBtn').textContent = `👥 Phòng (${mCount})`;
  }
  applyPartySplitLayout();
}

function setPartySyncIndicator(text, tone = 'ok') {
  const el = $('partySyncBadge');
  if (!el) return;
  el.textContent = text;
  el.classList.toggle('adjusting', tone === 'adjusting');
  el.classList.toggle('lag', tone === 'lag');
}

function showPartyActionPill(text) {
  const el = $('partyActionPill');
  if (!el || !text) return;
  el.textContent = text;
  el.style.display = 'block';
  clearTimeout(el._t);
  el._t = setTimeout(() => { el.style.display = 'none'; }, 3600);
}

function spawnFloatingEmoji(emoji, senderName = '') {
  const layer = $('partyReactionLayer');
  if (!layer || !emoji) return;
  const el = document.createElement('div');
  el.className = 'party-float-emoji';
  const dx = Math.round((Math.random() - 0.5) * 44);
  el.style.setProperty('--dx', `${dx}px`);
  el.innerHTML = `${escapeHtml(emoji)}${senderName ? `<span>${escapeHtml(senderName)}</span>` : ''}`;
  layer.appendChild(el);
  setTimeout(() => el.remove(), 2700);
}

function appendPartyMessages(messages) {
  const list = $('partyMessagesList');
  if (!list || !Array.isArray(messages) || !messages.length) return;
  for (const m of messages) {
    if (!m || !m.text) continue;
    if (m.id && m.id <= state.party.lastMsgId) continue;
    if (m.id) state.party.lastMsgId = m.id;
    const div = document.createElement('div');
    div.className = 'party-msg';
    div.innerHTML = `
      <div class="party-msg-author">
        <span>${escapeHtml(m.senderName || 'Khách')}</span>
        ${m.isHost ? '<span class="party-host-tag">HOST</span>' : ''}
      </div>
      <div>${escapeHtml(m.text)}</div>
    `;
    list.appendChild(div);
  }
  list.scrollTop = list.scrollHeight;
}

function renderPartyRoomTab() {
  const p = state.party;
  if (!p.roomCode) return;
  if ($('partyCodeDisplay')) $('partyCodeDisplay').textContent = p.roomCode;
  const shareUrl = `${window.location.origin}/?party=${p.roomCode}`;
  const qrEl = $('partyQrImg');
  if (qrEl) {
    const qrSrc = `https://api.qrserver.com/v1/create-qr-code/?size=160x160&margin=6&data=${encodeURIComponent(shareUrl)}`;
    if (qrEl.dataset.code !== p.roomCode) {
      qrEl.dataset.code = p.roomCode;
      qrEl.src = qrSrc;
    }
  }
  const hostBox = $('partyHostControlsBox');
  if (hostBox) {
    hostBox.style.display = p.isHost ? 'flex' : 'none';
  }
  if ($('partyToggleControlModeBtn')) {
    $('partyToggleControlModeBtn').textContent = p.controlMode === 'EVERYONE'
      ? '🤝 Ai cũng điều khiển'
      : '👑 Chỉ Chủ phòng';
    $('partyToggleControlModeBtn').classList.toggle('active', p.controlMode === 'EVERYONE');
  }
  if ($('partyToggleVisibilityBtn')) {
    $('partyToggleVisibilityBtn').textContent = p.visibility === 'PUBLIC'
      ? '🌐 Đang Công khai'
      : '🔒 Đang Riêng tư';
    $('partyToggleVisibilityBtn').classList.toggle('active', p.visibility === 'PUBLIC');
  }
  if ($('partyClaimHostBtn')) {
    $('partyClaimHostBtn').style.display = (!p.isHost && p.hostInactive) ? 'block' : 'none';
  }
  if ($('partyCountdownBtn')) {
    $('partyCountdownBtn').style.display = canControlParty() ? 'inline-flex' : 'none';
  }

  const members = Array.isArray(p.members) ? p.members : [];
  if ($('partyMembersHeading')) {
    $('partyMembersHeading').textContent = `👥 Thành viên đang xem (${Math.max(1, members.length)})`;
  }
  const listEl = $('partyMembersList');
  if (listEl) {
    listEl.innerHTML = members.length
      ? members.map((m) => {
          const stIcon = m.status === 'buffering' ? '⏳ Đang tải' : (m.status === 'paused' ? '⏸️ Dừng' : '🟢 Xem');
          const posStr = m.posSec > 0 ? ` • ${formatTime(m.posSec)}` : '';
          return `
            <div class="party-member-row">
              <div style="min-width:0;display:flex;align-items:center;gap:5px;">
                <span style="font-weight:800;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(m.name)}</span>
                ${m.isHost ? '<span class="party-host-tag">HOST</span>' : ''}
                ${m.id === p.memberId ? '<span style="font-size:10px;color:#86efac;">(Bạn)</span>' : ''}
              </div>
              <span style="font-size:10.5px;color:#cbd5e1;white-space:nowrap;">${stIcon}${posStr}</span>
            </div>
          `;
        }).join('')
      : '<div class="party-empty-hint">Đang cập nhật thành viên...</div>';
  }
}

function renderPartyQueue() {
  const p = state.party;
  const listEl = $('partyQueueList');
  if (!listEl) return;
  const queue = Array.isArray(p.queue) ? p.queue : [];
  if (!queue.length) {
    listEl.innerHTML = '<div class="party-empty-hint">Chưa có phim nào trong hàng chờ.<br>Tìm phim bên trên để xếp lịch xem nối tiếp hoặc đổi phim ngay trong phòng!</div>';
    return;
  }
  const allowPlay = canControlParty();
  listEl.innerHTML = '';
  queue.forEach((item) => {
    const votes = Array.isArray(item.votes) ? item.votes : [];
    const voted = Boolean(p.memberId && votes.includes(p.memberId));
    const canDel = allowPlay || (p.memberId && item.addedById === p.memberId);
    const row = document.createElement('div');
    row.className = 'party-queue-item';
    row.innerHTML = `
      <img src="${escapeHtml(item.movie?.poster || './assets/icon-192.png?v=2')}" alt="" onerror="this.src='./assets/icon-192.png?v=2'" />
      <div style="min-width:0;flex:1;">
        <div style="font-size:12px;font-weight:800;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(item.movie?.name || 'Phim')}</div>
        <div style="font-size:10.5px;color:#9ca3af;margin-top:2px;">${escapeHtml(item.episode?.name || 'Full')} • bởi ${escapeHtml(item.addedBy || 'Khách')}</div>
        <div style="display:flex;gap:5px;margin-top:5px;align-items:center;flex-wrap:wrap;">
          <button class="party-vote-btn ${voted ? 'voted' : ''}" type="button" data-q-vote="${escapeHtml(item.id)}">👍 ${votes.length}</button>
          ${allowPlay ? `<button class="btn-grad" type="button" data-q-play="${escapeHtml(item.id)}" style="padding:3px 8px;font-size:10.5px;">▶ Phát ngay</button>` : ''}
          ${canDel ? `<button class="btn-glass" type="button" data-q-del="${escapeHtml(item.id)}" style="padding:3px 7px;font-size:10.5px;color:#fca5a5;">✕</button>` : ''}
        </div>
      </div>
    `;
    row.querySelector('[data-q-vote]')?.addEventListener('click', async () => {
      try {
        const res = await partyQueueVote({ roomCode: p.roomCode, itemId: item.id, memberId: p.memberId });
        if (Array.isArray(res?.queue)) {
          state.party.queue = res.queue;
          renderPartyQueue();
          renderPartyHeaderAndButton();
        }
      } catch { /* ignore */ }
    });
    row.querySelector('[data-q-play]')?.addEventListener('click', () => playPartyQueueItem(item.id));
    row.querySelector('[data-q-del]')?.addEventListener('click', async () => {
      try {
        const res = await partyQueueRemove({ roomCode: p.roomCode, itemId: item.id, memberId: p.memberId, hostToken: p.hostToken });
        if (Array.isArray(res?.queue)) {
          state.party.queue = res.queue;
          renderPartyQueue();
          renderPartyHeaderAndButton();
        }
      } catch { /* ignore */ }
    });
    listEl.appendChild(row);
  });
}

async function resolveFirstEpisodeForPartyMovie(movie) {
  if (Array.isArray(movie?.servers) && movie.servers[0]?.episodes?.[0]) {
    return { movie, episode: movie.servers[0].episodes[0] };
  }
  const detail = await fetchCatalogDetail({
    source: movie.source || 'phimapi',
    slug: movie.slug,
    sourceId: movie.sourceId || '',
  });
  const firstEp = detail?.servers?.[0]?.episodes?.[0];
  if (!detail || !firstEp) throw new Error('Không tìm thấy luồng phát cho phim này');
  setCachedMovieDetail(movie.source || 'phimapi', movie.slug, movie.sourceId || '', detail);
  return { movie: detail, episode: firstEp };
}

async function searchMoviesForPartyQueue() {
  const input = $('partyQueueSearchInput');
  const box = $('partyQueueSearchResults');
  const q = (input?.value || '').trim();
  if (!box || q.length < 2) {
    if (box) box.style.display = 'none';
    return;
  }
  box.style.display = 'flex';
  box.innerHTML = '<div style="font-size:11px;color:#9ca3af;padding:6px;">⏳ Đang tìm phim...</div>';
  try {
    const data = await fetchCatalogBrowse({ q, limit: 6, fast: 1 });
    const items = Array.isArray(data?.items) ? data.items : [];
    if (!items.length) {
      box.innerHTML = '<div style="font-size:11px;color:#9ca3af;padding:6px;">Không thấy phim phù hợp.</div>';
      return;
    }
    box.innerHTML = '';
    const allowSwitch = canControlParty();
    items.slice(0, 5).forEach((m) => {
      const r = document.createElement('div');
      r.className = 'party-qsearch-row';
      r.innerHTML = `
        <img src="${escapeHtml(m.poster || './assets/icon-192.png?v=2')}" alt="" onerror="this.src='./assets/icon-192.png?v=2'" />
        <div style="min-width:0;flex:1;">
          <div style="font-size:11.5px;font-weight:800;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(getDisplayTitle(m))}</div>
          <div style="font-size:10px;color:#9ca3af;">${escapeHtml(m.year || '')} • ${escapeHtml(m.episode_current || m.quality || 'HD')}</div>
        </div>
        <button class="btn-glass" type="button" data-act="add" style="padding:4px 7px;font-size:10.5px;">+ Chờ</button>
        ${allowSwitch ? '<button class="btn-grad" type="button" data-act="play" style="padding:4px 7px;font-size:10.5px;">▶ Đổi</button>' : ''}
      `;
      r.querySelector('[data-act="add"]')?.addEventListener('click', async (ev) => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        btn.textContent = '...';
        try {
          const { movie, episode } = await resolveFirstEpisodeForPartyMovie(m);
          const res = await partyQueueAdd({
            roomCode: state.party.roomCode,
            movie: {
              slug: movie.slug, source: movie.source || 'phimapi', sourceId: movie.sourceId || '',
              name: getDisplayTitle(movie), poster: movie.poster || '', year: movie.year || '',
            },
            episode: { name: episode.name || 'Full', slug: episode.slug || 'full', link_m3u8: episode.link_m3u8 },
            nickname: state.party.nickname,
            memberId: state.party.memberId,
          });
          if (Array.isArray(res?.queue)) state.party.queue = res.queue;
          box.style.display = 'none';
          if (input) input.value = '';
          renderPartyQueue();
          renderPartyHeaderAndButton();
          showToast(`🎬 Đã thêm "${getDisplayTitle(movie)}" vào hàng chờ!`);
        } catch (err) {
          showToast(err?.message || 'Không thêm được phim');
          btn.disabled = false;
          btn.textContent = '+ Chờ';
        }
      });
      r.querySelector('[data-act="play"]')?.addEventListener('click', async (ev) => {
        const btn = ev.currentTarget;
        btn.disabled = true;
        btn.textContent = '...';
        try {
          const { movie, episode } = await resolveFirstEpisodeForPartyMovie(m);
          box.style.display = 'none';
          if (input) input.value = '';
          state.party.lastMovieSlug = movie.slug;
          state.party.lastEpisodeUrl = episode.link_m3u8;
          openPlayer(movie, episode, 0, { preserveParty: true });
          pushHostPartyState({
            movie: {
              slug: movie.slug, source: movie.source || 'phimapi', sourceId: movie.sourceId || '',
              name: getDisplayTitle(movie), poster: movie.poster || '', year: movie.year || '',
            },
            episode: { name: episode.name || 'Full', slug: episode.slug || 'full', link_m3u8: episode.link_m3u8 },
            positionSec: 0,
            isPlaying: true,
            actionNote: `🎬 ${state.party.nickname} đổi phim → ${getDisplayTitle(movie)}`,
          });
          showToast(`🎬 Đã đổi cả phòng sang "${getDisplayTitle(movie)}"!`);
        } catch (err) {
          showToast(err?.message || 'Không đổi được phim');
          btn.disabled = false;
          btn.textContent = '▶ Đổi';
        }
      });
      box.appendChild(r);
    });
  } catch {
    box.innerHTML = '<div style="font-size:11px;color:#fca5a5;padding:6px;">Lỗi tìm kiếm.</div>';
  }
}

async function playPartyQueueItem(itemId = '') {
  const p = state.party;
  if (!p.roomCode || !canControlParty()) return;
  try {
    const res = await partyQueuePlay({
      roomCode: p.roomCode,
      itemId,
      nickname: p.nickname,
      hostToken: p.hostToken,
    });
    if (res?.movie && res?.episode) {
      if (Array.isArray(res.queue)) state.party.queue = res.queue;
      await transitionPartyToMovieEpisode(res.movie, res.episode, 0);
      renderPartyQueue();
      renderPartyHeaderAndButton();
    }
  } catch (err) {
    showToast(err?.message || 'Không thể phát phim trong hàng chờ');
  }
}

async function transitionPartyToMovieEpisode(partyMovieMeta, partyEp, startSec = 0) {
  state.party.lastMovieSlug = partyMovieMeta?.slug || '';
  state.party.lastEpisodeUrl = partyEp?.link_m3u8 || '';
  // Try hydrating full movie detail so Episodes Drawer has all episodes of the new movie!
  let fullMovie = null;
  if (partyMovieMeta?.slug) {
    fullMovie = getCachedMovieDetail(partyMovieMeta.source || 'phimapi', partyMovieMeta.slug, partyMovieMeta.sourceId || '');
    if (!fullMovie) {
      fullMovie = await fetchCatalogDetail({
        source: partyMovieMeta.source || 'phimapi',
        slug: partyMovieMeta.slug,
        sourceId: partyMovieMeta.sourceId || '',
      }).catch(() => null);
      if (fullMovie) setCachedMovieDetail(partyMovieMeta.source || 'phimapi', partyMovieMeta.slug, partyMovieMeta.sourceId || '', fullMovie);
    }
  }
  const movieToPlay = fullMovie || {
    ...partyMovieMeta,
    type: 'single',
    servers: [{ server_name: 'Watch Party Sync', episodes: [partyEp] }],
  };
  openPlayer(movieToPlay, partyEp, startSec, { preserveParty: true });
}

function runPartyCountdownOverlay(targetStartMs, positionSec) {
  const overlay = $('partyCountdownOverlay');
  const numEl = $('partyCountdownNum');
  const video = $('videoEl');
  if (!overlay || !numEl) return;
  clearInterval(state.party.countdownTimer);
  if (video) {
    state.party.applyingRemoteSync = true;
    video.pause();
    if (isFinite(video.duration) && video.duration > 3) {
      video.currentTime = Math.min(video.duration - 1, Math.max(0, positionSec));
    }
  }
  overlay.style.display = 'flex';
  const update = () => {
    const remainMs = targetStartMs - Date.now();
    if (remainMs <= 80) {
      clearInterval(state.party.countdownTimer);
      state.party.countdownTimer = null;
      numEl.textContent = '▶';
      setTimeout(() => { overlay.style.display = 'none'; }, 420);
      if (video) {
        video.playbackRate = state.speed || 1;
        video.play().catch(() => {});
        setTimeout(() => { state.party.applyingRemoteSync = false; }, 350);
      }
      return;
    }
    numEl.textContent = String(Math.max(1, Math.ceil(remainMs / 1000)));
  };
  update();
  state.party.countdownTimer = setInterval(update, 120);
}

function pushHostPartyState(extra = {}) {
  const p = state.party;
  if (!p.roomCode || !canControlParty() || p.applyingRemoteSync) return;
  const video = $('videoEl');
  const isPlaying = typeof extra.isPlaying === 'boolean' ? extra.isPlaying : !video?.paused;
  const positionSec = typeof extra.positionSec === 'number' ? extra.positionSec : Number(video?.currentTime || 0);
  partyControl({
    roomCode: p.roomCode,
    hostToken: p.hostToken,
    nickname: p.nickname,
    isPlaying,
    positionSec,
    playbackRate: Number(video?.playbackRate || state.speed || 1),
    movie: extra.movie || null,
    episode: extra.episode || null,
    countdown: Boolean(extra.countdown),
    actionNote: extra.actionNote || '',
  }).then((res) => {
    if (res?.stateUpdatedAt) {
      state.party.lastStateUpdatedAt = res.stateUpdatedAt;
      if (extra.countdown) {
        const localTarget = Date.now() + Math.max(0, res.stateUpdatedAt - (res.serverTime || Date.now()));
        runPartyCountdownOverlay(localTarget, positionSec);
      }
    }
  }).catch(() => {});
}

function applySyncedPlaybackState(snap, rttMs = 0, { forceHardSync = false } = {}) {
  const p = state.party;
  const video = $('videoEl');
  if (!video || !$('playerModal')?.classList.contains('open')) return;

  // Check if room switched to a different Movie or Episode!
  const remoteEpUrl = snap.episode?.link_m3u8 || '';
  if (remoteEpUrl && p.lastEpisodeUrl && remoteEpUrl !== p.lastEpisodeUrl) {
    transitionPartyToMovieEpisode(snap.movie, snap.episode, snap.positionSec || 0);
    return;
  }

  // Only sync playback when stateUpdatedAt changed OR guest is following
  const serverNowAdj = (snap.serverTime || Date.now()) + rttMs * 0.5;

  // Check for active 3-2-1 Synchronized Countdown!
  if (snap.stateUpdatedAt > serverNowAdj + 350) {
    const localTargetMs = Date.now() + (snap.stateUpdatedAt - serverNowAdj);
    if (!p.countdownTimer) {
      runPartyCountdownOverlay(localTargetMs, snap.positionSec || 0);
    }
    return;
  }

  // If this client is Host & last update was from Host itself, just display synced status
  const remoteUpdateNewer = snap.stateUpdatedAt > (p.lastStateUpdatedAt || 0);
  if (p.isHost && !remoteUpdateNewer && !forceHardSync) {
    setPartySyncIndicator('👑 Đang phát', 'ok');
    return;
  }
  if (remoteUpdateNewer) {
    p.lastStateUpdatedAt = snap.stateUpdatedAt;
  }

  const baseRate = Math.max(0.5, Math.min(2.0, Number(snap.playbackRate || 1)));
  const elapsedSec = snap.isPlaying
    ? Math.max(0, (serverNowAdj - snap.stateUpdatedAt) / 1000) * baseRate
    : 0;
  const expectedPos = Math.max(0, (snap.positionSec || 0) + elapsedSec);
  const drift = expectedPos - (video.currentTime || 0);
  const absDrift = Math.abs(drift);

  p.applyingRemoteSync = true;
  try {
    if (snap.isPlaying && video.paused) {
      video.play().catch(() => {});
    } else if (!snap.isPlaying && !video.paused) {
      video.pause();
    }

    if (isFinite(video.duration) && video.duration > 5) {
      if (forceHardSync || absDrift > 2.2 || (!snap.isPlaying && absDrift > 0.7)) {
        video.currentTime = Math.min(video.duration - 1, expectedPos);
        video.playbackRate = baseRate;
        p.softSyncActive = false;
        setPartySyncIndicator('⚡ Đã khớp', 'ok');
      } else if (snap.isPlaying && absDrift >= 0.45) {
        // 3-Tier Smooth Soft-Sync: nudge speed +/-5% instead of jarring seek stutter!
        const adjRate = drift > 0 ? Math.min(2.0, baseRate * 1.05) : Math.max(0.5, baseRate * 0.95);
        video.playbackRate = adjRate;
        p.softSyncActive = true;
        setPartySyncIndicator(`🟡 Bù ${drift > 0 ? '+' : '-'}${absDrift.toFixed(1)}s`, 'adjusting');
      } else {
        if (p.softSyncActive || Math.abs((video.playbackRate || 1) - baseRate) > 0.04) {
          video.playbackRate = baseRate;
          p.softSyncActive = false;
        }
        setPartySyncIndicator(`🟢 Khớp ${absDrift.toFixed(1)}s`, 'ok');
      }
    }
  } finally {
    setTimeout(() => { p.applyingRemoteSync = false; }, 220);
  }
}

function startPartyPolling() {
  clearTimeout(state.party.pollTimer);
  const scheduleNext = () => {
    if (!state.party.roomCode) return;
    const delayMs = document.visibilityState === 'hidden' ? 4000 : 1400;
    state.party.pollTimer = setTimeout(tick, delayMs);
  };

  const tick = async () => {
    const p = state.party;
    if (!p.roomCode) return;
    const video = $('videoEl');
    const curStatus = !video ? 'paused' : (video.readyState < 3 && !video.paused ? 'buffering' : (video.paused ? 'paused' : 'playing'));
    const t0 = performance.now();
    try {
      const data = await partyPoll({
        roomCode: p.roomCode,
        sinceMsgId: p.lastMsgId,
        sinceReactionTs: p.lastReactionTs,
        memberId: p.memberId,
        nickname: p.nickname,
        hostToken: p.hostToken,
        status: curStatus,
        pos: video?.currentTime || 0,
      });
      const rttMs = Math.min(2000, Math.max(0, performance.now() - t0));
      const snap = data?.party;
      if (!snap) {
        scheduleNext();
        return;
      }

      p.roomTitle = snap.roomTitle || '';
      p.visibility = snap.visibility || 'PRIVATE';
      p.controlMode = snap.controlMode || 'HOST_ONLY';
      p.hasPasscode = Boolean(snap.hasPasscode);
      p.hostName = snap.hostName || '';
      p.hostInactive = Boolean(snap.hostInactive);
      p.members = Array.isArray(snap.members) ? snap.members : [];
      p.queue = Array.isArray(snap.queue) ? snap.queue : [];

      if ($('partyRoomBadge')) {
        $('partyRoomBadge').textContent = `🍿 ${snap.roomCode} • ${snap.viewerCount}`;
      }
      if ($('partyRoleSub')) {
        const modeTxt = snap.controlMode === 'EVERYONE' ? '🤝 Ai cũng điều khiển' : `👑 ${snap.hostName}`;
        $('partyRoleSub').textContent = `${snap.roomTitle || snap.movie?.name || 'Xem chung'} • ${modeTxt}`;
      }
      renderPartyHeaderAndButton();
      if (p.activeSideTab === 'queue') renderPartyQueue();
      if (p.activeSideTab === 'room') renderPartyRoomTab();

      appendPartyMessages(snap.messages || []);

      // Render new floating emoji reactions
      if (Array.isArray(snap.reactions) && snap.reactions.length) {
        for (const r of snap.reactions) {
          if (r.createdAt > p.lastReactionTs) {
            p.lastReactionTs = r.createdAt;
            if (r.senderName !== p.nickname || Date.now() - r.createdAt > 1200) {
              spawnFloatingEmoji(r.emoji, r.senderName);
            }
          }
        }
      }

      // Show live activity pill when someone seeks/switches/counts down
      if (snap.lastActionText && snap.lastActionAt > (p.lastActionAt || 0)) {
        p.lastActionAt = snap.lastActionAt;
        showPartyActionPill(snap.lastActionText);
      }

      applySyncedPlaybackState(snap, rttMs);
    } catch (err) {
      if (err?.status === 404) {
        showToast('Phòng xem chung đã kết thúc hoặc hết hạn');
        leaveWatchParty(true);
        return;
      }
      setPartySyncIndicator('⚠️ Mạng chậm', 'lag');
    }
    scheduleNext();
  };
  scheduleNext();
}

async function sendPartyReaction(emoji) {
  const p = state.party;
  if (!p.roomCode || !emoji) return;
  spawnFloatingEmoji(emoji, p.nickname);
  try {
    const res = await partyReact({ roomCode: p.roomCode, emoji, nickname: p.nickname });
    if (res?.reaction?.createdAt) {
      p.lastReactionTs = Math.max(p.lastReactionTs, res.reaction.createdAt);
    }
  } catch { /* ignore */ }
}

async function sharePartyInvite() {
  const p = state.party;
  if (!p.roomCode) return;
  const shareUrl = `${window.location.origin}/?party=${p.roomCode}`;
  const title = `🍿 Xem chung "${p.roomTitle || state.currentMovie?.name || 'ChillFilm'}"`;
  const text = `Vào phòng Xem Chung mã ${p.roomCode} trên ChillFilm NKI cùng ${p.nickname}!`;
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url: shareUrl });
      return;
    } catch { /* fallback to clipboard */ }
  }
  navigator.clipboard?.writeText(shareUrl).catch(() => {});
  showToast(`📋 Đã copy link mời phòng ${p.roomCode}!`);
}

function hydratePartyFromCreateOrJoin(res, { isHost = false, hostToken = '', nickname = '' } = {}) {
  const snap = res.party;
  state.party = {
    ...state.party,
    roomCode: snap.roomCode,
    roomTitle: snap.roomTitle || '',
    visibility: snap.visibility || 'PRIVATE',
    controlMode: snap.controlMode || 'HOST_ONLY',
    hasPasscode: Boolean(snap.hasPasscode),
    hostToken: hostToken || '',
    hostName: snap.hostName || nickname,
    hostInactive: false,
    memberId: res.memberId,
    nickname,
    isHost,
    pollTimer: null,
    lastMsgId: 0,
    lastReactionTs: Date.now(),
    lastActionAt: snap.lastActionAt || Date.now(),
    lastStateUpdatedAt: snap.stateUpdatedAt || Date.now(),
    chatVisible: true,
    activeSideTab: 'chat',
    applyingRemoteSync: false,
    softSyncActive: false,
    lastMovieSlug: snap.movie?.slug || '',
    lastEpisodeUrl: snap.episode?.link_m3u8 || '',
    queue: Array.isArray(snap.queue) ? snap.queue : [],
    members: Array.isArray(snap.members) ? snap.members : [],
  };
  savePartySessionStorage();
  if ($('partyMessagesList')) $('partyMessagesList').innerHTML = '';
  appendPartyMessages(snap.messages || []);
  if ($('partyRoomBadge')) $('partyRoomBadge').textContent = `🍿 ${snap.roomCode} • ${snap.viewerCount || 1}`;
  if ($('partyRoleSub')) {
    const modeTxt = snap.controlMode === 'EVERYONE' ? '🤝 Ai cũng điều khiển' : `👑 ${snap.hostName}`;
    $('partyRoleSub').textContent = `${snap.roomTitle || snap.movie?.name || 'Xem chung'} • ${modeTxt}`;
  }
  setPartySideTab('chat');
  renderPartyHeaderAndButton();
  renderPartyRoomTab();
  renderPartyQueue();
  startPartyPolling();
}

async function createWatchPartyFromHub() {
  const movie = state.party.createPickedMovie || state.currentMovie;
  const episode = state.party.createPickedEpisode || state.currentEpisode;
  if (!movie || !episode?.link_m3u8) {
    showToast('Hãy tìm và chọn 1 bộ phim bên trên trước khi tạo phòng!');
    return;
  }
  const nickname = ($('partyNickInput')?.value || getDefaultPartyNick()).trim() || 'Host';
  try { localStorage.setItem('chillfilm_nki_party_nick_v1', nickname); } catch { /* ignore */ }
  const roomTitle = ($('partyCreateTitleInput')?.value || '').trim();
  const visibility = $('partyCreateVisSelect')?.value || 'PRIVATE';
  const controlMode = $('partyCreateControlSelect')?.value || 'EVERYONE';
  const passcode = ($('partyCreatePassInput')?.value || '').trim();
  const isAlreadyPlayingThis = $('playerModal')?.classList.contains('open') && state.currentEpisode?.link_m3u8 === episode.link_m3u8;
  const video = $('videoEl');
  const startPos = isAlreadyPlayingThis ? Number(video?.currentTime || 0) : 0;

  const btn = $('partyCreateSubmitBtn');
  if (btn) { btn.disabled = true; btn.textContent = '⏳ Đang mở phòng...'; }
  try {
    const res = await partyCreate({
      movie: {
        slug: movie.slug,
        source: movie.source || 'phimapi',
        sourceId: movie.sourceId || '',
        name: getDisplayTitle(movie),
        poster: movie.poster || '',
        year: movie.year || '',
      },
      episode: {
        name: episode.name || 'Full',
        slug: episode.slug || 'full',
        link_m3u8: episode.link_m3u8,
      },
      nickname,
      roomTitle,
      visibility,
      controlMode,
      passcode,
      positionSec: startPos,
      isPlaying: isAlreadyPlayingThis ? !video?.paused : true,
    });
    $('partyModal')?.classList.remove('open');
    if ($('partyCreatePassInput')) $('partyCreatePassInput').value = '';
    if (!isAlreadyPlayingThis) {
      openPlayer(movie, episode, 0, { preserveParty: true });
    }
    hydratePartyFromCreateOrJoin(res, { isHost: true, hostToken: res.hostToken, nickname });
    const shareUrl = `${window.location.origin}/?party=${res.roomCode}`;
    navigator.clipboard?.writeText(shareUrl).catch(() => {});
    showToast(`🍿 Đã mở phòng ${res.roomCode} & copy link mời!`);
  } catch (err) {
    showToast(`Không tạo được phòng: ${err?.message || 'error'}`);
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = '✨ Tạo Phòng & Mời Bạn Bè'; }
  }
}

async function createWatchPartyForCurrent() {
  if (!state.currentMovie || !state.currentEpisode) {
    openPartyHubModal('create');
    return;
  }
  openPartyHubModal('create', { movie: state.currentMovie, episode: state.currentEpisode });
}

async function joinWatchPartyByCode(rawCode, rawNick = '', { passcode = '', hostToken = '', memberId = '', silentRejoin = false } = {}) {
  const roomCode = String(rawCode || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (roomCode.length < 4) return showToast('Vui lòng nhập mã phòng 6 ký tự hợp lệ');
  const nickname = String(rawNick || getDefaultPartyNick()).trim() || 'Khách';
  try { localStorage.setItem('chillfilm_nki_party_nick_v1', nickname); } catch { /* ignore */ }

  try {
    if (!silentRejoin) showToast(`🍿 Đang vào phòng ${roomCode}...`);
    const res = await partyJoin({ roomCode, nickname, memberId, passcode, hostToken, silentRejoin });
    const snap = res.party;
    if (!snap?.movie?.slug || !snap?.episode?.link_m3u8) {
      throw new Error('Phòng chưa có luồng phim');
    }
    $('partyModal')?.classList.remove('open');
    if ($('partyJoinPassInput')) $('partyJoinPassInput').value = '';

    const elapsedSec = snap.isPlaying ? Math.max(0, (snap.serverTime - snap.stateUpdatedAt) / 1000) * (snap.playbackRate || 1) : 0;
    const startSec = Math.max(0, (snap.positionSec || 0) + elapsedSec);
    await transitionPartyToMovieEpisode(snap.movie, snap.episode, startSec);
    hydratePartyFromCreateOrJoin(res, {
      isHost: Boolean(res.isHost),
      hostToken: res.isHost ? hostToken : '',
      nickname,
    });
    if (!silentRejoin) showToast(`🍿 Đã vào phòng của ${snap.hostName}!`);
    return true;
  } catch (err) {
    if (err?.data?.needPasscode) {
      openPartyHubModal('join', { prefillCode: roomCode });
      $('partyJoinPassInput')?.focus();
      showToast('🔒 Phòng này yêu cầu mật khẩu — hãy nhập mật khẩu để vào!');
      return false;
    }
    if (!silentRejoin) showToast(err?.message || 'Không thể vào phòng Xem chung');
    else clearPartySessionOnError();
    return false;
  }
}

function clearPartySessionOnError() {
  try { sessionStorage.removeItem(PARTY_SESSION_KEY); } catch { /* ignore */ }
}

function leaveWatchParty(silent = false) {
  const p = state.party;
  clearTimeout(p.pollTimer);
  clearInterval(p.countdownTimer);
  if ($('partyCountdownOverlay')) $('partyCountdownOverlay').style.display = 'none';
  if (p.softSyncActive) {
    const v = $('videoEl');
    if (v) v.playbackRate = state.prefs.playbackRate || 1;
  }
  if (p.roomCode && p.memberId) {
    partyLeave({ roomCode: p.roomCode, memberId: p.memberId }).catch(() => {});
  }
  state.party = {
    ...state.party,
    roomCode: '',
    roomTitle: '',
    visibility: 'PRIVATE',
    controlMode: 'HOST_ONLY',
    hasPasscode: false,
    hostToken: '',
    hostName: '',
    hostInactive: false,
    memberId: '',
    nickname: '',
    isHost: false,
    pollTimer: null,
    lastMsgId: 0,
    lastReactionTs: 0,
    lastActionAt: 0,
    lastStateUpdatedAt: 0,
    chatVisible: false,
    applyingRemoteSync: false,
    softSyncActive: false,
    lastMovieSlug: '',
    lastEpisodeUrl: '',
    queue: [],
    members: [],
    countdownTimer: null,
  };
  savePartySessionStorage();
  renderPartyHeaderAndButton();
  if (!silent) showToast('🚪 Đã rời phòng Xem chung');
}

async function sendPartyChatMsg() {
  const p = state.party;
  const input = $('partyChatInput');
  const text = (input?.value || '').trim();
  if (!p.roomCode || !text) return;
  input.value = '';
  try {
    const res = await partySendChat({
      roomCode: p.roomCode,
      nickname: p.nickname,
      text,
      hostToken: p.hostToken,
    });
    if (res?.message) appendPartyMessages([res.message]);
  } catch (err) {
    showToast(err?.message || 'Gửi tin nhắn thất bại');
  }
}

function renderPartyCreatePickedMovie() {
  const box = $('partyCreatePickedMovie');
  if (!box) return;
  const m = state.party.createPickedMovie;
  const ep = state.party.createPickedEpisode;
  if (!m) {
    box.innerHTML = '<span style="font-size:12px;color:#9ca3af;">Chưa chọn phim — tìm nhanh bên dưới hoặc mở sẵn 1 bộ phim</span>';
    return;
  }
  box.innerHTML = `
    <img src="${escapeHtml(m.poster || './assets/icon-192.png?v=2')}" alt="" onerror="this.src='./assets/icon-192.png?v=2'" />
    <div style="min-width:0;flex:1;">
      <div style="font-size:13px;font-weight:800;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(getDisplayTitle(m))}</div>
      <div style="font-size:11px;color:#86efac;">${escapeHtml(ep?.name || 'Full')} • ${escapeHtml(m.year || 'HD')}</div>
    </div>
  `;
}

async function loadPartyLobby() {
  const listEl = $('partyLobbyList');
  if (!listEl) return;
  listEl.innerHTML = '<div class="party-empty-hint">⏳ Đang tải Sảnh Xem Chung công khai...</div>';
  try {
    const res = await partyListLobby();
    const rooms = Array.isArray(res?.rooms) ? res.rooms : [];
    if (!rooms.length) {
      listEl.innerHTML = `
        <div class="party-empty-hint">
          Hiện chưa có phòng công khai nào đang mở.<br>
          Bấm tab <strong>✨ Tạo Phòng Mới</strong> rồi chọn chế độ <strong>🌐 Công khai</strong> để rủ mọi người cùng xem!
        </div>
      `;
      return;
    }
    listEl.innerHTML = '';
    rooms.forEach((rm) => {
      const card = document.createElement('div');
      card.className = 'party-lobby-card';
      card.innerHTML = `
        <img src="${escapeHtml(rm.movie?.poster || './assets/icon-192.png?v=2')}" alt="" onerror="this.src='./assets/icon-192.png?v=2'" />
        <div style="min-width:0;flex:1;">
          <div style="font-size:13px;font-weight:800;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(rm.roomTitle || rm.movie?.name || rm.roomCode)}</div>
          <div style="font-size:11px;color:#cbd5e1;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">🎬 ${escapeHtml(rm.movie?.name || '')} • ${escapeHtml(rm.episodeName || 'Full')}</div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:4px;font-size:10.5px;color:#86efac;font-weight:700;">
            <span>👁️ ${rm.viewerCount} đang xem</span>
            <span>• 👑 ${escapeHtml(rm.hostName)}</span>
            ${rm.controlMode === 'EVERYONE' ? '<span>• 🤝 Chung quyền</span>' : ''}
            ${rm.hasPasscode ? '<span style="color:#fde68a;">• 🔒 Có mật khẩu</span>' : ''}
          </div>
        </div>
        <button class="btn-grad" type="button" style="padding:7px 11px;font-size:11.5px;white-space:nowrap;">▶ Vào</button>
      `;
      card.querySelector('button').onclick = () => {
        const nick = $('partyNickInput')?.value || getDefaultPartyNick();
        if (rm.hasPasscode) {
          openPartyHubModal('join', { prefillCode: rm.roomCode });
          $('partyJoinPassInput')?.focus();
          showToast('🔒 Nhập mật khẩu để vào phòng này');
        } else {
          joinWatchPartyByCode(rm.roomCode, nick);
        }
      };
      listEl.appendChild(card);
    });
  } catch {
    listEl.innerHTML = '<div class="party-empty-hint">Không tải được Sảnh công khai — hãy thử làm mới!</div>';
  }
}

async function searchMoviesForCreateParty() {
  const input = $('partyCreateSearchInput');
  const box = $('partyCreateSearchResults');
  const q = (input?.value || '').trim();
  if (!box || q.length < 2) {
    if (box) box.style.display = 'none';
    return;
  }
  box.style.display = 'flex';
  box.innerHTML = '<div style="font-size:11px;color:#9ca3af;padding:6px;">⏳ Đang tìm...</div>';
  try {
    const data = await fetchCatalogBrowse({ q, limit: 6, fast: 1 });
    const items = Array.isArray(data?.items) ? data.items : [];
    if (!items.length) {
      box.innerHTML = '<div style="font-size:11px;color:#9ca3af;padding:6px;">Không tìm thấy phim phù hợp.</div>';
      return;
    }
    box.innerHTML = '';
    items.slice(0, 5).forEach((m) => {
      const r = document.createElement('div');
      r.className = 'party-qsearch-row';
      r.innerHTML = `
        <img src="${escapeHtml(m.poster || './assets/icon-192.png?v=2')}" alt="" onerror="this.src='./assets/icon-192.png?v=2'" />
        <div style="min-width:0;flex:1;">
          <div style="font-size:12px;font-weight:800;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(getDisplayTitle(m))}</div>
          <div style="font-size:10.5px;color:#9ca3af;">${escapeHtml(m.year || '')} • ${escapeHtml(m.episode_current || m.quality || 'HD')}</div>
        </div>
        <button class="btn-grad" type="button" style="padding:4px 9px;font-size:11px;">Chọn</button>
      `;
      r.querySelector('button').onclick = async (ev) => {
        const b = ev.currentTarget;
        b.disabled = true;
        b.textContent = '...';
        try {
          const { movie, episode } = await resolveFirstEpisodeForPartyMovie(m);
          state.party.createPickedMovie = movie;
          state.party.createPickedEpisode = episode;
          renderPartyCreatePickedMovie();
          if ($('partyCreateTitleInput') && !$('partyCreateTitleInput').value.trim()) {
            $('partyCreateTitleInput').value = getDisplayTitle(movie);
          }
          box.style.display = 'none';
        } catch (err) {
          showToast(err?.message || 'Không lấy được luồng phát phim này');
          b.disabled = false;
          b.textContent = 'Chọn';
        }
      };
      box.appendChild(r);
    });
  } catch {
    box.innerHTML = '<div style="font-size:11px;color:#fca5a5;padding:6px;">Lỗi tìm kiếm.</div>';
  }
}

function switchPartyHubTab(tabName) {
  const clean = ['lobby', 'join', 'create'].includes(tabName) ? tabName : 'lobby';
  document.querySelectorAll('#partyHubTabs [data-hub-tab]').forEach((b) => {
    b.classList.toggle('active', b.dataset.hubTab === clean);
  });
  $('partyHubLobby')?.classList.toggle('active', clean === 'lobby');
  $('partyHubJoin')?.classList.toggle('active', clean === 'join');
  $('partyHubCreate')?.classList.toggle('active', clean === 'create');
  if (clean === 'lobby') loadPartyLobby();
  if (clean === 'create') renderPartyCreatePickedMovie();
}

function openPartyHubModal(tab = 'lobby', { prefillCode = '', movie = null, episode = null } = {}) {
  if ($('partyNickInput')) $('partyNickInput').value = getDefaultPartyNick();
  if (prefillCode && $('partyCodeInput')) $('partyCodeInput').value = String(prefillCode).toUpperCase();
  if (movie && episode) {
    state.party.createPickedMovie = movie;
    state.party.createPickedEpisode = episode;
    if ($('partyCreateTitleInput')) $('partyCreateTitleInput').value = getDisplayTitle(movie);
  } else if (!state.party.createPickedMovie && state.currentMovie && state.currentEpisode) {
    state.party.createPickedMovie = state.currentMovie;
    state.party.createPickedEpisode = state.currentEpisode;
  }
  switchPartyHubTab(prefillCode ? 'join' : tab);
  $('partyModal')?.classList.add('open');
}

function openPartyJoinModal(prefillCode = '') {
  openPartyHubModal(prefillCode ? 'join' : 'lobby', { prefillCode });
}

function openPlayer(movie, episode, startSec = 0, { preserveFailover = false, preserveParty = false } = {}) {
  // Save previous episode position if switching mid-session
  if (state.currentMovie && state.currentEpisode && $('playerModal').classList.contains('open')) {
    persistCurrentPlaybackProgress();
  }

  stopWebAiAudioSub(true);
  cancelAutoNext(false);
  clearStallWatchdog();

  state.currentMovie = movie;
  state.currentEpisode = episode;
  state.prefs.syncOffsetMs = getMovieSyncOffsetMs(movie.slug);
  syncBrowserUrl({ movie, episode, playing: true }, { push: true });
  state.skipIntroDismissed = false;
  state.autoNextCancelled = false;
  state.hlsNetworkRetries = 0;
  if (!preserveFailover) {
    state.failoverTriedServers = new Set();
  }
  if ($('skipIntroBtn')) $('skipIntroBtn').style.display = 'none';
  if ($('streamFailoverBar')) $('streamFailoverBar').style.display = 'none';
  if ($('hudAudioSubPrompt')) $('hudAudioSubPrompt').style.display = 'none';
  closePlayerDrawer();

  // Detect active server index cleanly
  if (Array.isArray(movie.servers) && movie.servers.length) {
    const foundSrv = movie.servers.findIndex((s) => (s.episodes || []).some((e) => e.link_m3u8 === episode.link_m3u8));
    if (foundSrv >= 0) {
      state.currentServerIdx = foundSrv;
      state.episodesFlat = movie.servers[foundSrv].episodes || state.episodesFlat;
    }
  }
  renderPlayerDrawer();
  updateMediaSession(movie, episode);
  if (state.party.roomCode && canControlParty() && !preserveParty) {
    state.party.lastMovieSlug = movie.slug;
    state.party.lastEpisodeUrl = episode.link_m3u8;
    pushHostPartyState({
      positionSec: startSec,
      isPlaying: true,
      movie: {
        slug: movie.slug,
        source: movie.source || 'phimapi',
        sourceId: movie.sourceId || '',
        name: getDisplayTitle(movie),
        poster: movie.poster || '',
        year: movie.year || '',
      },
      episode: {
        name: episode.name || 'Full',
        slug: episode.slug || 'full',
        link_m3u8: episode.link_m3u8,
      },
    });
  }
  renderPartyHeaderAndButton();

  const resolvedStartSec = startSec > 2 ? Math.floor(startSec) : getSavedEpisodePosition(movie.slug, episode);
  state.pendingSeekSec = resolvedStartSec;
  state.lastSavedPosTs = Date.now();

  const modal = $('playerModal');
  const video = $('videoEl');
  state.isMinimized = false;
  state.hudPointerInside = false;
  modal.classList.remove('minimized');
  modal.classList.add('open');
  document.documentElement.style.overflow = 'hidden';
  document.body.style.overflow = 'hidden';

  $('hudMovieTitle').textContent = `${getDisplayTitle(movie)} • ${episode.name}`;
  if ($('miniTitle')) $('miniTitle').textContent = `${getDisplayTitle(movie)} • ${episode.name}`;
  updateSubStatusBadge(tr('checkingSavedSub'));
  applyVolumeBoost(state.volumeScale);
  applyHardsubMaskUi();
  syncTtsUi();
  stopTtsSpeech();
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
    try {
      video.preservesPitch = true;
      video.mozPreservesPitch = true;
      video.webkitPreservesPitch = true;
    } catch { /* ignore */ }
    video.playbackRate = state.speed;
    if (state.audioCtx && state.audioCtx.state === 'suspended') {
      state.audioCtx.resume().catch(() => {});
    }
    video.play().catch(() => {});
  };

  video.onloadedmetadata = () => {
    if (needsWebAudioDsp()) ensureWebAudioDsp(video);
    applyPendingResumeSeek();
  };
  video.oncanplay = () => {
    applyPendingResumeSeek();
  };

  if (streamUrl.includes('.m3u8') && window.Hls && window.Hls.isSupported()) {
    const hls = new window.Hls({
      enableWorker: true,
      lowLatencyMode: false,
      startFragPrefetch: true,
      maxBufferLength: 24,
      maxMaxBufferLength: 45,
      backBufferLength: 30,
      maxAudioFramesDrift: 4,
      abrEwmaDefaultEstimate: 2_500_000,
      manifestLoadingTimeOut: 10000,
      fragLoadingTimeOut: 12000,
      startPosition: resolvedStartSec > 2 ? resolvedStartSec : -1,
      xhrSetup: (xhr) => { xhr.withCredentials = false; },
    });
    hls.loadSource(streamUrl);
    hls.attachMedia(video);
    hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
      if (needsWebAudioDsp()) ensureWebAudioDsp(video);
      applyVolumeBoost(state.volumeScale);
      applyEqPreset(state.eqPreset);
      applyNightAudioMode(state.prefs.nightAudio);
      renderHlsQualityPicker(hls);
      startPlayback();
    });
    hls.on(window.Hls.Events.LEVEL_SWITCHED, () => {
      updateStreamStatsBadge();
    });
    hls.on(window.Hls.Events.ERROR, (_, data) => {
      if (data.fatal) {
        if (data.type === window.Hls.ErrorTypes.NETWORK_ERROR && state.hlsNetworkRetries < 1) {
          state.hlsNetworkRetries += 1;
          hls.startLoad();
        } else if (data.type === window.Hls.ErrorTypes.MEDIA_ERROR && state.hlsNetworkRetries < 2) {
          state.hlsNetworkRetries += 1;
          if (state.hlsNetworkRetries === 2) {
            try { hls.swapAudioCodec(); } catch { /* ignore */ }
          }
          hls.recoverMediaError();
        } else {
          triggerAutoFailover('HLS stream error');
        }
      }
    });
    state.hls = hls;
  } else {
    video.src = streamUrl;
    video.onloadedmetadata = () => {
      if (needsWebAudioDsp()) ensureWebAudioDsp(video);
      startPlayback();
    };
  }

  // Next episode button visibility
  const curIdx = state.episodesFlat.findIndex((e) => e.link_m3u8 === episode.link_m3u8);
  const nextEp = curIdx >= 0 && curIdx + 1 < state.episodesFlat.length ? state.episodesFlat[curIdx + 1] : null;
  const nextBtn = $('hudNextOrAspectBtn');
  if (nextBtn) {
    if (nextEp) {
      nextBtn.classList.add('has-label');
      nextBtn.innerHTML = `${SVG_ICONS.nextEp}<span>${escapeHtml(tr('nextEpBtn').replace(/^[^\w\p{L}]+/u, '').trim() || 'Next')}</span>`;
      nextBtn.title = nextEp.name ? `Next: ${nextEp.name}` : tr('nextEpBtn');
    } else {
      nextBtn.classList.remove('has-label');
      nextBtn.innerHTML = SVG_ICONS.aspect;
      nextBtn.title = tr('aspectBtn');
    }
    nextBtn.onclick = () => {
      if (nextEp) {
        persistCurrentPlaybackProgress();
        openPlayer(movie, nextEp, 0);
      } else {
        openMoreModal();
      }
    };
  }

  // Defer scrub thumbnail preview by 2.8s so it NEVER competes for bandwidth during first-frame playback!
  clearTimeout(state.scrubDeferTimer);
  destroyScrubPreview();
  state.scrubDeferTimer = setTimeout(() => {
    if ($('playerModal')?.classList.contains('open') && state.currentEpisode?.link_m3u8 === streamUrl) {
      initScrubPreviewForStream(streamUrl);
    }
  }, 2800);
  if (state.prefs.ambientGlow) startAmbientGlowLoop();
  autoLoadSubtitlesForMovie(movie, episode, resolvedStartSec);
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
  $('playerStage')?.classList.remove('hud-hidden');
  document.documentElement.style.overflow = '';
  document.body.style.overflow = '';
  syncPlayIcons($('videoEl').paused);
  renderPartyHeaderAndButton();
  if ($('detailView')?.classList.contains('active') && state.currentMovie) {
    syncBrowserUrl({ movie: state.currentMovie, playing: false }, { push: false });
  } else {
    syncBrowserUrl({}, { push: false });
  }
}

function restorePlayer(e) {
  if (e && typeof e.stopPropagation === 'function') {
    e.stopPropagation();
  }
  const modal = $('playerModal');
  if (!modal.classList.contains('open')) return;
  state.isMinimized = false;
  state.hudPointerInside = false;
  modal.classList.remove('minimized');
  document.documentElement.style.overflow = 'hidden';
  document.body.style.overflow = 'hidden';
  renderPartyHeaderAndButton();
  setControlsVisible(true);
}

function closePlayer(e) {
  if (e && typeof e.stopPropagation === 'function') {
    e.stopPropagation();
  }
  stopActiveSubJob(true, true);
  stopTtsSpeech();
  stopAmbientGlowLoop();
  clearTimeout(state.scrubDeferTimer);
  destroyScrubPreview();
  cancelAutoNext(false);
  clearStallWatchdog();
  closePlayerDrawer();
  if (state.party.roomCode) leaveWatchParty(true);
  if ($('skipIntroBtn')) $('skipIntroBtn').style.display = 'none';
  if ($('streamFailoverBar')) $('streamFailoverBar').style.display = 'none';
  if ($('hudAudioSubPrompt')) $('hudAudioSubPrompt').style.display = 'none';
  const video = $('videoEl');
  persistCurrentPlaybackProgress({ immediate: true });
  renderHistorySection();
  if ($('detailView').classList.contains('active') && state.currentMovie && Array.isArray(state.currentMovie.servers) && state.currentMovie.servers.length) {
    renderDetailView(state.currentMovie);
  }
  video.pause();
  video.removeAttribute('src');
  disconnectAudioCaptureTap();
  if (state.audioCtx && state.audioCtx.state === 'running') {
    state.audioCtx.suspend().catch(() => {});
  }
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  else if (document.webkitFullscreenElement) document.webkitExitFullscreen?.();
  try { screen.orientation?.unlock?.(); } catch { /* ignore */ }
  $('playerModal').classList.remove('open', 'minimized');
  $('playerStage')?.classList.remove('hud-hidden');
  document.documentElement.style.overflow = '';
  document.body.style.overflow = '';
  state.isLocked = false;
  state.isMinimized = false;
  state.hudPointerInside = false;
  $('lockFab').classList.remove('visible');
}

function setControlsVisible(show, hideDelayMs = 1800) {
  state.controlsVisible = show;
  const hud = $('playerHud');
  const modal = $('playerModal');
  const stage = $('playerStage');
  const isHidden = !show || state.isLocked;
  hud.classList.toggle('hidden', isHidden);
  modal.classList.toggle('controls-visible', !isHidden);
  stage?.classList.toggle('hud-hidden', isHidden && !state.isMinimized && !$('videoEl').paused);
  clearTimeout(state.controlsTimer);
  if (show && !state.isLocked && !$('videoEl').paused && !state.hudPointerInside) {
    state.controlsTimer = setTimeout(() => setControlsVisible(false), hideDelayMs);
  }
}

/* ==================== UNIVERSAL AI SUBTITLES IN WEB PLAYER ==================== */
function updateSubStatusBadge(text) {
  state.subStatusText = text;
  const lang = resolveLanguage(state.prefs.primaryLang);
  $('hudSubStatus').textContent = text;
  $('hudSubLangPill').textContent = `CC • ${lang.badge}`;
}

/* ==================== BACKGROUND NEXT-EPISODE AI PRE-TRANSLATION (ZERO-WAIT BINGE) ==================== */
async function maybePreTranslateNextEpisode(curSec, durSec) {
  if (!state.prefs.subEnabled || !state.currentMovie || !state.currentEpisode) return;
  if (!durSec || !Number.isFinite(durSec) || durSec < 180) return;
  const ratio = curSec / durSec;
  const remaining = durSec - curSec;
  // Trigger silently once user crosses 78% OR has <= 6 minutes left
  if (ratio < 0.78 && remaining > 360) return;

  const nextEp = getNextEpisode();
  if (!nextEp || !nextEp.link_m3u8) return;
  const targetLang = resolveLanguage(state.prefs.primaryLang);
  const bgKey = buildSubCacheKey(state.currentMovie.slug, nextEp, targetLang.code);
  if (state.nextEpBgStartedKeys.has(bgKey) || state.nextEpBgReadyKeys.has(bgKey)) return;
  state.nextEpBgStartedKeys.add(bgKey);

  const movie = state.currentMovie;
  try {
    // 1. Check local IndexedDB first
    const localHit = await loadCachedEpisodeSub(bgKey);
    if (localHit && localHit.primaryCues?.length >= 5) {
      state.nextEpBgReadyKeys.add(bgKey);
      return;
    }
    // 2. Check NKI Cloud Pool first (0 Gemini tokens!)
    if (isCloudSignedIn()) {
      const cloudHit = await cloudFetchSubtitle(movie.slug, nextEp.name || nextEp.slug, targetLang.code);
      if (cloudHit && cloudHit.primaryCues?.length >= 5 && !hasWrongScriptForLang(cloudHit.primaryCues, targetLang.code)) {
        await saveCachedEpisodeSub(bgKey, {
          ...cloudHit,
          trackLabel: cloudHit.sourcePlatform || `☁️ NKI Cloud ${targetLang.badge}`,
          translatedCount: cloudHit.primaryCues.length,
        }).catch(() => {});
        state.nextEpBgReadyKeys.add(bgKey);
        return;
      }
    }

    if (!hasAiAudioCredentials()) return;
    const epLabel = nextEp.name || 'Tập tiếp';
    updateSubStatusBadge(`⚡ Đang dịch ngầm trước ${epLabel}...`);

    // 3. Try fast online SRT for next episode
    const epMatch = String(nextEp.name || '').match(/\d+/);
    const epNum = epMatch ? Number(epMatch[0]) : 1;
    const disc = await fetchCatalogSubtitles({
      imdbId: movie.imdbId || '',
      title: movie.nameEn || movie.name || '',
      originTitle: movie.origin_name || '',
      year: movie.year || '',
      isSeries: true,
      season: 1,
      episode: epNum,
    }).catch(() => null);

    const bestTrack = Array.isArray(disc?.tracks) && disc.tracks.length
      ? (disc.tracks.find((t) => t.lang === targetLang.code) || disc.tracks.find((t) => t.lang === 'en') || disc.tracks[0])
      : null;

    const movieCtx = {
      slug: movie.slug,
      title: getDisplayTitle(movie),
      originTitle: movie.origin_name || movie.name || '',
      synopsis: getDisplayDesc(movie).slice(0, 400),
      genres: movie.genres || [],
      glossary: movie.glossary || null,
    };

    if (bestTrack) {
      const rawText = await fetchSubtitleTextSmart(bestTrack);
      const srcCues = parseSubtitleText(rawText);
      if (srcCues.length >= 5) {
        let outCues = srcCues;
        if (bestTrack.lang !== targetLang.code) {
          const translated = [];
          for (let i = 0; i < Math.min(srcCues.length, 180); i += 55) {
            const slice = srcCues.slice(i, i + 55);
            const res = isCloudSignedIn()
              ? await cloudTranslateCueBatch({ cues: slice, targetLang: targetLang.code, model: state.prefs.geminiModel, movieContext: movieCtx }).catch(() => null)
              : await translateCueBatch({ cues: slice, targetLang: targetLang.code, apiKey: state.prefs.geminiApiKey, model: state.prefs.geminiModel }).catch(() => null);
            translated.push(...(res || slice));
          }
          if (srcCues.length > translated.length) {
            translated.push(...srcCues.slice(translated.length));
          }
          outCues = translated;
        }
        const payload = {
          primaryLangCode: targetLang.code,
          secondaryLangCode: state.prefs.dualSub ? state.prefs.secondaryLang : '',
          sourceLangCode: bestTrack.lang || 'auto',
          trackLabel: `⚡ Pre-Translated ${epLabel}`,
          sourceCues: srcCues,
          primaryCues: outCues,
          secondaryCues: state.prefs.dualSub ? srcCues : [],
          isFullyTranslated: outCues.length >= srcCues.length && srcCues.length <= 180,
          translatedCount: Math.min(srcCues.length, 180),
        };
        await saveCachedEpisodeSub(bgKey, payload).catch(() => {});
        if (isCloudSignedIn()) {
          cloudSaveSubtitle({ movieSlug: movie.slug, episodeNameOrSlug: nextEp.name || nextEp.slug, ...payload }).catch(() => {});
        }
        state.nextEpBgReadyKeys.add(bgKey);
        updateSubStatusBadge(`✅ ${epLabel} đã sẵn Sub ${targetLang.badge} • Xem ngay không chờ`);
        return;
      }
    }

    // 4. Fallback: Quietly pre-transcribe first ~5 minutes (8 x 38s HLS windows, 2x parallel) of Next Episode!
    if (nextEp.link_m3u8.includes('.m3u8')) {
      const parsed = await parseHlsMediaSegments(nextEp.link_m3u8).catch(() => null);
      const segs = parsed?.segments || [];
      if (segs.length >= 2) {
        const allWins = buildHlsAudioWindows(segs, HLS_AUDIO_WINDOW_SEC);
        const firstWins = allWins.slice(0, 8); // ~5 minutes ahead!
        const keyCache = new Map();
        const collected = [];
        const doneWinIdx = [];
        const doneRanges = [];
        let cursor = 0;
        const runWorker = async () => {
          while (cursor < firstWins.length) {
            const win = firstWins[cursor++];
            const extracted = await extractHlsWindowAdtsBase64(win, keyCache).catch(() => null);
            if (!extracted?.audioBase64) continue;
            const res = isCloudSignedIn()
              ? await cloudTranscribeAudioChunk({
                  audioBase64: extracted.audioBase64,
                  mimeType: extracted.mimeType || 'audio/aac',
                  chunkStartOffsetMs: win.startMs,
                  chunkDurationMs: win.durationMs,
                  targetLang: targetLang.code,
                  model: state.prefs.geminiModel,
                  movieContext: movieCtx,
                }).catch(() => null)
              : await transcribeAudioChunkDirect({
                  audioBase64: extracted.audioBase64,
                  mimeType: extracted.mimeType || 'audio/aac',
                  chunkStartOffsetMs: win.startMs,
                  chunkDurationMs: win.durationMs,
                  targetLang: targetLang.code,
                  apiKey: state.prefs.geminiApiKey,
                  modelId: state.prefs.geminiModel,
                }).catch(() => null);
            if (res && Array.isArray(res.cues)) {
              doneWinIdx.push(win.index);
              doneRanges.push([win.startMs, win.endMs]);
              if (res.cues.length) collected.push(...res.cues);
            }
          }
        };
        await Promise.all([runWorker(), runWorker()]);
        if (collected.length >= 3) {
          collected.sort((a, b) => (a.startMs || 0) - (b.startMs || 0));
          const mergedRanges = mergeTimeRanges(doneRanges);
          const coveredUntilMs = mergedRanges.length ? mergedRanges[mergedRanges.length - 1][1] : (firstWins[firstWins.length - 1]?.endMs || 300000);
          const payload = {
            primaryLangCode: targetLang.code,
            secondaryLangCode: '',
            sourceLangCode: 'auto',
            trackLabel: `⚡ Pre-Translated Audio ${epLabel}`,
            sourceCues: collected,
            primaryCues: collected,
            secondaryCues: [],
            isFullyTranslated: doneWinIdx.length >= allWins.length,
            audioMode: true,
            audioTranslatedUntilMs: coveredUntilMs,
            totalDurationMs: parsed?.totalDurationMs || 0,
            translatedWindows: doneWinIdx,
            translatedRanges: mergedRanges,
            translatedCount: collected.length,
          };
          await saveCachedEpisodeSub(bgKey, payload).catch(() => {});
          if (isCloudSignedIn()) {
            cloudSaveSubtitle({ movieSlug: movie.slug, episodeNameOrSlug: nextEp.name || nextEp.slug, ...payload }).catch(() => {});
          }
          state.nextEpBgReadyKeys.add(bgKey);
          updateSubStatusBadge(`✅ ${epLabel} đã sẵn 5 phút đầu Sub ${targetLang.badge}`);
        }
      }
    }
  } catch {
    // non-fatal background pre-translation
  }
}

async function persistCurrentSubCache(isFullyTranslated, translatedCount, { skipCloudPush = false } = {}) {
  if (!state.currentMovie || !state.currentEpisode || state.primaryCues.length < 3) return;
  const targetLang = resolveLanguage(state.prefs.primaryLang);
  if (hasWrongScriptForLang(state.primaryCues, targetLang.code)) return;
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

async function autoLoadSubtitlesForMovie(movie, episode, startSec = 0) {
  const runId = ++state.subTranslationRunId;
  const targetLang = resolveLanguage(state.prefs.primaryLang);
  state.liveAudioFallbackCue = null;

  // SEAMLESS HANDOFF: If user already started pre-translating this exact episode on the Detail Page,
  // attach immediately without canceling or losing progress!
  if (isSubJobFor(movie, episode, targetLang.code) && (state.subJob.active || state.subJob.cues.length >= 3)) {
    state.sourceCues = state.subJob.cues.slice();
    state.primaryCues = state.subJob.cues.slice();
    state.secondaryCues = state.prefs.dualSub ? state.sourceCues : [];
    renderActiveSubtitle(($('videoEl')?.currentTime || startSec || 0) * 1000);
    syncAllSubProgressUi();
    if (state.subJob.audioMode && state.subJob.hlsWindows.length) {
      maybeRefillHlsLookahead(startSec || $('videoEl')?.currentTime || 0, { forceJump: true });
    }
    return;
  }

  state.subTracks = [];
  state.sourceCues = [];
  state.primaryCues = [];
  state.secondaryCues = [];
  setHudSubProgress(0, false);
  renderActiveSubtitle(0);

  const cacheKey = buildSubCacheKey(movie.slug, episode, targetLang.code);

  // STEP 0A: INSTANT RESTORE FROM LOCAL INDEXEDDB (<10ms, checks canonical + legacy keys)
  let cached = await loadCachedSubSmart(movie.slug, episode, targetLang.code);
  let fromCloud = false;
  if (runId !== state.subTranslationRunId) return;

  // STEP 0B: SHARED NKI CLOUD SUBTITLE POOL ON VERCEL BLOB (<150ms, 0 GEMINI TOKENS!)
  if ((!cached || !cached.isFullyTranslated) && isCloudSignedIn()) {
    if (!cached) updateSubStatusBadge(`☁️ Đang kiểm tra kho Sub NKI Cloud (${targetLang.badge})...`);
    const cloudBundle = await cloudFetchSubtitle(movie.slug, episode.name || episode.slug, targetLang.code);
    if (runId !== state.subTranslationRunId) return;
    const normCloud = cloudBundle && !hasWrongScriptForLang(cloudBundle.primaryCues, targetLang.code)
      ? normalizeCloudSubBundle(cloudBundle, targetLang)
      : null;
    if (normCloud) {
      if (!cached) {
        cached = normCloud;
        fromCloud = true;
      } else {
        cached = {
          ...cached,
          ...normCloud,
          audioMode: Boolean(cached.audioMode || normCloud.audioMode),
          isFullyTranslated: Boolean(cached.isFullyTranslated || normCloud.isFullyTranslated),
          primaryCues: mergeCuesChronological(cached.primaryCues, normCloud.primaryCues),
          sourceCues: mergeCuesChronological(cached.sourceCues || cached.primaryCues, normCloud.sourceCues || normCloud.primaryCues),
          translatedWindows: [...new Set([...(cached.translatedWindows || []), ...(normCloud.translatedWindows || [])])],
          translatedRanges: mergeTimeRanges([...(cached.translatedRanges || []), ...(normCloud.translatedRanges || [])]),
          audioTranslatedUntilMs: Math.max(Number(cached.audioTranslatedUntilMs || 0), Number(normCloud.audioTranslatedUntilMs || 0)),
          totalDurationMs: Math.max(Number(cached.totalDurationMs || 0), Number(normCloud.totalDurationMs || 0)),
        };
      }
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

    const covSec = Math.floor((cached.audioTranslatedUntilMs || 0) / 1000);
    updateSubStatusBadge(
      fromCloud
        ? `☁️ Sub ${targetLang.badge} • NKI Cloud (${state.primaryCues.length} câu)`
        : cached.isFullyTranslated
          ? `💾 Sub ${targetLang.badge} • 100% Đã lưu (${state.primaryCues.length} câu)`
          : covSec > 0
            ? `💾 Sub ${targetLang.badge} • Đã dịch tới ${formatTime(covSec)} (${state.primaryCues.length} câu)`
            : `💾 Sub ${targetLang.badge} • Đã lưu (${state.primaryCues.length} câu)`
    );
    renderActiveSubtitle(($('videoEl')?.currentTime || startSec || 0) * 1000);

    // If this was a partial Audio AI cache (e.g. first 10 minutes pre-translated from Detail Page!),
    // automatically prime the 10-minute rolling HLS Lookahead engine so playback stays 7–10 min ahead!
    if (cached.audioMode && !cached.isFullyTranslated && episode.link_m3u8?.includes('.m3u8') && hasAiAudioCredentials()) {
      startUnifiedAiSubJob({
        movie,
        episode,
        langCode: targetLang.code,
        scope: 'lookahead10',
        startSec: startSec || $('videoEl')?.currentTime || 0,
        initiatedFrom: 'player',
        preferAudioOnly: true,
      });
      return;
    }

    // Hydrate online track list silently in background for track switcher
    const isSeriesBg = movie.type !== 'single' || state.episodesFlat.length > 1;
    const epMatchBg = String(episode.name || '').match(/\d+/);
    const epNumBg = epMatchBg ? Number(epMatchBg[0]) : 1;
    fetchCatalogSubtitles({
      imdbId: movie.imdbId || '',
      title: movie.nameEn || movie.name || '',
      originTitle: movie.origin_name || '',
      year: movie.year || '',
      isSeries: isSeriesBg,
      season: 1,
      episode: epNumBg,
    }).then((disc) => {
      if (state.currentEpisode?.link_m3u8 === episode.link_m3u8 && Array.isArray(disc?.tracks)) {
        state.subTracks = disc.tracks;
      }
    }).catch(() => {});

    // Resume remaining SRT translation ONLY if a previous session closed mid-translation
    if (!cached.isFullyTranslated && !cached.audioMode && (cached.translatedCount || 0) < state.sourceCues.length) {
      await applyLoadedSourceCues(state.sourceCues, state.lastSubSourceLang, {
        resumeWorking: state.primaryCues.slice(),
        trackLabel: state.lastSubTrackLabel,
        startSec,
      });
    } else if (wantSec && (!cached.secondaryCues?.length || cached.secondaryLangCode !== state.prefs.secondaryLang)) {
      await translateSecondaryInBackground(runId, state.sourceCues, state.lastSubSourceLang);
    }
    return;
  }

  // Fresh episode -> run Unified Auto-Discover (Cinemeta + OpenSubtitles SRT 3x55 Parallel OR 10-Min HLS Audio AI!)
  await startUnifiedAiSubJob({
    movie,
    episode,
    langCode: targetLang.code,
    scope: 'lookahead10',
    startSec: startSec || $('videoEl')?.currentTime || 0,
    initiatedFrom: 'player',
  });
}

async function selectSubtitleTrack(track) {
  if (!track) return;
  state.activeSubTrackId = track.id;
  state.lastSubTrackLabel = track.label || '';
  updateSubStatusBadge(`📥 Đang tải ${track.label}...`);
  try {
    const raw = await fetchSubtitleTextSmart(track);
    const cues = parseSubtitleText(raw);
    if (!cues.length) {
      updateSubStatusBadge('File phụ đề rỗng — thử bản khác');
      return;
    }
    await applyLoadedSourceCues(cues, track.langCode, {
      trackLabel: track.label,
      skipCacheLookup: true,
      startSec: Math.floor($('videoEl')?.currentTime || 0),
    });
  } catch {
    updateSubStatusBadge('Không tải được file phụ đề — thử chọn bản khác hoặc nạp .SRT');
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
  const batches = [];
  for (let i = 0; i < cues.length; i += SRT_BATCH_SIZE) {
    batches.push({ start: i, end: Math.min(cues.length, i + SRT_BATCH_SIZE) });
  }
  let cursor = 0;
  const movieCtx = buildMovieContext(state.currentMovie);
  await Promise.all(
    Array.from({ length: Math.min(2, batches.length) }, async () => {
      while (cursor < batches.length) {
        if (runId !== state.subTranslationRunId) return;
        const b = batches[cursor++];
        const chunk = cues.slice(b.start, b.end);
        const translated = await translateCueBatch({
          cues: chunk,
          targetLang: secCode,
          geminiApiKey: state.prefs.geminiApiKey,
          geminiModel: state.prefs.geminiModel,
          cloudTranslator: isCloudSignedIn() ? cloudTranslateCueBatch : null,
          movieContext: movieCtx,
        });
        if (runId !== state.subTranslationRunId) return;
        for (let j = 0; j < translated.length; j++) workingSec[b.start + j] = translated[j];
        state.secondaryCues = workingSec.slice();
      }
    })
  );
  if (runId === state.subTranslationRunId) {
    await persistCurrentSubCache(true, cues.length);
  }
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
      setHudSubProgress(100, false);
      renderActiveSubtitle(($('videoEl')?.currentTime || 0) * 1000);
      if (state.prefs.dualSub && state.prefs.secondaryLang !== 'off' && cached.secondaryLangCode !== state.prefs.secondaryLang) {
        await translateSecondaryInBackground(runId, cues, sourceLangCode);
      }
      return;
    }
  }

  state.primaryCues = opts.resumeWorking ? opts.resumeWorking.slice() : cues;
  state.secondaryCues = state.prefs.dualSub ? cues : [];

  // Reuse unified 3x55 parallel playhead-first SRT translator + live HUD progress bar!
  const j = state.subJob;
  stopActiveSubJob(true, true);
  const jobId = ++j.id;
  j.active = true;
  j.mode = 'srt';
  j.phase = 'translating';
  j.movieSlug = normalizeMovieSlug(state.currentMovie?.slug || 'local');
  j.episodeKey = normalizeEpisodeKey(state.currentEpisode?.name || state.currentEpisode?.slug || 'full');
  j.episodeName = state.currentEpisode?.name || 'Full';
  j.langCode = targetLang.code;
  j.movie = state.currentMovie;
  j.episode = state.currentEpisode;
  j.cues = state.primaryCues;
  j.abortCtrl = new AbortController();

  await runParallelSrtTranslation({
    job: j,
    jobId,
    movie: state.currentMovie,
    episode: state.currentEpisode,
    cues,
    sourceLangCode,
    targetLang,
    startSec: opts.startSec ?? Math.floor($('videoEl')?.currentTime || 0),
    trackLabel: opts.trackLabel || state.lastSubTrackLabel || `Sub ${targetLang.badge}`,
    resumeWorking: opts.resumeWorking || null,
  });

  if (runId === state.subTranslationRunId) {
    await translateSecondaryInBackground(runId, cues, sourceLangCode);
  }
}

function renderActiveSubtitle(currentMs) {
  const pill = $('subPill');
  if (!pill) return;
  if (!state.prefs.subEnabled) {
    pill.innerHTML = '';
    return;
  }
  const effectiveMs = currentMs - state.prefs.syncOffsetMs;
  let pText = findActiveCueText(state.primaryCues, effectiveMs);
  // Grace window for non-HLS live MediaRecorder fallback so lines are NEVER dropped in the past
  if (!pText && state.liveAudioFallbackCue && Date.now() < state.liveAudioFallbackCue.expiresAt) {
    pText = state.liveAudioFallbackCue.text;
  }
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

  // #3 AI Voice Dubbing / TTS Auto-Ducking synchronized to active cue
  if (state.prefs.ttsDubbing && pText && !$('videoEl')?.paused && !state.isMinimized) {
    speakSubtitleLineWithDucking(pText);
  }
}

/* ==================== V14 CINEMA PRO ENGINES (#1 HARDSUB MASK, #3 AI TTS DUB, #5 SCRUB THUMB, #6 AMBIENT GLOW, #8 QR TV, #11 TRAILER) ==================== */

// #1 Hardsub Blur / Mask Overlay
function applyHardsubMaskUi() {
  const mask = $('hardsubMask');
  const subLayer = $('subLayer');
  const on = Boolean(state.prefs.hardsubMask);
  const mode = state.prefs.hardsubMaskMode === 'solid' ? 'solid' : 'blur';
  const height = Math.max(24, Math.min(130, Number(state.prefs.hardsubMaskHeight) || 58));
  const bottom = Math.max(0, Math.min(26, Number(state.prefs.hardsubMaskBottom) || 3));

  if (mask) {
    mask.style.display = on ? 'block' : 'none';
    mask.classList.toggle('mode-solid', mode === 'solid');
    mask.style.height = `${height}px`;
    mask.style.bottom = `${bottom}%`;
  }
  if (subLayer) {
    subLayer.style.zIndex = '15';
  }
  if ($('hudMaskBtn')) $('hudMaskBtn').classList.toggle('active', on);
  if ($('hardsubMaskToggle')) $('hardsubMaskToggle').checked = on;
  if ($('hardsubMaskMode')) $('hardsubMaskMode').value = mode;
  if ($('hardsubHeightRange')) $('hardsubHeightRange').value = String(height);
  if ($('hardsubHeightLabel')) $('hardsubHeightLabel').textContent = `${height}px`;
  if ($('hardsubBottomRange')) $('hardsubBottomRange').value = String(bottom);
  if ($('hardsubBottomLabel')) $('hardsubBottomLabel').textContent = `${bottom}%`;
}

// #3 AI Voice Dubbing / TTS with Web Audio Auto-Ducking
function setTtsAudioDuck(ducked) {
  if (state.ttsDucked === ducked) return;
  state.ttsDucked = ducked;
  const baseVol = state.volumeScale;
  const targetVol = ducked ? baseVol * 0.32 : baseVol;
  const video = $('videoEl');
  if (state.gainNode && state.audioCtx) {
    try {
      state.gainNode.gain.setTargetAtTime(Math.max(0, targetVol), state.audioCtx.currentTime, 0.08);
      return;
    } catch { /* fallback below */ }
  }
  if (video) {
    video.volume = Math.max(0, Math.min(1, targetVol));
  }
}

function stopTtsSpeech() {
  state.ttsLastSpokenText = '';
  try { window.speechSynthesis?.cancel?.(); } catch { /* ignore */ }
  setTtsAudioDuck(false);
}

function syncTtsUi() {
  const on = Boolean(state.prefs.ttsDubbing);
  const rate = Math.max(0.85, Math.min(1.5, Number(state.prefs.ttsRate) || 1.15));
  if ($('hudTtsBtn')) $('hudTtsBtn').classList.toggle('active', on);
  if ($('ttsDubToggle')) $('ttsDubToggle').checked = on;
  if ($('ttsRateRange')) $('ttsRateRange').value = String(Math.round(rate * 100));
  if ($('ttsRateLabel')) $('ttsRateLabel').textContent = `${rate.toFixed(2)}x`;
}

function speakSubtitleLineWithDucking(text) {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;
  const clean = String(text || '').replace(/\s+/g, ' ').trim();
  if (!clean || clean === state.ttsLastSpokenText) return;
  state.ttsLastSpokenText = clean;

  try {
    window.speechSynthesis.cancel();
    const utter = new SpeechSynthesisUtterance(clean);
    const langCode = resolveLanguage(state.prefs.primaryLang).code;
    const bcpMap = { vi: 'vi-VN', en: 'en-US', th: 'th-TH', zh: 'zh-CN', 'zh-TW': 'zh-TW', ko: 'ko-KR', ja: 'ja-JP', fr: 'fr-FR', es: 'es-ES', de: 'de-DE' };
    const wantLang = bcpMap[langCode] || langCode || 'vi-VN';
    utter.lang = wantLang;
    utter.rate = Math.max(0.85, Math.min(1.5, Number(state.prefs.ttsRate) || 1.15));
    const voices = window.speechSynthesis.getVoices?.() || [];
    const matched = voices.find((v) => v.lang?.toLowerCase().startsWith(wantLang.slice(0, 2).toLowerCase()));
    if (matched) utter.voice = matched;

    utter.onstart = () => setTtsAudioDuck(true);
    utter.onend = () => setTtsAudioDuck(false);
    utter.onerror = () => setTtsAudioDuck(false);
    window.speechSynthesis.speak(utter);
  } catch {
    setTtsAudioDuck(false);
  }
}

// #6 Ambient Cinema Glow (4 FPS ultra-light 32x18 frame sampler behind video)
function stopAmbientGlowLoop() {
  clearInterval(state.ambientTimer);
  state.ambientTimer = null;
  $('ambientGlowCanvas')?.classList.remove('active');
}

function startAmbientGlowLoop() {
  stopAmbientGlowLoop();
  const canvas = $('ambientGlowCanvas');
  const video = $('videoEl');
  if (!canvas || !video || !state.prefs.ambientGlow) return;
  const ctx = canvas.getContext('2d', { willReadFrequently: false });
  if (!ctx) return;

  state.ambientTimer = setInterval(() => {
    if (!$('playerModal')?.classList.contains('open') || state.isMinimized || video.paused || video.readyState < 2) {
      return;
    }
    try {
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      canvas.classList.add('active');
    } catch {
      // Cross-origin tainted frame -> stop silently with zero console spam
      stopAmbientGlowLoop();
    }
  }, 250);
}

// #5 Seekbar Video Thumbnail Scrubbing
function destroyScrubPreview() {
  if (state.scrubHls) {
    try { state.scrubHls.destroy(); } catch { /* ignore */ }
    state.scrubHls = null;
  }
  const sv = $('scrubVideoEl');
  if (sv) {
    sv.removeAttribute('src');
    try { sv.load(); } catch { /* ignore */ }
  }
  state.scrubLoadedUrl = '';
  if ($('scrubCanvas')) $('scrubCanvas').style.display = 'none';
}

function initScrubPreviewForStream(streamUrl) {
  destroyScrubPreview();
  const sv = $('scrubVideoEl');
  if (!sv || !streamUrl || state.isMinimized) return;
  state.scrubLoadedUrl = streamUrl;
  sv.crossOrigin = 'anonymous';
  sv.muted = true;

  if (streamUrl.includes('.m3u8') && window.Hls && window.Hls.isSupported()) {
    try {
      const h = new window.Hls({
        enableWorker: true,
        autoStartLoad: true,
        maxBufferLength: 6,
        maxMaxBufferLength: 10,
        startLevel: 0, // Lowest quality variant for instant thumbnail scrubbing!
        xhrSetup: (xhr) => { xhr.withCredentials = false; },
      });
      h.loadSource(streamUrl);
      h.attachMedia(sv);
      state.scrubHls = h;
    } catch { /* ignore */ }
  } else {
    sv.src = streamUrl;
  }

  sv.onseeked = () => {
    const c = $('scrubCanvas');
    if (!c) return;
    try {
      const ctx = c.getContext('2d');
      ctx.drawImage(sv, 0, 0, c.width, c.height);
      c.style.display = 'block';
    } catch {
      c.style.display = 'none';
    }
  };
}

function renderScrubThumbAt(targetSec) {
  const sv = $('scrubVideoEl');
  if (!sv || !state.scrubLoadedUrl || !Number.isFinite(targetSec)) return;
  const now = Date.now();
  if (now - state.scrubLastHoverTs < 170) return;
  state.scrubLastHoverTs = now;
  if (sv.readyState >= 1) {
    try { sv.currentTime = Math.max(0, targetSec); } catch { /* ignore */ }
  }
}

// #8 QR Pairing for Smart TV
function renderTvQrCode() {
  const img = $('tvQrImg');
  const hint = $('tvQrHint');
  const ip = (state.prefs.tvIp || $('tvIpInput')?.value || '').trim();
  if (!img || !hint) return;
  if (!ip) {
    img.style.display = 'none';
    hint.textContent = 'Nhập IP Smart TV bên trên rồi bấm Lưu IP để tạo mã QR — quét bằng điện thoại để kết nối & điều khiển TV trong 1 giây!';
    return;
  }
  const pairUrl = `${window.location.origin}/?tvip=${encodeURIComponent(ip)}`;
  img.src = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&margin=6&data=${encodeURIComponent(pairUrl)}`;
  img.style.display = 'block';
  hint.innerHTML = `Quét mã QR bằng điện thoại (cùng Wi-Fi) để ghép nối ngay với TV <strong>${escapeHtml(ip)}</strong> và tìm phim tiếng Việt bắn thẳng lên TV!`;
}

// #11 Trailer YouTube Popup Modal
function extractYoutubeId(url) {
  const s = String(url || '').trim();
  if (!s) return '';
  const m = s.match(/(?:youtube\.com\/(?:watch\?v=|embed\/|shorts\/)|youtu\.be\/)([A-Za-z0-9_-]{11})/);
  return m ? m[1] : '';
}

function openTrailerModal(detail) {
  const modal = $('trailerModal');
  const wrap = $('trailerEmbedWrap');
  const titleEl = $('trailerModalTitle');
  if (!modal || !wrap) return;
  const dispTitle = getDisplayTitle(detail);
  if (titleEl) titleEl.textContent = `🎬 Trailer — ${dispTitle}`;

  const ytId = extractYoutubeId(detail.trailerUrl);
  const searchQuery = encodeURIComponent(`${detail.origin_name || detail.name || dispTitle} ${detail.year || ''} official trailer`.trim());
  if (ytId) {
    wrap.innerHTML = `<iframe src="https://www.youtube.com/embed/${ytId}?autoplay=1&rel=0" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>`;
  } else {
    wrap.innerHTML = `
      <div style="text-align:center;padding:24px;display:flex;flex-direction:column;align-items:center;gap:14px;">
        <div style="font-size:14px;color:#e5e7eb;font-weight:700;">Chưa có link YouTube gắn sẵn cho "${escapeHtml(dispTitle)}"</div>
        <a class="btn-ai-sub-primary" href="https://www.youtube.com/results?search_query=${searchQuery}" target="_blank" rel="noopener" style="text-decoration:none;">▶ Mở Trailer trên YouTube ngay</a>
      </div>
    `;
  }
  modal.classList.add('open');
}

function closeTrailerModal() {
  $('trailerModal')?.classList.remove('open');
  if ($('trailerEmbedWrap')) $('trailerEmbedWrap').innerHTML = '';
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
  const curSync = state.prefs.syncOffsetMs || 0;
  $('syncValLabel').textContent = `${(curSync / 1000).toFixed(2).replace(/\.00$/, '.0')}s`;
  if ($('syncRangeSlider')) $('syncRangeSlider').value = String(Math.max(-10000, Math.min(10000, curSync)));
  applyHardsubMaskUi();
  syncTtsUi();
  syncAudioSubUi();
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
      if (state.sourceCues.length && !state.subJob.audioMode) {
        applyLoadedSourceCues(state.sourceCues, 'auto');
      } else if (state.currentMovie && state.currentEpisode) {
        autoLoadSubtitlesForMovie(state.currentMovie, state.currentEpisode, Math.floor($('videoEl')?.currentTime || 0));
      }
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
  applyNightAudioMode(state.prefs.nightAudio);
  renderHlsQualityPicker(state.hls);
  syncSkipIntroSettingsUi();
  if ($('ambientGlowToggle')) $('ambientGlowToggle').checked = Boolean(state.prefs.ambientGlow);
  if ($('moreQuickDubBtn')) $('moreQuickDubBtn').classList.toggle('active', Boolean(state.prefs.ttsDubbing));
  if ($('moreQuickMaskBtn')) $('moreQuickMaskBtn').classList.toggle('active', Boolean(state.prefs.hardsubMask));
  if ($('moreQuickPartyBtn')) $('moreQuickPartyBtn').classList.toggle('active', Boolean(state.party.roomCode));
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
      const label = (session.user.displayName || session.user.username || 'Cloud').trim();
      txt.textContent = label.slice(0, 12);
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

  // Show instant localized status from cached session (no English "Checking..." flicker!)
  if (statusEl) {
    if (session?.user) {
      statusEl.textContent = session.serverAiReady ? tr('cloudActiveSub') : tr('cloudKeyPendingSub');
    } else if (state.cloudHealth?.ok) {
      statusEl.textContent = !state.cloudHealth.hasAdmin
        ? '👑 Tài khoản đầu tiên tạo mới sẽ trở thành ADMIN'
        : (state.cloudHealth.registrationMode === 'INVITE_ONLY'
            ? '🔒 Đăng nhập hoặc đăng ký bằng Mã mời từ Admin'
            : tr('cloudActiveSub'));
    } else {
      statusEl.textContent = tr('cloudChecking');
    }
  }

  // Check health in background to detect Bootstrap Admin / Server AI readiness
  fetchCloudHealth().then((h) => {
    state.cloudHealth = h;
    if (statusEl) {
      if (!h.ok) {
        statusEl.textContent = '⚠️ ' + tr('cloudOfflineSub');
      } else if (session?.user) {
        statusEl.textContent = h.serverAiReady ? tr('cloudActiveSub') : tr('cloudKeyPendingSub');
      } else if (!h.hasAdmin) {
        statusEl.textContent = '👑 Tài khoản đầu tiên tạo mới sẽ trở thành ADMIN';
      } else {
        statusEl.textContent = h.registrationMode === 'INVITE_ONLY'
          ? '🔒 Đăng nhập hoặc đăng ký bằng Mã mời từ Admin'
          : tr('cloudActiveSub');
      }
    }
    syncCloudAuthFormFields();
  }).catch(() => {
    if (statusEl) statusEl.textContent = tr('cloudOfflineSub');
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
    if ($('profileDisplayNameInput')) {
      $('profileDisplayNameInput').value = session.user.displayName || session.user.username || '';
    }
    if ($('profileAccountMeta')) {
      const createdStr = session.user.createdAt ? new Date(session.user.createdAt).toLocaleDateString() : '—';
      $('profileAccountMeta').innerHTML = [
        `👤 <strong>Username:</strong> @${escapeHtml(session.user.username)} • <strong>Vai trò:</strong> ${escapeHtml(session.user.role || 'MEMBER')}`,
        `📅 <strong>Ngày tạo:</strong> ${escapeHtml(createdStr)} • <strong>Đồng bộ:</strong> Web ↔ Android ↔ Smart TV`,
      ].join('<br>');
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
  if ($('cloudDisplayNameWrap')) $('cloudDisplayNameWrap').style.display = isReg ? 'block' : 'none';
  if ($('cloudConfirmPasswordWrap')) $('cloudConfirmPasswordWrap').style.display = isReg ? 'block' : 'none';
  if ($('cloudPasswordInput')) {
    $('cloudPasswordInput').setAttribute('autocomplete', isReg ? 'new-password' : 'current-password');
  }
  const needInvite = isReg && state.cloudHealth?.hasAdmin && state.cloudHealth?.registrationMode === 'INVITE_ONLY';
  if ($('cloudInviteWrap')) $('cloudInviteWrap').style.display = needInvite ? 'block' : 'none';
  const hint = $('cloudBootstrapHint');
  if (hint) {
    if (isReg && state.cloudHealth && !state.cloudHealth.hasAdmin) {
      hint.style.display = 'block';
      hint.textContent = '👑 Chưa có Admin — tài khoản đầu tiên sẽ tự động thành ADMIN và bật chế độ Invite-Only!';
    } else if (needInvite) {
      hint.style.display = 'block';
      hint.textContent = '🔒 Admin đang bật chế độ Invite-Only. Nhập mã mời NKI-XXXX-XXXX được cấp.';
    } else {
      hint.style.display = 'none';
    }
  }
  if ($('cloudAuthSubmitBtn')) {
    $('cloudAuthSubmitBtn').textContent = isReg ? '✨ Tạo tài khoản NKI & Đồng bộ' : '☁️ Đăng nhập & Đồng bộ';
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
      statusEl.textContent = tr('modelsLoadingText');
    } else if (state.liveModels.length) {
      statusEl.textContent = tr('modelsLoadedText', { n: state.liveModels.length });
    } else if (isCloudSignedIn() || state.prefs.geminiApiKey) {
      statusEl.textContent = tr('modelsClickLoadText');
    } else {
      statusEl.textContent = tr('modelsSignInHint');
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
  renderTvQrCode();
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

function closeAllSearchDropdowns() {
  $('searchDropdownDesktop')?.classList.remove('open');
  $('searchDropdownMobile')?.classList.remove('open');
}

function setupInstantSearchDropdown(inputId, dropdownId) {
  const input = $(inputId);
  const dd = $(dropdownId);
  if (!input || !dd) return;

  let debounceTimer = null;
  let fetchSeq = 0;

  const renderHistoryInDropdown = () => {
    const hist = loadSearchHistory();
    if (!hist.length) {
      dd.classList.remove('open');
      return;
    }
    dd.innerHTML = `
      <div class="sd-section-head">
        <span>🕒 Tìm kiếm gần đây</span>
        <button class="sd-clear-btn" type="button" data-sd-clear="1">Xóa</button>
      </div>
      <div class="sd-history-chips">
        ${hist.map((term) => `<button class="sd-history-chip" type="button" data-sd-term="${escapeHtml(term)}">🔍 ${escapeHtml(term)}</button>`).join('')}
      </div>
    `;
    dd.classList.add('open');
    dd.querySelector('[data-sd-clear]')?.addEventListener('click', (e) => {
      e.stopPropagation();
      clearSearchHistory();
      dd.classList.remove('open');
    });
    dd.querySelectorAll('[data-sd-term]').forEach((btn) => {
      btn.onclick = (e) => {
        e.stopPropagation();
        const term = btn.dataset.sdTerm || '';
        input.value = term;
        closeAllSearchDropdowns();
        $('mobileSearchBar')?.classList.remove('open');
        executeSearch(term);
      };
    });
  };

  const renderResultsInDropdown = (items, query) => {
    if (!items.length) {
      dd.innerHTML = `<div style="padding:12px 10px;font-size:12px;color:#9ca3af;text-align:center;">Không thấy ngay "${escapeHtml(query)}" — bấm Enter để tìm rộng hoặc <strong>✨ AI</strong></div>`;
      dd.classList.add('open');
      return;
    }
    dd.innerHTML = `
      <div class="sd-section-head">
        <span>🎬 Gợi ý nhanh</span>
        <button class="sd-clear-btn" type="button" style="color:#86efac;" data-sd-all="1">Xem tất cả →</button>
      </div>
    ` + items.slice(0, 6).map((m, idx) => {
      const title = getDisplayTitle(m);
      const hasSub = Boolean(m.hasCloudSub || (m.slug && state.cloudSubSlugs.has(normalizeMovieSlug(m.slug))));
      const poster = m.poster || './assets/icon-192.png?v=2';
      return `
        <button class="sd-movie-item" type="button" data-sd-idx="${idx}">
          <img src="${escapeHtml(poster)}" alt="" onerror="this.src='./assets/icon-192.png?v=2'" />
          <div style="min-width:0;flex:1;">
            <div class="sd-movie-title">${escapeHtml(title)}</div>
            <div class="sd-movie-meta">
              <span>${escapeHtml(m.year || '')}</span>
              <span>${escapeHtml(m.episode_current || m.quality || 'HD')}</span>
              ${hasSub ? '<span class="sd-sub-pill">☁️ SUB AI</span>' : ''}
            </div>
          </div>
        </button>
      `;
    }).join('');
    dd.classList.add('open');
    dd.querySelector('[data-sd-all]')?.addEventListener('click', (e) => {
      e.stopPropagation();
      closeAllSearchDropdowns();
      $('mobileSearchBar')?.classList.remove('open');
      executeSearch(query);
    });
    dd.querySelectorAll('[data-sd-idx]').forEach((btn) => {
      btn.onclick = (e) => {
        e.stopPropagation();
        const picked = items[Number(btn.dataset.sdIdx)];
        if (picked) {
          closeAllSearchDropdowns();
          $('mobileSearchBar')?.classList.remove('open');
          saveSearchHistory(query);
          openMovieDetail(picked, false);
        }
      };
    });
  };

  input.addEventListener('focus', () => {
    const q = input.value.trim();
    if (q.length < 2) renderHistoryInDropdown();
  });

  const searchLocalRamCatalog = (query) => {
    const needle = query.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    const pool = [
      ...loadHistory(),
      ...loadMyList(),
      ...(state.homeRows || []).flatMap((r) => r.movies || []),
      ...(state.movies || []),
    ];
    const seen = new Set();
    const matches = [];
    for (const m of pool) {
      if (!m || !m.slug) continue;
      const k = `${m.source || 'phimapi'}::${m.slug}`;
      if (seen.has(k)) continue;
      const hay = [m.name, m.nameVi, m.nameEn, m.origin_name, m.slug].filter(Boolean).join(' ')
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
      if (hay.includes(needle)) {
        seen.add(k);
        matches.push(m);
        if (matches.length >= 6) break;
      }
    }
    return matches;
  };

  input.addEventListener('input', () => {
    const q = input.value.trim();
    clearTimeout(debounceTimer);
    if (q.length < 2) {
      renderHistoryInDropdown();
      return;
    }
    // 0ms Instant Local RAM matches while fast server search runs in background!
    const localHits = searchLocalRamCatalog(q);
    if (localHits.length) {
      renderResultsInDropdown(localHits, q);
    }
    const seq = ++fetchSeq;
    debounceTimer = setTimeout(async () => {
      try {
        const data = await fetchCatalogBrowse({ q, limit: 6, fast: 1 });
        if (seq !== fetchSeq) return;
        const remoteItems = Array.isArray(data?.items) ? data.items : [];
        const seen = new Set();
        const merged = [];
        for (const it of [...remoteItems, ...localHits]) {
          const k = `${it.source || 'phimapi'}::${it.slug}`;
          if (!seen.has(k)) {
            seen.add(k);
            merged.push(it);
          }
        }
        renderResultsInDropdown(merged.slice(0, 6), q);
      } catch {
        // ignore network hiccup on instant suggest
      }
    }, 150);
  });

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeAllSearchDropdowns();
  });
}

/* ==================== BOOTSTRAP & EVENT WIRING ==================== */
function init() {
  applyUiTranslations();
  renderCategories();
  renderMyListSection();
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
    const hasAdv = Boolean($('advGenreSelect')?.value || $('advYearSelect')?.value || Number($('advRatingSelect')?.value || 0) > 0);
    if (hasAdv) {
      executeAdvancedFilter();
    } else if (!slug) {
      loadCategory('hot');
    } else {
      $('searchInputDesktop').value = '';
      $('searchInputMobile').value = '';
      loadCategory(slug);
    }
  });
  $('advFilterToggleBtn')?.addEventListener('click', () => {
    const panel = $('advFilterPanel');
    if (!panel) return;
    const willOpen = panel.style.display === 'none';
    panel.style.display = willOpen ? 'flex' : 'none';
    $('advFilterToggleBtn').classList.toggle('active', willOpen);
  });
  $('advFilterApplyBtn')?.addEventListener('click', () => executeAdvancedFilter());
  $('advFilterResetBtn')?.addEventListener('click', () => {
    if ($('countryPickerSelect')) $('countryPickerSelect').value = '';
    if ($('advGenreSelect')) $('advGenreSelect').value = '';
    if ($('advYearSelect')) $('advYearSelect').value = '';
    if ($('advRatingSelect')) $('advRatingSelect').value = '0';
    if ($('advFilterPanel')) $('advFilterPanel').style.display = 'none';
    $('advFilterToggleBtn')?.classList.remove('active');
    loadCategory('hot');
  });
  $('clearAllHistoryBtn')?.addEventListener('click', clearAllHistory);
  setupInstantSearchDropdown('searchInputDesktop', 'searchDropdownDesktop');
  setupInstantSearchDropdown('searchInputMobile', 'searchDropdownMobile');
  document.addEventListener('click', (e) => {
    if (!e.target.closest?.('#searchDesktopWrap') && !e.target.closest?.('#mobileSearchBar')) {
      closeAllSearchDropdowns();
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
  $('openMyListBtn')?.addEventListener('click', () => openMyListModal());
  $('clearAllMyListBtn')?.addEventListener('click', () => {
    if (confirm('Xóa toàn bộ phim trong Danh sách yêu thích?')) {
      clearAllMyList();
    }
  });
  $('myListSearchInput')?.addEventListener('input', () => renderMyListSection());
  $('openPartyBtn')?.addEventListener('click', () => openPartyHubModal('lobby'));
  $('hudPartyBtn')?.addEventListener('click', () => {
    if (state.party.roomCode) {
      state.party.chatVisible = !state.party.chatVisible;
      renderPartyHeaderAndButton();
    } else {
      createWatchPartyForCurrent();
    }
  });
  document.querySelectorAll('#partyHubTabs [data-hub-tab]').forEach((btn) => {
    btn.addEventListener('click', () => switchPartyHubTab(btn.dataset.hubTab));
  });
  document.querySelectorAll('#partySideTabs [data-ptab]').forEach((btn) => {
    btn.addEventListener('click', () => setPartySideTab(btn.dataset.ptab));
  });
  $('partyRefreshLobbyBtn')?.addEventListener('click', loadPartyLobby);
  $('partyCreateSearchBtn')?.addEventListener('click', searchMoviesForCreateParty);
  $('partyCreateSearchInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') searchMoviesForCreateParty();
  });
  $('partyCreateSubmitBtn')?.addEventListener('click', createWatchPartyFromHub);
  $('partyJoinSubmitBtn')?.addEventListener('click', () => {
    joinWatchPartyByCode($('partyCodeInput')?.value, $('partyNickInput')?.value, {
      passcode: $('partyJoinPassInput')?.value || '',
    });
  });
  $('partyCodeInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      joinWatchPartyByCode($('partyCodeInput')?.value, $('partyNickInput')?.value, {
        passcode: $('partyJoinPassInput')?.value || '',
      });
    }
  });
  $('partyJoinPassInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      joinWatchPartyByCode($('partyCodeInput')?.value, $('partyNickInput')?.value, {
        passcode: $('partyJoinPassInput')?.value || '',
      });
    }
  });
  $('partyCopyLinkBtn')?.addEventListener('click', sharePartyInvite);
  $('partyShareInviteBtn')?.addEventListener('click', sharePartyInvite);
  $('partySplitToggleBtn')?.addEventListener('click', () => {
    state.party.splitMode = !state.party.splitMode;
    applyPartySplitLayout();
    showToast(state.party.splitMode ? '🖥️ Chế độ Rạp chia đôi' : '🪟 Chế độ Khung nổi');
  });
  $('partyResyncBtn')?.addEventListener('click', async () => {
    const p = state.party;
    if (!p.roomCode) return;
    setPartySyncIndicator('🔄 Đang khớp...', 'adjusting');
    const t0 = performance.now();
    try {
      const data = await partyPoll({
        roomCode: p.roomCode,
        sinceMsgId: p.lastMsgId,
        sinceReactionTs: p.lastReactionTs,
        memberId: p.memberId,
        nickname: p.nickname,
        hostToken: p.hostToken,
      });
      if (data?.party) applySyncedPlaybackState(data.party, performance.now() - t0, { forceHardSync: true });
      showToast('⚡ Đã đồng bộ chính xác với phòng!');
    } catch { /* ignore */ }
  });
  $('partyCountdownBtn')?.addEventListener('click', () => {
    if (!canControlParty()) return showToast('Chỉ Chủ phòng mới có quyền đếm ngược');
    const video = $('videoEl');
    pushHostPartyState({
      countdown: true,
      isPlaying: true,
      positionSec: Number(video?.currentTime || 0),
    });
  });
  $('partyToggleControlModeBtn')?.addEventListener('click', async () => {
    const p = state.party;
    if (!p.isHost || !p.hostToken) return;
    const next = p.controlMode === 'EVERYONE' ? 'HOST_ONLY' : 'EVERYONE';
    try {
      await partyUpdateSettings({ roomCode: p.roomCode, hostToken: p.hostToken, controlMode: next });
      p.controlMode = next;
      renderPartyRoomTab();
      showToast(next === 'EVERYONE' ? '🤝 Mọi người trong phòng đều điều khiển được!' : '👑 Chỉ Chủ phòng điều khiển');
    } catch (err) {
      showToast(err?.message || 'Lỗi cập nhật quyền');
    }
  });
  $('partyToggleVisibilityBtn')?.addEventListener('click', async () => {
    const p = state.party;
    if (!p.isHost || !p.hostToken) return;
    const next = p.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
    try {
      await partyUpdateSettings({ roomCode: p.roomCode, hostToken: p.hostToken, visibility: next });
      p.visibility = next;
      renderPartyRoomTab();
      showToast(next === 'PUBLIC' ? '🌐 Phòng đã hiện trên Sảnh Công Khai!' : '🔒 Phòng đã chuyển sang Riêng tư');
    } catch (err) {
      showToast(err?.message || 'Lỗi cập nhật chế độ phòng');
    }
  });
  $('partyClaimHostBtn')?.addEventListener('click', async () => {
    const p = state.party;
    if (!p.roomCode) return;
    try {
      const res = await partyClaimHost({ roomCode: p.roomCode, memberId: p.memberId, nickname: p.nickname });
      if (res?.hostToken) {
        p.isHost = true;
        p.hostToken = res.hostToken;
        p.hostInactive = false;
        p.hostName = p.nickname;
        savePartySessionStorage();
        renderPartyRoomTab();
        showToast('👑 Bạn đã tiếp quản quyền Chủ phòng!');
      }
    } catch (err) {
      showToast(err?.message || 'Không thể tiếp quản lúc này');
    }
  });
  $('partyQueueSearchBtn')?.addEventListener('click', searchMoviesForPartyQueue);
  $('partyQueueSearchInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') searchMoviesForPartyQueue();
  });
  document.querySelectorAll('#partyReactBar [data-react]').forEach((btn) => {
    btn.addEventListener('click', () => sendPartyReaction(btn.dataset.react));
  });
  $('partyHideChatBtn')?.addEventListener('click', () => {
    state.party.chatVisible = false;
    renderPartyHeaderAndButton();
  });
  $('partySendBtn')?.addEventListener('click', sendPartyChatMsg);
  $('partyChatInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') sendPartyChatMsg();
  });
  $('partyLeaveBtn')?.addEventListener('click', () => leaveWatchParty(false));
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
    const confirmPassword = $('cloudConfirmPasswordInput')?.value || '';
    const displayName = $('cloudDisplayNameInput')?.value.trim() || '';
    const inviteCode = $('cloudInviteInput')?.value.trim() || '';
    if (!username || !password) return showToast('Vui lòng nhập tên đăng nhập và mật khẩu');
    if (state.cloudAuthMode === 'register') {
      if (password.length < 6) return showToast('Mật khẩu phải có ít nhất 6 ký tự');
      if (password !== confirmPassword) return showToast('Mật khẩu xác nhận không khớp!');
      if (displayName && displayName === password) {
        return showToast('⚠️ Tên hiển thị không được trùng với mật khẩu! Vui lòng đặt biệt danh khác.');
      }
    }
    const btn = $('cloudAuthSubmitBtn');
    const prevTxt = btn.textContent;
    btn.disabled = true;
    btn.textContent = '⏳ Đang kết nối...';
    try {
      if (state.cloudAuthMode === 'register') {
        await cloudRegister({ username, password, displayName, inviteCode });
        showToast('👑 Đã tạo tài khoản NKI Cloud & đồng bộ!');
      } else {
        await cloudLogin(username, password);
        showToast('☁️ Đã đăng nhập NKI Cloud! Đang tự động đồng bộ...');
      }
      if ($('cloudPasswordInput')) $('cloudPasswordInput').value = '';
      if ($('cloudConfirmPasswordInput')) $('cloudConfirmPasswordInput').value = '';
      await pullCloudStateAndMerge({ silent: true, force: true, migrateLocalMyList: state.cloudAuthMode === 'register' });
      renderCloudCard();
      loadLiveGeminiModels({ silent: true });
    } catch (err) {
      showToast(err.message || 'Đăng nhập thất bại');
    } finally {
      btn.disabled = false;
      btn.textContent = prevTxt;
    }
  });
  $('profileSaveNameBtn')?.addEventListener('click', async () => {
    const newName = $('profileDisplayNameInput')?.value.trim() || '';
    if (newName.length < 2) return showToast('Tên hiển thị phải có ít nhất 2 ký tự!');
    const btn = $('profileSaveNameBtn');
    const oldTxt = btn.textContent;
    btn.disabled = true;
    btn.textContent = '⏳...';
    try {
      await cloudUpdateProfile({ displayName: newName });
      renderCloudCard();
      showToast(`✅ Đã đổi tên hiển thị thành "${newName}"!`);
    } catch (err) {
      showToast(err?.message || 'Không thể đổi tên hiển thị');
    } finally {
      btn.disabled = false;
      btn.textContent = oldTxt;
    }
  });
  $('profileSavePassBtn')?.addEventListener('click', async () => {
    const currentPassword = $('profileCurPassInput')?.value || '';
    const newPassword = $('profileNewPassInput')?.value || '';
    const confirmPassword = $('profileConfirmPassInput')?.value || '';
    if (!currentPassword) return showToast('Vui lòng nhập mật khẩu hiện tại');
    if (newPassword.length < 6) return showToast('Mật khẩu mới phải có ít nhất 6 ký tự');
    if (newPassword !== confirmPassword) return showToast('Mật khẩu xác nhận không khớp!');
    const btn = $('profileSavePassBtn');
    const oldTxt = btn.textContent;
    btn.disabled = true;
    btn.textContent = '⏳ Đang đổi...';
    try {
      await cloudUpdateProfile({ currentPassword, newPassword });
      if ($('profileCurPassInput')) $('profileCurPassInput').value = '';
      if ($('profileNewPassInput')) $('profileNewPassInput').value = '';
      if ($('profileConfirmPassInput')) $('profileConfirmPassInput').value = '';
      renderCloudCard();
      showToast('🔑 Đã đổi mật khẩu thành công & cập nhật phiên đăng nhập mới!');
    } catch (err) {
      showToast(err?.message || 'Không thể đổi mật khẩu');
    } finally {
      btn.disabled = false;
      btn.textContent = oldTxt;
    }
  });
  $('profileRevokeOthersBtn')?.addEventListener('click', async () => {
    const btn = $('profileRevokeOthersBtn');
    const oldTxt = btn.textContent;
    btn.disabled = true;
    btn.textContent = '⏳...';
    try {
      await cloudUpdateProfile({ revokeOtherSessions: true });
      renderCloudCard();
      showToast('🛡️ Đã đăng xuất tất cả các thiết bị khác (giữ lại thiết bị này)!');
    } catch (err) {
      showToast(err?.message || 'Thao tác thất bại');
    } finally {
      btn.disabled = false;
      btn.textContent = oldTxt;
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
    if (state.audioCtx && state.audioCtx.state === 'suspended') state.audioCtx.resume().catch(() => {});
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

  const togglePlayPause = () => {
    if (state.audioCtx && state.audioCtx.state === 'suspended') state.audioCtx.resume().catch(() => {});
    if (video.paused) video.play().catch(() => {});
    else video.pause();
    setControlsVisible(true);
  };
  const seekBack10 = () => {
    video.currentTime = Math.max(0, video.currentTime - 10);
    showGestureToast('⏪ -10s');
    setControlsVisible(true);
  };
  const seekFwd10 = () => {
    video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
    showGestureToast('+10s ⏩');
    setControlsVisible(true);
  };

  $('playPauseBtn').onclick = togglePlayPause;
  if ($('hudPlayBtn')) $('hudPlayBtn').onclick = togglePlayPause;
  $('rewBtn').onclick = seekBack10;
  if ($('hudRewBtn')) $('hudRewBtn').onclick = seekBack10;
  $('fwdBtn').onclick = seekFwd10;
  if ($('hudFwdBtn')) $('hudFwdBtn').onclick = seekFwd10;

  if ($('hudMuteBtn')) {
    $('hudMuteBtn').onclick = () => {
      toggleMute();
      setControlsVisible(true);
    };
  }
  if ($('hudVolSlider')) {
    $('hudVolSlider').addEventListener('input', (e) => {
      applyVolumeBoost(Number(e.target.value) / 100);
      setControlsVisible(true);
    });
  }

  $('hudLockBtn').onclick = () => {
    state.isLocked = true;
    state.hudPointerInside = false;
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
    if ($('hudFsBtn')) {
      $('hudFsBtn').innerHTML = active ? SVG_ICONS.fsExit : SVG_ICONS.fsEnter;
      $('hudFsBtn').title = active ? tr('exitFsBtn') : tr('fullscreenBtn');
    }
    $('playerModal').classList.toggle('is-fullscreen', active);
  };
  document.addEventListener('fullscreenchange', onFsChange);
  document.addEventListener('webkitfullscreenchange', onFsChange);

  // Seekbar, timeupdate (auto-save every 3s), Skip Intro, Auto-Next, Failover & Hover Tooltip
  video.addEventListener('play', () => {
    syncPlayIcons(false);
    clearStallWatchdog();
    setControlsVisible(true, 1600);
    pushHostPartyState({ isPlaying: true, actionNote: `▶ ${state.party.nickname} tiếp tục phát` });
    if (state.prefs.ambientGlow) startAmbientGlowLoop();
  });
  video.addEventListener('playing', () => {
    clearStallWatchdog();
    if ($('streamFailoverBar')) $('streamFailoverBar').style.display = 'none';
  });
  video.addEventListener('waiting', () => armStallWatchdog());
  video.addEventListener('stalled', () => armStallWatchdog());
  video.addEventListener('error', () => {
    if ($('playerModal')?.classList.contains('open') && video.getAttribute('src')) {
      triggerAutoFailover('Video stream failed');
    }
  });
  video.addEventListener('ended', () => {
    clearStallWatchdog();
    stopTtsSpeech();
    if (state.currentMovie && state.currentEpisode) {
      markEpisodeWatched(state.currentMovie.slug, state.currentEpisode);
    }
    // If inside a Watch Party with queued Up Next movies, automatically advance the room to the top-voted queued movie!
    if (state.party.roomCode && canControlParty() && state.party.queue?.length > 0) {
      playPartyQueueItem(state.party.queue[0].id);
      return;
    }
    const nextEp = getNextEpisode();
    if (nextEp && !state.autoNextCancelled) {
      startAutoNextCountdown(nextEp, 5);
    }
  });
  video.addEventListener('seeked', () => {
    const pos = video.currentTime || 0;
    stopTtsSpeech();
    pushHostPartyState({ positionSec: pos, actionNote: `⏩ ${state.party.nickname} tua tới ${formatTime(pos)}` });
    maybeRefillHlsLookahead(pos, { forceJump: true });
    if (state.audioSubActive && state.audioSubRecorder?.state === 'recording') {
      // Restart rolling 8s live audio fallback cleanly at the new seeked timestamp
      try { state.audioSubRecorder.stop(); } catch { /* ignore */ }
    }
  });
  video.addEventListener('pause', () => {
    syncPlayIcons(true);
    clearStallWatchdog();
    stopTtsSpeech();
    persistCurrentPlaybackProgress({ immediate: true });
    setControlsVisible(true);
    pushHostPartyState({ isPlaying: false, actionNote: `⏸️ ${state.party.nickname} tạm dừng ở ${formatTime(video.currentTime || 0)}` });
  });
  video.addEventListener('timeupdate', () => {
    const cur = video.currentTime || 0;
    const dur = video.duration || 0;
    $('curTimeLabel').textContent = formatTime(cur);
    $('durTimeLabel').textContent = formatTime(dur);
    if (dur > 0) {
      const ratio = Math.max(0, Math.min(1, cur / dur));
      const seekEl = $('seekRange');
      seekEl.value = String(ratio * 1000);
      seekEl.style.setProperty('--pct', `${(ratio * 100).toFixed(2)}%`);
    }
    renderActiveSubtitle(cur * 1000);
    updateSkipIntroUi(cur, dur);
    checkAutoNext(cur, dur);
    if (Date.now() - state.lastLookaheadCheckTs >= 3000) {
      state.lastLookaheadCheckTs = Date.now();
      maybeRefillHlsLookahead(cur, { forceJump: false });
      maybePreTranslateNextEpisode(cur, dur);
      if ($('moreModal')?.classList.contains('open')) updateStreamStatsBadge();
    }
    if (!video.paused && cur > 2 && Date.now() - state.lastSavedPosTs >= 3000) {
      persistCurrentPlaybackProgress({ immediate: false });
    }
  });

  // Hover Seekbar Time + #5 Video Thumbnail Scrubbing Tooltip
  const seekWrap = $('seekRowWrap');
  const seekTip = $('seekTooltip');
  const seekTipTime = $('seekTooltipTime');
  if (seekWrap && seekTip) {
    const updateTip = (clientX) => {
      const dur = video.duration || 0;
      if (!dur || !Number.isFinite(dur)) {
        seekTip.classList.remove('visible');
        return;
      }
      const rect = seekWrap.getBoundingClientRect();
      const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
      const hoverSec = ratio * dur;
      if (seekTipTime) seekTipTime.textContent = formatTime(hoverSec);
      else seekTip.textContent = formatTime(hoverSec);
      seekTip.style.left = `${Math.max(80, Math.min(rect.width - 80, clientX - rect.left))}px`;
      seekTip.classList.add('visible');
      renderScrubThumbAt(hoverSec);
    };
    seekWrap.addEventListener('mousemove', (e) => updateTip(e.clientX));
    seekWrap.addEventListener('mouseleave', () => seekTip.classList.remove('visible'));
  }

  // Phase 1 Player Pro Actions (Episodes Drawer, Skip Intro, Auto-Next, Failover, AI Audio Sub)
  if ($('hudEpisodesBtn')) {
    $('hudEpisodesBtn').onclick = () => {
      if ($('playerDrawer')?.classList.contains('open')) closePlayerDrawer();
      else openPlayerDrawer();
    };
  }
  if ($('closePlayerDrawerBtn')) $('closePlayerDrawerBtn').onclick = closePlayerDrawer;
  if ($('playerDrawerBackdrop')) $('playerDrawerBackdrop').onclick = closePlayerDrawer;
  if ($('drawerEpSearchInput')) {
    $('drawerEpSearchInput').oninput = (e) => {
      state.drawerEpQuery = e.target.value || '';
      renderPlayerDrawer();
    };
  }
  if ($('drawerEpSortBtn')) {
    $('drawerEpSortBtn').onclick = () => {
      state.drawerEpDesc = !state.drawerEpDesc;
      renderPlayerDrawer();
    };
  }

  if ($('skipIntroBtn')) {
    $('skipIntroBtn').onclick = (e) => {
      e.stopPropagation();
      const skipSec = getSkipIntroSec();
      const dur = video.duration || 0;
      const target = dur > 10 ? Math.min(dur - 5, (video.currentTime || 0) + skipSec) : (video.currentTime || 0) + skipSec;
      video.currentTime = target;
      state.skipIntroDismissed = true;
      $('skipIntroBtn').style.display = 'none';
      showGestureToast(`⏭ +${skipSec}s`);
    };
  }

  if ($('autoNextNowBtn')) $('autoNextNowBtn').onclick = () => playNextEpisodeNow();
  if ($('autoNextCancelBtn')) $('autoNextCancelBtn').onclick = () => cancelAutoNext(true);

  if ($('failoverRetryBtn')) {
    $('failoverRetryBtn').onclick = () => {
      if (state.currentMovie && state.currentEpisode) {
        openPlayer(state.currentMovie, state.currentEpisode, Math.floor(video.currentTime || 0));
      }
    };
  }
  if ($('failoverSearchBtn')) {
    $('failoverSearchBtn').onclick = () => {
      const q = state.currentMovie?.name || state.currentMovie?.origin_name || '';
      closePlayer();
      if (q) {
        if ($('searchInputDesktop')) $('searchInputDesktop').value = q;
        executeSearch(q);
        showToast(`🔍 Searching backup global sources for "${q}"...`);
      }
    };
  }

  if ($('aiAudioSubBtn')) $('aiAudioSubBtn').onclick = () => startWebAiAudioSub('lookahead10');
  if ($('aiAudioSubFullBtn')) $('aiAudioSubFullBtn').onclick = () => startWebAiAudioSub('full');
  if ($('hudAudioSubPrompt')) {
    $('hudAudioSubPrompt').onclick = (e) => {
      e.stopPropagation();
      startWebAiAudioSub('lookahead10');
    };
  }

  document.querySelectorAll('[data-skipintro]').forEach((b) => {
    b.onclick = () => applySkipIntroPref(Number(b.dataset.skipintro));
  });
  if ($('applyCustomSkipIntroBtn')) {
    $('applyCustomSkipIntroBtn').onclick = () => {
      applySkipIntroPref($('customSkipIntroInput')?.value);
    };
  }
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
    const val = Number(e.target.value);
    e.target.style.setProperty('--pct', `${(val / 10).toFixed(2)}%`);
    const dur = video.duration || 0;
    if (dur > 0) video.currentTime = (val / 1000) * dur;
    setControlsVisible(true);
  });

  // Tap, Mouse Auto-Hide, Hold-to-2x Speed & Swipe Gestures on Player Stage
  const stage = $('playerStage');
  let lastTap = 0;
  let touchStart = null;
  let lastMouseX = -1;
  let lastMouseY = -1;
  let hold2xTimer = null;
  let hold2xEndedAt = 0;

  const startHold2xTimer = () => {
    clearTimeout(hold2xTimer);
    if (state.isMinimized || state.isLocked || video.paused) return;
    hold2xTimer = setTimeout(() => {
      if (state.isMinimized || state.isLocked || video.paused) return;
      state.hold2xActive = true;
      video.playbackRate = 2.0;
      if ($('hold2xPill')) $('hold2xPill').style.display = 'inline-flex';
    }, 420);
  };

  const endHold2x = () => {
    clearTimeout(hold2xTimer);
    hold2xTimer = null;
    if (state.hold2xActive) {
      state.hold2xActive = false;
      hold2xEndedAt = Date.now();
      video.playbackRate = state.speed || 1.0;
      if ($('hold2xPill')) $('hold2xPill').style.display = 'none';
    }
  };

  // Keep HUD steady while mouse is directly over interactive top/bottom bars, hide fast once mouse leaves!
  const bindHudHoverGuard = (el) => {
    if (!el) return;
    el.addEventListener('mouseenter', () => {
      state.hudPointerInside = true;
      clearTimeout(state.controlsTimer);
    });
    el.addEventListener('mouseleave', () => {
      state.hudPointerInside = false;
      if (!$('videoEl').paused && !state.isLocked && state.controlsVisible) {
        setControlsVisible(true, 1100);
      }
    });
  };
  bindHudHoverGuard($('hudBottomBar'));
  bindHudHoverGuard(document.querySelector('.hud-top'));

  stage.addEventListener('mousemove', (e) => {
    if (state.isMinimized || state.isLocked) return;
    if (Math.abs(e.clientX - lastMouseX) < 3 && Math.abs(e.clientY - lastMouseY) < 3) return;
    lastMouseX = e.clientX;
    lastMouseY = e.clientY;
    setControlsVisible(true, 1800);
  });

  stage.addEventListener('mouseleave', () => {
    state.hudPointerInside = false;
    if (!state.isMinimized && !state.isLocked && !video.paused) {
      clearTimeout(state.controlsTimer);
      state.controlsTimer = setTimeout(() => setControlsVisible(false), 260);
    }
  });
  stage.addEventListener('mousedown', (e) => {
    if (e.button !== 0 || state.isMinimized || state.isLocked) return;
    const hud = $('playerHud');
    if (e.target !== stage && e.target !== video && e.target !== hud) return;
    startHold2xTimer();
  });
  window.addEventListener('mouseup', endHold2x);

  stage.addEventListener('click', (e) => {
    if (Date.now() - hold2xEndedAt < 260) return;
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
    if (e.target.closest?.('button, input, select, .hud-bottom, .hud-top, .player-drawer, .party-chat-panel')) return;
    const t = e.touches[0];
    touchStart = { x: t.clientX, y: t.clientY, vol: state.volumeScale, bright: state.brightness, time: video.currentTime, mode: null };
    startHold2xTimer();
  }, { passive: true });

  stage.addEventListener('touchmove', (e) => {
    if (state.isMinimized || !touchStart || state.isLocked || e.touches.length !== 1) return;
    const t = e.touches[0];
    const dx = t.clientX - touchStart.x;
    const dy = t.clientY - touchStart.y;
    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
      if (!state.hold2xActive) {
        clearTimeout(hold2xTimer);
        hold2xTimer = null;
      } else {
        return; // keep 2x active smoothly while holding
      }
    }
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
    endHold2x();
    if (touchStart?.mode === 'seek' && typeof touchStart.targetSeek === 'number') {
      video.currentTime = touchStart.targetSeek;
    }
    touchStart = null;
  });
  stage.addEventListener('touchcancel', () => {
    endHold2x();
    touchStart = null;
  });

  // PC Pro Keyboard Shortcuts (Space, Arrows/J/L, 0-9, G/H sub sync, </> speed, S skip intro, N next ep, B mask, V dub, ? help, Esc minimize)
  const SPEED_STEPS = [0.5, 0.75, 1, 1.25, 1.5, 2];
  const applySpeedValue = (nextSpeed) => {
    state.speed = nextSpeed;
    video.playbackRate = state.speed;
    document.querySelectorAll('[data-speed]').forEach((x) => x.classList.toggle('active', Number(x.dataset.speed) === state.speed));
    if ($('hudSpeedBoostBtn')) {
      $('hudSpeedBoostBtn').textContent = state.volumeScale > 1.01 ? `🔊 ${Math.round(state.volumeScale * 100)}%` : `${state.speed}x`;
    }
    showGestureToast(`⏩ Tốc độ ${state.speed}x`);
  };

  document.addEventListener('keydown', (e) => {
    if (!$('playerModal').classList.contains('open') || state.isMinimized) return;
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) return;
    const k = e.key.toLowerCase();
    if (e.code === 'Space') { e.preventDefault(); video.paused ? video.play() : video.pause(); }
    else if (e.key === 'ArrowLeft' || k === 'j') { video.currentTime = Math.max(0, video.currentTime - 10); showGestureToast('⏪ -10s'); }
    else if (e.key === 'ArrowRight' || k === 'l') { video.currentTime = Math.min(video.duration || 0, video.currentTime + 10); showGestureToast('+10s ⏩'); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); applyVolumeBoost(state.volumeScale + 0.1); showGestureToast(`🔊 ${Math.round(state.volumeScale * 100)}%`); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); applyVolumeBoost(state.volumeScale - 0.1); showGestureToast(`🔊 ${Math.round(state.volumeScale * 100)}%`); }
    else if (/^[0-9]$/.test(e.key) && video.duration > 10) {
      const pct = Number(e.key) * 10;
      video.currentTime = (pct / 100) * video.duration;
      showGestureToast(`🎯 ${pct}% • ${formatTime(video.currentTime)}`);
    }
    else if (k === 'g') {
      setMovieSyncOffsetMs(state.currentMovie?.slug, (state.prefs.syncOffsetMs || 0) - 250);
      showGestureToast(`⏱️ Sub: ${(state.prefs.syncOffsetMs / 1000).toFixed(2)}s`);
    }
    else if (k === 'h') {
      setMovieSyncOffsetMs(state.currentMovie?.slug, (state.prefs.syncOffsetMs || 0) + 250);
      showGestureToast(`⏱️ Sub: ${(state.prefs.syncOffsetMs >= 0 ? '+' : '')}${(state.prefs.syncOffsetMs / 1000).toFixed(2)}s`);
    }
    else if (e.key === '<' || (e.shiftKey && e.key === ',')) {
      const idx = SPEED_STEPS.indexOf(state.speed);
      applySpeedValue(SPEED_STEPS[Math.max(0, (idx < 0 ? 2 : idx) - 1)]);
    }
    else if (e.key === '>' || (e.shiftKey && e.key === '.')) {
      const idx = SPEED_STEPS.indexOf(state.speed);
      applySpeedValue(SPEED_STEPS[Math.min(SPEED_STEPS.length - 1, (idx < 0 ? 2 : idx) + 1)]);
    }
    else if (k === 's') {
      const skipSec = getSkipIntroSec();
      const dur = video.duration || 0;
      video.currentTime = dur > 10 ? Math.min(dur - 5, (video.currentTime || 0) + skipSec) : (video.currentTime || 0) + skipSec;
      state.skipIntroDismissed = true;
      if ($('skipIntroBtn')) $('skipIntroBtn').style.display = 'none';
      showGestureToast(`⏭ +${skipSec}s`);
    }
    else if (k === 'n') {
      const nxt = getNextEpisode();
      if (nxt) playNextEpisodeNow();
      else showGestureToast('Đây là tập cuối cùng');
    }
    else if (k === 'b') { $('hudMaskBtn')?.click(); }
    else if (k === 'v') { $('hudTtsBtn')?.click(); }
    else if (e.key === '?' || (e.shiftKey && e.key === '/')) {
      $('shortcutsModal')?.classList.toggle('open');
    }
    else if (k === 'f') { $('hudFsBtn').click(); }
    else if (k === 'm') { toggleMute(); }
    else if (k === 'e') {
      if ($('playerDrawer')?.classList.contains('open')) closePlayerDrawer();
      else openPlayerDrawer();
    }
    else if (k === 'c') { openSubModal(); }
    else if (e.key === 'Escape') {
      if ($('shortcutsModal')?.classList.contains('open')) $('shortcutsModal').classList.remove('open');
      else if ($('playerDrawer')?.classList.contains('open')) closePlayerDrawer();
      else minimizePlayer();
    }
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
  $('syncMinusBtn').onclick = () => setMovieSyncOffsetMs(state.currentMovie?.slug, (state.prefs.syncOffsetMs || 0) - 500);
  $('syncMinusFineBtn')?.addEventListener('click', () => setMovieSyncOffsetMs(state.currentMovie?.slug, (state.prefs.syncOffsetMs || 0) - 250));
  $('syncPlusFineBtn')?.addEventListener('click', () => setMovieSyncOffsetMs(state.currentMovie?.slug, (state.prefs.syncOffsetMs || 0) + 250));
  $('syncPlusBtn').onclick = () => setMovieSyncOffsetMs(state.currentMovie?.slug, (state.prefs.syncOffsetMs || 0) + 500);
  $('syncResetBtn').onclick = () => setMovieSyncOffsetMs(state.currentMovie?.slug, 0);
  $('syncRangeSlider')?.addEventListener('input', (e) => setMovieSyncOffsetMs(state.currentMovie?.slug, Number(e.target.value)));

  // Night Audio Mode, Mobile Quick Strip & Shortcuts Modal
  $('nightAudioToggle')?.addEventListener('change', (e) => {
    applyNightAudioMode(Boolean(e.target.checked));
    savePrefs();
    showGestureToast(state.prefs.nightAudio ? '🌙 Bật Chế Độ Xem Đêm' : 'Tắt Chế Độ Xem Đêm');
  });
  $('moreQuickDubBtn')?.addEventListener('click', () => {
    $('hudTtsBtn')?.click();
    $('moreQuickDubBtn').classList.toggle('active', Boolean(state.prefs.ttsDubbing));
  });
  $('moreQuickMaskBtn')?.addEventListener('click', () => {
    $('hudMaskBtn')?.click();
    $('moreQuickMaskBtn').classList.toggle('active', Boolean(state.prefs.hardsubMask));
  });
  $('moreQuickPartyBtn')?.addEventListener('click', () => {
    $('moreModal')?.classList.remove('open');
    $('hudPartyBtn')?.click();
  });
  $('openShortcutsBtn')?.addEventListener('click', () => {
    $('moreModal')?.classList.remove('open');
    $('shortcutsModal')?.classList.add('open');
  });

  // Chill-Player More Menu (Speed, Boost 200%, EQ, Aspect, Sleep, PiP)
  document.querySelectorAll('[data-speed]').forEach((b) => {
    b.onclick = () => {
      state.speed = Number(b.dataset.speed);
      video.playbackRate = state.speed;
      document.querySelectorAll('[data-speed]').forEach((x) => x.classList.toggle('active', x === b));
      $('hudSpeedBoostBtn').textContent = state.volumeScale > 1.01 ? `🔊 ${Math.round(state.volumeScale * 100)}%` : `${state.speed}x`;
      showGestureToast(`Tốc độ ${state.speed}x`);
    };
  });
  $('boostSlider').oninput = (e) => {
    applyVolumeBoost(Number(e.target.value) / 100);
  };
  document.querySelectorAll('[data-eq]').forEach((b) => {
    b.onclick = () => { applyEqPreset(b.dataset.eq); };
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
    const prevPrimaryLang = resolveLanguage(state.prefs.primaryLang).code;
    if ($('uiLangSelect')) {
      state.prefs.uiLang = resolveUiLang($('uiLangSelect').value);
    }
    state.prefs.geminiApiKey = $('geminiKeyInput').value.trim();
    state.prefs.geminiModel = $('geminiModelSelect').value;
    state.prefs.primaryLang = resolveLanguage($('defaultLangSelect').value).code;
    const nextPrimaryLang = state.prefs.primaryLang;
    savePrefs(false, true);
    applyUiTranslations();
    renderCategories();
    if (state.heroMovies.length) renderHero(state.heroMovies[state.heroIndex] || state.heroMovies[0]);
    else if (state.movies.length) renderHero(state.movies[0]);
    updateCloudHeaderBadge();

    // Immediately sync Detail Page & active Player subtitle language when user updates Settings!
    const lObj = resolveLanguage(nextPrimaryLang);
    if ($('detailSubLangSelect')) $('detailSubLangSelect').value = nextPrimaryLang;
    if ($('detailHeroSubBadge')) $('detailHeroSubBadge').textContent = `AI SUB (${lObj.badge})`;
    if ($('detailView')?.classList.contains('active') && state.currentMovie && $('detailSubCard')) {
      const epVal = $('detailSubEpSelect')?.value || '';
      const srvEps = state.currentMovie.servers?.[0]?.episodes || state.episodesFlat || [];
      const pickedEp = srvEps.find((x) => x.link_m3u8 === epVal) || srvEps[0];
      if (pickedEp) refreshDetailSubCardForSelection(state.currentMovie, pickedEp);
    }
    if ($('primaryLangChips')) renderLanguagePickers();
    if (
      $('playerModal')?.classList.contains('open')
      && state.currentMovie
      && state.currentEpisode
      && (prevPrimaryLang !== nextPrimaryLang || hasWrongScriptForLang(state.primaryCues, nextPrimaryLang))
    ) {
      autoLoadSubtitlesForMovie(state.currentMovie, state.currentEpisode, Math.floor($('videoEl')?.currentTime || 0));
    }

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

  // Secondary TV Cast Modal + #8 QR Pairing & Vietnamese Quick Search
  $('saveTvIpBtn').onclick = () => {
    state.prefs.tvIp = $('tvIpInput').value.trim();
    savePrefs();
    renderTvQrCode();
    showToast(state.prefs.tvIp ? `📡 Đã lưu TV IP: ${state.prefs.tvIp}` : 'Đã xóa IP TV');
  };
  if ($('tvQuickSearchBtn')) {
    $('tvQuickSearchBtn').onclick = () => {
      const q = ($('tvQuickSearchInput')?.value || '').trim();
      if (!q) return;
      $('tvModal')?.classList.remove('open');
      if ($('searchInputDesktop')) $('searchInputDesktop').value = q;
      executeSearch(q);
      showToast(`🔍 Tìm "${q}" — mở phim rồi bấm Cast TV để phát thẳng lên TV!`);
    };
  }
  $('syncKeyToTvBtn').onclick = syncGeminiKeyToTv;
  document.querySelectorAll('[data-tvkey]').forEach((b) => {
    b.onclick = () => sendTvRemoteKey(b.dataset.tvkey);
  });

  // V14 Super-Upgrade Pack Event Bindings (#1, #3, #4, #6, #7, #9, #10, #11)
  if ($('aiVibeBtnDesktop')) {
    $('aiVibeBtnDesktop').onclick = () => executeAiVibeSearch($('searchInputDesktop')?.value);
  }
  if ($('aiVibeBtnMobile')) {
    $('aiVibeBtnMobile').onclick = () => executeAiVibeSearch($('searchInputMobile')?.value);
  }
  if ($('notifBellBtn')) {
    $('notifBellBtn').onclick = (e) => {
      e.stopPropagation();
      refreshEpisodeReleaseNotifications();
      $('notifMenu')?.classList.toggle('open');
    };
  }
  if ($('notifMarkReadBtn')) {
    $('notifMarkReadBtn').onclick = (e) => {
      e.stopPropagation();
      markAllNotifsRead();
    };
  }
  document.addEventListener('click', (e) => {
    if (!e.target.closest?.('.notif-wrap')) $('notifMenu')?.classList.remove('open');
  });

  if ($('hudMaskBtn')) {
    $('hudMaskBtn').onclick = () => {
      state.prefs.hardsubMask = !state.prefs.hardsubMask;
      savePrefs();
      applyHardsubMaskUi();
      showGestureToast(state.prefs.hardsubMask ? '🫥 Bật Che Sub Cứng' : 'Tắt Che Sub Cứng');
    };
  }
  if ($('hardsubMaskToggle')) {
    $('hardsubMaskToggle').onchange = (e) => {
      state.prefs.hardsubMask = Boolean(e.target.checked);
      savePrefs();
      applyHardsubMaskUi();
    };
  }
  if ($('hardsubMaskMode')) {
    $('hardsubMaskMode').onchange = (e) => {
      state.prefs.hardsubMaskMode = e.target.value;
      savePrefs();
      applyHardsubMaskUi();
    };
  }
  if ($('hardsubHeightRange')) {
    $('hardsubHeightRange').oninput = (e) => {
      state.prefs.hardsubMaskHeight = Number(e.target.value);
      savePrefs();
      applyHardsubMaskUi();
    };
  }
  if ($('hardsubBottomRange')) {
    $('hardsubBottomRange').oninput = (e) => {
      state.prefs.hardsubMaskBottom = Number(e.target.value);
      savePrefs();
      applyHardsubMaskUi();
    };
  }

  if ($('hudTtsBtn')) {
    $('hudTtsBtn').onclick = () => {
      state.prefs.ttsDubbing = !state.prefs.ttsDubbing;
      savePrefs();
      syncTtsUi();
      if (!state.prefs.ttsDubbing) stopTtsSpeech();
      showGestureToast(state.prefs.ttsDubbing ? '🗣️ Bật AI Thuyết Minh' : '🔇 Tắt AI Thuyết Minh');
    };
  }
  if ($('ttsDubToggle')) {
    $('ttsDubToggle').onchange = (e) => {
      state.prefs.ttsDubbing = Boolean(e.target.checked);
      savePrefs();
      syncTtsUi();
      if (!state.prefs.ttsDubbing) stopTtsSpeech();
    };
  }
  if ($('ttsRateRange')) {
    $('ttsRateRange').oninput = (e) => {
      state.prefs.ttsRate = Number(e.target.value) / 100;
      savePrefs();
      syncTtsUi();
    };
  }

  if ($('downloadSubSrtBtn')) {
    $('downloadSubSrtBtn').onclick = () => {
      if (!state.primaryCues?.length) return showToast('Chưa có phụ đề nào đang tải để xuất .SRT!');
      const langCode = resolveLanguage(state.prefs.primaryLang).code;
      const safeFile = `${normalizeMovieSlug(state.currentMovie?.slug || 'chillfilm')}-${normalizeEpisodeKey(state.currentEpisode?.name || 'full')}-${langCode}.srt`;
      downloadSrtFile(state.primaryCues, safeFile);
      showToast(`⬇️ Đã tải file ${safeFile} (${state.primaryCues.length} câu)!`);
    };
  }

  if ($('saveMovieSkipIntroBtn')) {
    $('saveMovieSkipIntroBtn').onclick = () => {
      if (!state.currentMovie?.slug) return showToast('Hãy mở một bộ phim trước!');
      const sec = Math.max(10, Math.min(300, Math.round(Number($('customSkipIntroInput')?.value) || 85)));
      saveMovieIntroSec(state.currentMovie.slug, sec);
      syncSkipIntroSettingsUi();
      showToast(`💾 Đã nhớ Skip Intro +${sec}s riêng cho "${getDisplayTitle(state.currentMovie)}"!`);
    };
  }

  if ($('ambientGlowToggle')) {
    $('ambientGlowToggle').onchange = (e) => {
      state.prefs.ambientGlow = Boolean(e.target.checked);
      savePrefs();
      if (state.prefs.ambientGlow) startAmbientGlowLoop();
      else stopAmbientGlowLoop();
    };
  }

  if ($('closeTrailerModalBtn')) $('closeTrailerModalBtn').onclick = closeTrailerModal;
  if ($('trailerModal')) {
    $('trailerModal').addEventListener('click', (e) => {
      if (e.target === $('trailerModal')) closeTrailerModal();
    });
  }

  updateCloudHeaderBadge();
  refreshEpisodeReleaseNotifications();
  if (isCloudSignedIn()) {
    // Stagger background sync slightly after first paint so Home Feed & posters load at max speed!
    setTimeout(() => {
      cloudRefreshMe().then(() => updateCloudHeaderBadge()).catch(() => {});
      pullCloudStateAndMerge({ silent: true, force: true }).then(() => refreshEpisodeReleaseNotifications());
      if (!state.liveModels.length) {
        loadLiveGeminiModels({ silent: true });
      }
    }, 350);
  }

  // Auto-detect ?tvip=, ?party=, and ?movie=source:slug&sid=...&ep=...&play=1 deep-links on startup
  try {
    const params = new URLSearchParams(window.location.search);
    const pairIp = params.get('tvip');
    if (pairIp && pairIp.trim().length >= 7) {
      state.prefs.tvIp = pairIp.trim();
      savePrefs();
      showToast(`📲 Đã ghép đôi QR với Smart TV (${state.prefs.tvIp}) thành công!`);
    }
    const movieParam = (params.get('movie') || '').trim();
    const openDeepLinkMovie = () => {
      if (!movieParam) return;
      const colonIdx = movieParam.indexOf(':');
      const source = colonIdx > 0 ? movieParam.slice(0, colonIdx) : 'phimapi';
      const slug = colonIdx > 0 ? movieParam.slice(colonIdx + 1) : movieParam;
      const sourceId = (params.get('sid') || '').trim();
      const wantPlay = params.get('play') === '1';
      const epParam = (params.get('ep') || '').trim();
      if (slug) {
        openMovieDetail({ source, slug, sourceId, epName: epParam }, wantPlay);
      }
    };
    const inviteRoom = params.get('party');
    if (inviteRoom && inviteRoom.trim().length >= 4) {
      const code = inviteRoom.trim().toUpperCase();
      if (isCloudSignedIn()) {
        joinWatchPartyByCode(code, getDefaultPartyNick());
      } else {
        openPartyHubModal('join', { prefillCode: code });
      }
    } else {
      // Auto-rejoin active Watch Party smoothly after accidental F5 refresh (preserves Host token!)
      const savedParty = loadPartySessionStorage();
      if (savedParty?.roomCode) {
        joinWatchPartyByCode(savedParty.roomCode, savedParty.nickname || getDefaultPartyNick(), {
          hostToken: savedParty.hostToken || '',
          memberId: savedParty.memberId || '',
          silentRejoin: true,
        }).then((ok) => {
          if (!ok && movieParam) openDeepLinkMovie();
        });
      } else if (movieParam) {
        openDeepLinkMovie();
      }
    }
  } catch { /* ignore */ }

  // Browser Back / Forward History Navigation (Player -> Detail -> Home smoothly!)
  window.addEventListener('popstate', () => {
    state.isPopstateNav = true;
    try {
      const params = new URLSearchParams(window.location.search);
      const movieParam = (params.get('movie') || '').trim();
      const wantPlay = params.get('play') === '1';
      if (!wantPlay && $('playerModal')?.classList.contains('open') && !state.isMinimized) {
        minimizePlayer();
      }
      if (!movieParam) {
        if ($('detailView')?.classList.contains('active')) {
          $('detailView').classList.remove('active');
          $('homeView').style.display = 'block';
          if ($('heroBox').style.display !== 'none') $('topbar')?.classList.remove('solid');
        }
      } else {
        const colonIdx = movieParam.indexOf(':');
        const source = colonIdx > 0 ? movieParam.slice(0, colonIdx) : 'phimapi';
        const slug = colonIdx > 0 ? movieParam.slice(colonIdx + 1) : movieParam;
        const sourceId = (params.get('sid') || '').trim();
        if (!state.currentMovie || normalizeMovieSlug(state.currentMovie.slug) !== normalizeMovieSlug(slug) || !$('detailView')?.classList.contains('active')) {
          openMovieDetail({ source, slug, sourceId }, wantPlay);
        }
      }
    } finally {
      setTimeout(() => { state.isPopstateNav = false; }, 120);
    }
  });

  // PWA Service Worker Registration & Native Install Prompt (#8)
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  }
  window.addEventListener('beforeinstallprompt', (ev) => {
    ev.preventDefault();
    state.deferredPwaPrompt = ev;
    if ($('pwaInstallBtn')) $('pwaInstallBtn').style.display = 'inline-flex';
  });
  window.addEventListener('appinstalled', () => {
    state.deferredPwaPrompt = null;
    if ($('pwaInstallBtn')) $('pwaInstallBtn').style.display = 'none';
    showToast('✅ Đã cài ChillFilm vào thiết bị của bạn!');
  });
  $('pwaInstallBtn')?.addEventListener('click', async () => {
    if (!state.deferredPwaPrompt) return;
    state.deferredPwaPrompt.prompt();
    const res = await state.deferredPwaPrompt.userChoice.catch(() => null);
    if (res?.outcome === 'accepted') {
      $('pwaInstallBtn').style.display = 'none';
    }
    state.deferredPwaPrompt = null;
  });

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
