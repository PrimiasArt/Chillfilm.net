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
} from './subtitles.js';
import { UI_LANGUAGES, resolveUiLang, t } from './i18n.js';

const PHIM_API = 'https://phimapi.com';
const PHIM_CDN = 'https://phimimg.com';
const APK_RELEASE_URL = 'https://github.com/PrimiasArt/Chillfilm.net/releases/download/android-apk-latest/ChillFilm-latest.apk';

const CATEGORIES = [
  { id: 'hot', label: 'Trending', url: `${PHIM_API}/danh-sach/phim-moi-cap-nhat?page=1` },
  { id: 'phim-le', label: 'Movies', url: `${PHIM_API}/v1/api/danh-sach/phim-le?page=1&limit=24` },
  { id: 'phim-bo', label: 'TV Series', url: `${PHIM_API}/v1/api/danh-sach/phim-bo?page=1&limit=24` },
  { id: 'hoat-hinh', label: 'Animation', url: `${PHIM_API}/v1/api/danh-sach/hoat-hinh?page=1&limit=24` },
  { id: 'han-quoc', label: 'Korean', url: `${PHIM_API}/v1/api/quoc-gia/han-quoc?page=1&limit=24` },
  { id: 'au-my', label: 'US / UK', url: `${PHIM_API}/v1/api/quoc-gia/au-my?page=1&limit=24` },
  { id: 'trung-quoc', label: 'Chinese', url: `${PHIM_API}/v1/api/quoc-gia/trung-quoc?page=1&limit=24` },
  { id: 'thai-lan', label: 'Thai', url: `${PHIM_API}/v1/api/quoc-gia/thai-lan?page=1&limit=24` },
  { id: 'nhat-ban', label: 'Japanese', url: `${PHIM_API}/v1/api/quoc-gia/nhat-ban?page=1&limit=24` },
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
};

const $ = (id) => document.getElementById(id);
const tr = (key, vars) => t(state.prefs.uiLang, key, vars);

function getCategoryLabel(catId) {
  return tr(`cat_${catId}`);
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
  if (state.movies.length) renderHero(state.movies[0]);
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

function savePrefs() {
  localStorage.setItem('chillfilm_nki_prefs_v1', JSON.stringify(state.prefs));
}

function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem('chillfilm_nki_history_v1') || '[]');
  } catch {
    return [];
  }
}

function buildEpisodePosKey(movieSlug, ep) {
  const m = String(movieSlug || '').trim().toLowerCase();
  const epKey = String(ep?.slug || ep?.name || '').trim().toLowerCase();
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
  const entry = map[key];
  if (entry && typeof entry.pos === 'number' && entry.pos > 2) {
    if (entry.dur > 30 && entry.pos / entry.dur >= 0.97) return 0;
    return Math.floor(entry.pos);
  }
  // Fallback: check watch history by movie + episode name or URL
  const hist = loadHistory().find(
    (h) => h.slug === movieSlug && (h.epUrl === ep.link_m3u8 || h.epName === ep.name)
  );
  if (hist && hist.position > 2) {
    if (hist.duration > 30 && hist.position / hist.duration >= 0.97) return 0;
    return Math.floor(hist.position);
  }
  return 0;
}

function getLatestMovieHistory(movieSlug) {
  if (!movieSlug) return null;
  return loadHistory().find((h) => h.slug === movieSlug) || null;
}

function saveHistoryItem(movie, ep, currentTime, duration) {
  if (!movie || !ep) return;
  const pos = Math.floor(currentTime || 0);
  const dur = Math.floor(duration || 0);
  if (pos < 2) return;

  // 1. Update per-episode resume map (persists across ALL episodes of a series)
  const map = loadEpisodePosMap();
  const epKey = buildEpisodePosKey(movie.slug, ep);
  const isFinished = dur > 30 && pos / dur >= 0.97;
  if (isFinished) {
    delete map[epKey];
  } else {
    map[epKey] = { pos, dur, epName: ep.name, ts: Date.now() };
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

  // 2. Update "Tiếp Tục Xem" movie history strip
  if (dur < 10) return;
  const list = loadHistory().filter((h) => h.slug !== movie.slug);
  list.unshift({
    slug: movie.slug,
    name: movie.name,
    origin_name: movie.origin_name || '',
    poster: movie.poster,
    year: movie.year || '',
    epName: ep.name,
    epSlug: ep.slug || '',
    epUrl: ep.link_m3u8,
    position: isFinished ? 0 : pos,
    duration: dur,
    progress: Math.min(1, Math.max(0.02, pos / dur)),
    ts: Date.now(),
  });
  try {
    localStorage.setItem('chillfilm_nki_history_v1', JSON.stringify(list.slice(0, 24)));
  } catch {
    // Ignore storage quota
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
    quality: m.quality || 'HD',
    episode_current: m.episode_current || '',
    poster: normalizeImage(m.poster_url || m.thumb_url),
    backdrop: normalizeImage(m.thumb_url || m.poster_url),
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

async function loadCategory(catId) {
  state.activeCategory = catId;
  renderCategories();
  closeDetailView();

  const liftEl = document.querySelector('.content-lift');
  const swimWrap = $('homeSwimlanes');
  const gridSec = $('catalogGridSection');

  // HOME ("hot"): Authentic Netflix Multi-Row Swimlanes + Top 10 Ranked Row!
  if (catId === 'hot') {
    if (liftEl) liftEl.classList.remove('no-hero');
    $('topbar')?.classList.remove('solid');
    gridSec.style.display = 'none';
    swimWrap.style.display = 'block';
    swimWrap.innerHTML = `<div style="text-align:center;padding:42px;color:#b3b3b3;">${escapeHtml(tr('loadingCatalog'))}</div>`;

    const rowCatIds = ['hot', 'phim-le', 'phim-bo', 'han-quoc', 'au-my', 'hoat-hinh', 'thai-lan'];
    const rowCats = rowCatIds.map((id) => CATEGORIES.find((c) => c.id === id)).filter(Boolean);
    const results = await Promise.all(rowCats.map((c) => fetchCategoryList(c)));

    state.movies = results[0] || [];
    state.homeRows = rowCats
      .map((c, idx) => ({ id: c.id, isTopTen: idx === 0, movies: results[idx] || [] }))
      .filter((r) => r.movies.length > 0);

    if (state.movies.length) {
      renderHero(state.movies[0]);
      renderHistorySection();
      renderHomeSwimlanes();
    } else {
      swimWrap.innerHTML = `<div style="text-align:center;padding:36px;color:#fca5a5;">${escapeHtml(tr('loadError'))}</div>`;
    }
    return;
  }

  // Dedicated Category Grid View
  if (liftEl) liftEl.classList.remove('no-hero');
  $('topbar')?.classList.remove('solid');
  swimWrap.style.display = 'none';
  gridSec.style.display = 'block';
  $('catalogGrid').innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:42px;color:#b3b3b3;">${escapeHtml(tr('loadingCatalog'))}</div>`;

  const cat = CATEGORIES.find((c) => c.id === catId) || CATEGORIES[0];
  const list = await fetchCategoryList(cat);
  if (list.length) {
    state.movies = list;
    renderHero(state.movies[0]);
    renderHistorySection();
    renderMovieGrid(state.movies, getCategoryLabel(cat.id));
  } else {
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
    head.innerHTML = `
      <div class="section-title">
        ${row.isTopTen ? '<span class="top10-pill">TOP 10</span>' : ''}
        <span>${escapeHtml(getCategoryLabel(row.id))}</span>
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
      card.onclick = () => openMovieDetail(movie.slug, false);
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
    const res = await fetch(`${PHIM_API}/v1/api/tim-kiem?keyword=${encodeURIComponent(q)}&limit=24`);
    const json = await res.json();
    const list = mapPhimItems(json);
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

function renderHero(movie) {
  const box = $('heroBox');
  if (!movie) {
    box.style.display = 'none';
    return;
  }
  box.style.display = 'flex';
  const lang = resolveLanguage(state.prefs.primaryLang);
  box.innerHTML = `
    <img class="hero-bg" src="${movie.backdrop || movie.poster}" alt="${escapeHtml(movie.name)}" />
    <div class="hero-overlay"></div>
    <div class="hero-content">
      <div class="hero-inner">
        <div class="hero-eyebrow">
          <div class="nf-series-mark"><span class="nki-tag">NKI</span><span>AI CINEMA</span></div>
        </div>
        <h1 class="hero-title">${escapeHtml(movie.name)}</h1>
        <div class="hero-meta">
          <span class="match-score">98% Match</span>
          <span style="color:#e5e5e5;">${escapeHtml(movie.year || '2026')}</span>
          <span class="badge-ai">${escapeHtml(tr('heroBadge', { lang: lang.badge }))}</span>
          <span style="border:1px solid rgba(255,255,255,0.35);padding:1px 6px;border-radius:3px;font-size:10.5px;">${escapeHtml(movie.quality)}</span>
        </div>
        <p class="hero-sub">${escapeHtml(movie.origin_name || tr('heroDefaultDesc'))}</p>
        <div class="hero-actions">
          <button class="btn-nf-play" id="heroPlayBtn">${escapeHtml(tr('watchNow'))}</button>
          <button class="btn-nf-info" id="heroDetailBtn">ⓘ ${escapeHtml(tr('detailsBtn'))}</button>
        </div>
      </div>
    </div>
  `;
  $('heroPlayBtn').onclick = () => openMovieDetail(movie.slug, true);
  $('heroDetailBtn').onclick = () => openMovieDetail(movie.slug, false);
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
    card.onclick = () => openMovieDetail(movie.slug, false);
    grid.appendChild(card);
  });
}

function createMovieCardElement(movie) {
  const el = document.createElement('div');
  el.className = 'movie-card';
  el.innerHTML = `
    <div class="poster-wrap">
      <img loading="lazy" src="${movie.poster}" alt="${escapeHtml(movie.name)}" />
      <span class="card-nki-mark">NKI</span>
      <span class="card-badge">AI</span>
      <span class="card-quality">${escapeHtml((movie.episode_current || movie.quality || 'HD').slice(0, 12))}</span>
      <div class="card-hover-play"><span>▶</span></div>
      ${movie.progress ? `<div class="card-progress"><div class="card-progress-fill" style="width:${Math.round(movie.progress * 100)}%"></div></div>` : ''}
    </div>
    <div class="card-info">
      <div class="card-title">${escapeHtml(movie.name)}</div>
      <div class="card-meta">${escapeHtml(movie.year || movie.origin_name || 'HD • AI Sub')}</div>
    </div>
  `;
  return el;
}

/* ==================== MOVIE DETAIL ==================== */
async function openMovieDetail(slug, autoPlayFirst = false, resumeSec = 0, resumeUrl = '') {
  showToast('Đang tải thông tin phim...');
  try {
    const res = await fetch(`${PHIM_API}/phim/${encodeURIComponent(slug)}`);
    const json = await res.json();
    const m = json.movie || {};
    const servers = (json.episodes || []).map((s) => ({
      server_name: s.server_name || 'Server',
      episodes: (s.server_data || []).filter((e) => e.link_m3u8),
    })).filter((s) => s.episodes.length);

    const detail = {
      slug: m.slug || slug,
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
    state.currentMovie = detail;
    state.episodesFlat = servers[0]?.episodes || [];

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
    showToast('Không thể tải chi tiết phim này!');
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

function persistCurrentPlaybackProgress() {
  const video = $('videoEl');
  if (!state.currentMovie || !state.currentEpisode || !video) return;
  const cur = video.currentTime || 0;
  const dur = video.duration || 0;
  if (cur > 2 && isFinite(cur)) {
    saveHistoryItem(state.currentMovie, state.currentEpisode, cur, isFinite(dur) ? dur : 0);
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

function minimizePlayer() {
  const modal = $('playerModal');
  if (!modal.classList.contains('open')) return;
  persistCurrentPlaybackProgress();
  renderHistorySection();
  if ($('detailView').classList.contains('active') && state.currentMovie) {
    renderDetailView(state.currentMovie);
  }
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  else if (document.webkitFullscreenElement) document.webkitExitFullscreen?.();
  try { screen.orientation?.unlock?.(); } catch { /* ignore */ }
  state.isLocked = false;
  state.isMinimized = true;
  $('lockFab').classList.remove('visible');
  modal.classList.add('minimized');
  document.body.style.overflow = '';
  const video = $('videoEl');
  if ($('miniPlayBtn')) $('miniPlayBtn').textContent = video.paused ? '▶' : '⏸';
}

function restorePlayer() {
  const modal = $('playerModal');
  if (!modal.classList.contains('open')) return;
  state.isMinimized = false;
  modal.classList.remove('minimized');
  document.body.style.overflow = 'hidden';
  setControlsVisible(true);
}

function closePlayer() {
  const video = $('videoEl');
  persistCurrentPlaybackProgress();
  renderHistorySection();
  if ($('detailView').classList.contains('active') && state.currentMovie) {
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

async function persistCurrentSubCache(isFullyTranslated, translatedCount) {
  if (!state.currentMovie || !state.currentEpisode || state.primaryCues.length < 3) return;
  const targetLang = resolveLanguage(state.prefs.primaryLang);
  const key = buildSubCacheKey(state.currentMovie.slug, state.currentEpisode, targetLang.code);
  await saveCachedEpisodeSub(key, {
    primaryLangCode: targetLang.code,
    secondaryLangCode: state.prefs.dualSub ? state.prefs.secondaryLang : '',
    sourceLangCode: state.lastSubSourceLang || 'auto',
    trackLabel: state.lastSubTrackLabel || `Sub ${targetLang.badge}`,
    sourceCues: state.sourceCues.length ? state.sourceCues : state.primaryCues,
    primaryCues: state.primaryCues,
    secondaryCues: state.secondaryCues,
    isFullyTranslated,
    translatedCount: translatedCount ?? state.primaryCues.length,
  });
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

  // STEP 0: INSTANT RESTORE FROM INDEXEDDB (TRANSLATE ONCE, LOAD INSTANTLY FOREVER!)
  const cached = await loadCachedEpisodeSub(cacheKey);
  if (runId !== state.subTranslationRunId) return;
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

    updateSubStatusBadge(`💾 Sub ${targetLang.badge} • Đã lưu (${state.primaryCues.length} câu)`);
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

function openSettingsModal() {
  $('geminiKeyInput').value = state.prefs.geminiApiKey || '';
  $('geminiModelSelect').value = state.prefs.geminiModel || 'gemini-3-flash-preview';
  if ($('uiLangSelect')) {
    const curUi = resolveUiLang(state.prefs.uiLang);
    $('uiLangSelect').innerHTML = UI_LANGUAGES.map(
      (l) => `<option value="${l.code}" ${l.code === curUi ? 'selected' : ''}>${l.badge} — ${l.name}</option>`
    ).join('');
  }
  $('defaultLangSelect').innerHTML = SUBTITLE_LANGUAGES.map(
    (l) => `<option value="${l.code}" ${l.code === state.prefs.primaryLang ? 'selected' : ''}>${l.badge} — ${l.name} (${l.native})</option>`
  ).join('');
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

  // Topbar & Search
  $('brandHome').onclick = () => loadCategory('hot');
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

  $('openCastBtn').onclick = openTvCastModal;
  $('openSettingsBtn').onclick = openSettingsModal;
  $('refreshBtn').onclick = () => loadCategory(state.activeCategory);

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
  $('hudBackBtn').onclick = minimizePlayer;
  $('miniCloseBtn').onclick = (e) => {
    e.stopPropagation();
    closePlayer();
  };
  $('miniExpandBtn').onclick = (e) => {
    e.stopPropagation();
    restorePlayer();
  };
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
    persistCurrentPlaybackProgress();
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
      persistCurrentPlaybackProgress();
    }
  });
  window.addEventListener('pagehide', persistCurrentPlaybackProgress);
  window.addEventListener('beforeunload', persistCurrentPlaybackProgress);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') persistCurrentPlaybackProgress();
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
    if (state.isMinimized) {
      restorePlayer();
      return;
    }
    if (e.target !== stage && e.target !== video) return;
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
    if (state.movies.length) renderHero(state.movies[0]);
    $('aiDot').style.display = state.prefs.geminiApiKey ? 'block' : 'none';
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

  if (state.prefs.geminiApiKey) $('aiDot').style.display = 'block';
}

document.addEventListener('DOMContentLoaded', init);
