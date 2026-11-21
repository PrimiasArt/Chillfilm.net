import {
  SUBTITLE_LANGUAGES,
  resolveLanguage,
  parseSubtitleText,
  findActiveCueText,
  fetchOpenSubtitlesV3,
  translateCueBatch,
} from './subtitles.js';

const PHIM_API = 'https://phimapi.com';
const PHIM_CDN = 'https://phimimg.com';
const APK_RELEASE_URL = 'https://github.com/PrimiasArt/Chillfilm.net/releases/download/android-apk-latest/ChillFilm-latest.apk';

const CATEGORIES = [
  { id: 'hot', label: 'Mới & Hot', url: `${PHIM_API}/danh-sach/phim-moi-cap-nhat?page=1` },
  { id: 'phim-le', label: 'Phim Lẻ', url: `${PHIM_API}/v1/api/danh-sach/phim-le?page=1&limit=24` },
  { id: 'phim-bo', label: 'Phim Bộ', url: `${PHIM_API}/v1/api/danh-sach/phim-bo?page=1&limit=24` },
  { id: 'hoat-hinh', label: 'Hoạt Hình', url: `${PHIM_API}/v1/api/danh-sach/hoat-hinh?page=1&limit=24` },
  { id: 'han-quoc', label: 'Hàn Quốc', url: `${PHIM_API}/v1/api/quoc-gia/han-quoc?page=1&limit=24` },
  { id: 'au-my', label: 'Âu Mỹ', url: `${PHIM_API}/v1/api/quoc-gia/au-my?page=1&limit=24` },
  { id: 'trung-quoc', label: 'Trung Quốc', url: `${PHIM_API}/v1/api/quoc-gia/trung-quoc?page=1&limit=24` },
  { id: 'thai-lan', label: 'Thái Lan', url: `${PHIM_API}/v1/api/quoc-gia/thai-lan?page=1&limit=24` },
  { id: 'nhat-ban', label: 'Nhật Bản', url: `${PHIM_API}/v1/api/quoc-gia/nhat-ban?page=1&limit=24` },
];

const DEFAULT_PREFS = {
  primaryLang: 'vi',
  secondaryLang: 'en',
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
  controlsVisible: true,
  controlsTimer: null,
  // Subtitles
  subTracks: [],
  activeSubTrackId: null,
  sourceCues: [],
  primaryCues: [],
  secondaryCues: [],
  subTranslationRunId: 0,
  subStatusText: 'AI Sub sẵn sàng',
};

const $ = (id) => document.getElementById(id);

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

function saveHistoryItem(movie, ep, currentTime, duration) {
  if (!movie || !ep || !duration || duration < 10) return;
  const list = loadHistory().filter((h) => h.slug !== movie.slug);
  list.unshift({
    slug: movie.slug,
    name: movie.name,
    origin_name: movie.origin_name || '',
    poster: movie.poster,
    year: movie.year || '',
    epName: ep.name,
    epUrl: ep.link_m3u8,
    position: Math.floor(currentTime),
    duration: Math.floor(duration),
    progress: Math.min(1, Math.max(0.02, currentTime / duration)),
    ts: Date.now(),
  });
  localStorage.setItem('chillfilm_nki_history_v1', JSON.stringify(list.slice(0, 18)));
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

/* ==================== CATALOG & SEARCH ==================== */
async function loadCategory(catId) {
  state.activeCategory = catId;
  renderCategories();
  closeDetailView();
  $('catalogGrid').innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:42px;color:#9ca3af;">Đang tải kho phim ChillFilm...</div>';

  const cat = CATEGORIES.find((c) => c.id === catId) || CATEGORIES[0];
  try {
    const res = await fetch(cat.url);
    const json = await res.json();
    const rawItems = json.items || json.data?.items || [];
    state.movies = rawItems.map((m) => ({
      slug: m.slug,
      name: m.name,
      origin_name: m.origin_name || '',
      year: m.year ? String(m.year) : '',
      quality: m.quality || 'HD',
      episode_current: m.episode_current || '',
      poster: normalizeImage(m.poster_url || m.thumb_url),
      backdrop: normalizeImage(m.thumb_url || m.poster_url),
    }));
    renderHero(state.movies[0]);
    renderHistorySection();
    renderMovieGrid(state.movies, cat.label);
  } catch (e) {
    $('catalogGrid').innerHTML = `<div style="grid-column:1/-1;text-align:center;padding:36px;color:#fca5a5;">Không tải được danh sách phim. Hãy thử làm mới lại trang!</div>`;
  }
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
  $('catalogTitle').textContent = `Kết quả tìm kiếm: "${q}"`;
  $('catalogGrid').innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:42px;color:#9ca3af;">Đang tìm phim...</div>';

  try {
    const res = await fetch(`${PHIM_API}/v1/api/tim-kiem?keyword=${encodeURIComponent(q)}&limit=24`);
    const json = await res.json();
    const rawItems = json.data?.items || json.items || [];
    const list = rawItems.map((m) => ({
      slug: m.slug,
      name: m.name,
      origin_name: m.origin_name || '',
      year: m.year ? String(m.year) : '',
      quality: m.quality || 'HD',
      episode_current: m.episode_current || '',
      poster: normalizeImage(m.poster_url || m.thumb_url),
      backdrop: normalizeImage(m.thumb_url || m.poster_url),
    }));
    renderMovieGrid(list, `Kết quả cho "${q}" (${list.length})`);
  } catch {
    showToast('Lỗi tìm kiếm phim!');
  }
}

function renderCategories() {
  const strip = $('catStrip');
  strip.innerHTML = '';
  CATEGORIES.forEach((c) => {
    const btn = document.createElement('button');
    btn.className = `cat-chip ${state.activeCategory === c.id ? 'active' : ''}`;
    btn.textContent = c.label;
    btn.onclick = () => {
      $('searchInputDesktop').value = '';
      $('searchInputMobile').value = '';
      loadCategory(c.id);
    };
    strip.appendChild(btn);
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
      <div class="hero-meta">
        <span class="badge-ai">AI SUB • ${lang.badge} & ĐA NGÔN NGỮ</span>
        <span style="font-size:12px;color:#c084fc;font-weight:700;">${escapeHtml(movie.year || '2026')} • ${escapeHtml(movie.quality)}</span>
      </div>
      <h1 class="hero-title">${escapeHtml(movie.name)}</h1>
      <p class="hero-sub">${escapeHtml(movie.origin_name || 'Xem trực tiếp trên trình duyệt PC, điện thoại hoặc truyền lên TV — hỗ trợ dịch phụ đề mọi ngôn ngữ AI.')}</p>
      <div class="hero-actions">
        <button class="btn-grad" id="heroPlayBtn">▶ Xem Ngay</button>
        <button class="btn-glass" id="heroDetailBtn">Chi Tiết & Tập</button>
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
  history.slice(0, 6).forEach((item) => {
    const card = createMovieCardElement({
      slug: item.slug,
      name: item.name,
      year: `${item.epName} • ${formatTime(item.position)}`,
      quality: 'TIẾP TỤC',
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
    grid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:42px;color:#9ca3af;">Không có phim nào phù hợp.</div>';
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
      <span class="card-badge">AI SUB</span>
      <span class="card-quality">${escapeHtml((movie.episode_current || movie.quality || 'HD').slice(0, 12))}</span>
      ${movie.progress ? `<div class="card-progress"><div class="card-progress-fill" style="width:${Math.round(movie.progress * 100)}%"></div></div>` : ''}
    </div>
    <div class="card-info">
      <div class="card-title">${escapeHtml(movie.name)}</div>
      <div class="card-meta">${escapeHtml(movie.year || movie.origin_name || 'ChillFilm')}</div>
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
      const targetEp = (resumeUrl && state.episodesFlat.find((e) => e.link_m3u8 === resumeUrl)) || state.episodesFlat[0];
      openPlayer(detail, targetEp, resumeSec);
      return;
    }

    renderDetailView(detail);
  } catch (e) {
    showToast('Không thể tải chi tiết phim này!');
  }
}

function renderDetailView(detail) {
  $('homeView').style.display = 'none';
  const dv = $('detailView');
  dv.classList.add('active');
  const firstEp = detail.servers[0]?.episodes?.[0];
  const lang = resolveLanguage(state.prefs.primaryLang);

  dv.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;">
      <button class="btn-glass" id="detailBackBtn">← Quay lại kho phim</button>
      ${firstEp ? `<button class="btn-glass" id="detailCastBtn">📡 Phát lên TV</button>` : ''}
    </div>
    <div class="detail-hero">
      <img class="detail-poster" src="${detail.poster}" alt="${escapeHtml(detail.name)}" />
      <div style="display:flex;flex-direction:column;gap:8px;min-width:0;">
        <div class="hero-meta">
          <span class="badge-ai">AI SUB (${lang.badge})</span>
          <span style="font-size:12px;color:#c084fc;font-weight:700;">${escapeHtml(detail.year)} • ${escapeHtml(detail.time || detail.quality)}</span>
        </div>
        <h2 style="font-size:clamp(18px,3vw,28px);font-weight:900;">${escapeHtml(detail.name)}</h2>
        ${detail.origin_name ? `<div style="font-size:13px;color:#9ca3af;">${escapeHtml(detail.origin_name)}</div>` : ''}
        <p style="font-size:13px;color:#cbd5e1;line-height:1.6;">${escapeHtml(detail.content || 'Hỗ trợ phát trực tiếp FHD kèm phụ đề AI đa ngôn ngữ.')}</p>
        <div style="display:flex;flex-wrap:wrap;gap:10px;margin-top:6px;">
          ${firstEp ? `<button class="btn-grad" id="detailPlayPrimary">▶ Xem Ngay (${escapeHtml(firstEp.name)})</button>` : '<span style="color:#fca5a5;font-size:13px;">Chưa có luồng phát trực tiếp.</span>'}
        </div>
      </div>
    </div>
    <div id="detailServersWrap" style="margin-top:20px;"></div>
  `;

  $('detailBackBtn').onclick = closeDetailView;
  if (firstEp) {
    $('detailPlayPrimary').onclick = () => openPlayer(detail, firstEp, 0);
    $('detailCastBtn').onclick = () => castCurrentToTv(detail, firstEp, 0);
  }

  const wrap = $('detailServersWrap');
  detail.servers.forEach((srv) => {
    const block = document.createElement('div');
    block.style.marginBottom = '18px';
    block.innerHTML = `<div class="section-title" style="margin-bottom:8px;">${escapeHtml(srv.server_name)} (${srv.episodes.length} tập)</div>`;
    const grid = document.createElement('div');
    grid.className = 'ep-grid';
    srv.episodes.forEach((ep) => {
      const b = document.createElement('button');
      b.className = 'ep-btn';
      b.textContent = ep.name;
      b.onclick = () => {
        state.episodesFlat = srv.episodes;
        openPlayer(detail, ep, 0);
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

function openPlayer(movie, episode, startSec = 0) {
  state.currentMovie = movie;
  state.currentEpisode = episode;
  const modal = $('playerModal');
  const video = $('videoEl');
  modal.classList.add('open');
  document.body.style.overflow = 'hidden';

  $('hudMovieTitle').textContent = `${movie.name} • ${episode.name}`;
  updateSubStatusBadge('Đang chuẩn bị phụ đề AI...');
  setControlsVisible(true);

  // Destroy previous HLS instance
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }

  const streamUrl = episode.link_m3u8;
  video.crossOrigin = 'anonymous';

  const startPlayback = () => {
    if (startSec > 2) video.currentTime = startSec;
    video.playbackRate = state.speed;
    video.play().catch(() => {});
  };

  if (streamUrl.includes('.m3u8') && window.Hls && window.Hls.isSupported()) {
    const hls = new window.Hls({
      enableWorker: true,
      lowLatencyMode: false,
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
  $('hudNextOrAspectBtn').textContent = nextEp ? '⏭ Tập sau' : '⬜ Tỉ lệ';
  $('hudNextOrAspectBtn').onclick = () => {
    if (nextEp) openPlayer(movie, nextEp, 0);
    else openMoreModal();
  };

  // Auto-discover and translate subtitles into user's chosen AI language!
  autoLoadSubtitlesForMovie(movie, episode);
}

function closePlayer() {
  const video = $('videoEl');
  if (state.currentMovie && state.currentEpisode && video.duration) {
    saveHistoryItem(state.currentMovie, state.currentEpisode, video.currentTime, video.duration);
    renderHistorySection();
  }
  video.pause();
  video.removeAttribute('src');
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  $('playerModal').classList.remove('open');
  document.body.style.overflow = '';
  state.isLocked = false;
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

async function autoLoadSubtitlesForMovie(movie, episode) {
  state.subTracks = [];
  state.sourceCues = [];
  state.primaryCues = [];
  state.secondaryCues = [];
  renderActiveSubtitle(0);

  const isSeries = movie.type !== 'single' || state.episodesFlat.length > 1;
  const epNumMatch = (episode.name || '').match(/\d+/);
  const epNum = epNumMatch ? Number(epNumMatch[0]) : 1;

  if (!movie.imdbId) {
    updateSubStatusBadge('Chưa có mã IMDb • Bấm CC để nạp file .SRT');
    return;
  }

  updateSubStatusBadge('Đang tìm phụ đề trên OpenSubtitles...');
  const tracks = await fetchOpenSubtitlesV3(movie.imdbId, isSeries, 1, epNum);
  state.subTracks = tracks;
  if (!tracks.length) {
    updateSubStatusBadge('Không thấy Sub rời • Bấm CC để tải file .SRT lên');
    return;
  }

  const targetLang = resolveLanguage(state.prefs.primaryLang).code;
  const best =
    tracks.find((t) => t.langCode === targetLang) ||
    tracks.find((t) => t.langCode === 'en') ||
    tracks.find((t) => t.langCode === 'vi') ||
    tracks[0];

  await selectSubtitleTrack(best);
}

async function selectSubtitleTrack(track) {
  state.activeSubTrackId = track.id;
  updateSubStatusBadge(`Đang tải ${track.label}...`);
  try {
    const res = await fetch(track.url);
    const raw = await res.text();
    const cues = parseSubtitleText(raw);
    if (!cues.length) {
      updateSubStatusBadge('File phụ đề rỗng — thử bản khác');
      return;
    }
    await applyLoadedSourceCues(cues, track.langCode);
  } catch {
    updateSubStatusBadge('Không tải được file phụ đề (CORS) — thử nạp .SRT từ máy');
  }
}

async function applyLoadedSourceCues(cues, sourceLangCode = 'auto') {
  const runId = ++state.subTranslationRunId;
  state.sourceCues = cues;
  state.primaryCues = cues;
  state.secondaryCues = state.prefs.dualSub ? cues : [];

  const targetLang = resolveLanguage(state.prefs.primaryLang);
  if (sourceLangCode === targetLang.code) {
    updateSubStatusBadge(`Sub ${targetLang.badge} Gốc (${cues.length} câu)`);
    renderActiveSubtitle($('videoEl').currentTime * 1000);
  } else {
    updateSubStatusBadge(`AI đang dịch sang ${targetLang.name}...`);
    const working = cues.slice();
    const batchSize = 35;
    for (let i = 0; i < cues.length; i += batchSize) {
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
      const pct = Math.min(99, Math.round(((i + batchSize) / cues.length) * 100));
      updateSubStatusBadge(`Sub ${targetLang.badge} AI (${pct}%)`);
      renderActiveSubtitle($('videoEl').currentTime * 1000);
    }
    updateSubStatusBadge(`Sub ${targetLang.badge} AI (${cues.length} câu)`);
  }

  // Secondary subtitle language translation if Dual Sub is ON and differs from source
  if (state.prefs.dualSub && state.prefs.secondaryLang !== 'off') {
    const secCode = state.prefs.secondaryLang;
    if (secCode === 'orig' || secCode === sourceLangCode) {
      state.secondaryCues = cues;
    } else {
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
    }
  }
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
  renderCategories();
  loadCategory('hot');

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

  // Video & Player Controls
  const video = $('videoEl');
  $('hudBackBtn').onclick = closePlayer;
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
    showGestureToast('🔒 Đã khóa màn hình');
  };
  $('lockFab').onclick = () => {
    state.isLocked = false;
    $('lockFab').classList.remove('visible');
    setControlsVisible(true);
    showGestureToast('🔓 Đã mở khóa');
  };
  $('hudSpeedBoostBtn').onclick = openMoreModal;
  $('hudFsBtn').onclick = () => {
    const stage = $('playerModal');
    if (!document.fullscreenElement) stage.requestFullscreen?.().catch(() => {});
    else document.exitFullscreen?.().catch(() => {});
  };

  // Seekbar & timeupdate
  video.addEventListener('play', () => {
    $('playPauseBtn').textContent = '⏸';
    setControlsVisible(true);
  });
  video.addEventListener('pause', () => {
    $('playPauseBtn').textContent = '▶';
    setControlsVisible(true);
  });
  video.addEventListener('timeupdate', () => {
    const cur = video.currentTime || 0;
    const dur = video.duration || 0;
    $('curTimeLabel').textContent = formatTime(cur);
    $('durTimeLabel').textContent = formatTime(dur);
    if (dur > 0) $('seekRange').value = String((cur / dur) * 1000);
    renderActiveSubtitle(cur * 1000);
  });
  $('seekRange').addEventListener('input', (e) => {
    const dur = video.duration || 0;
    if (dur > 0) video.currentTime = (Number(e.target.value) / 1000) * dur;
    setControlsVisible(true);
  });

  // Tap & Swipe Gestures on Player Stage
  const stage = $('playerStage');
  let lastTap = 0;
  let touchStart = null;
  stage.addEventListener('click', (e) => {
    if (e.target !== stage && e.target !== video) return;
    if (state.isLocked) {
      showGestureToast('🔒 Chạm ổ khóa bên trái để mở');
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
    if (state.isLocked || e.touches.length !== 1) return;
    const t = e.touches[0];
    touchStart = { x: t.clientX, y: t.clientY, vol: state.volumeScale, bright: state.brightness, time: video.currentTime, mode: null };
  }, { passive: true });

  stage.addEventListener('touchmove', (e) => {
    if (!touchStart || state.isLocked || e.touches.length !== 1) return;
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

  // PC Keyboard Shortcuts
  document.addEventListener('keydown', (e) => {
    if (!$('playerModal').classList.contains('open')) return;
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) return;
    if (e.code === 'Space') { e.preventDefault(); video.paused ? video.play() : video.pause(); }
    else if (e.key === 'ArrowLeft') { video.currentTime = Math.max(0, video.currentTime - 10); showGestureToast('⏪ -10s'); }
    else if (e.key === 'ArrowRight') { video.currentTime = Math.min(video.duration || 0, video.currentTime + 10); showGestureToast('+10s ⏩'); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); ensureWebAudioDsp(video); applyVolumeBoost(state.volumeScale + 0.1); showGestureToast(`🔊 ${Math.round(state.volumeScale * 100)}%`); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); ensureWebAudioDsp(video); applyVolumeBoost(state.volumeScale - 0.1); showGestureToast(`🔊 ${Math.round(state.volumeScale * 100)}%`); }
    else if (e.key.toLowerCase() === 'f') { $('hudFsBtn').click(); }
    else if (e.key.toLowerCase() === 'c') { openSubModal(); }
    else if (e.key === 'Escape') { closePlayer(); }
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
      await applyLoadedSourceCues(cues, 'auto');
      showToast(`Đã nạp ${cues.length} câu từ ${f.name}`);
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

  // Settings Modal (Gemini Key, Default AI Sub Language, Direct URL / Local Video)
  $('saveSettingsBtn').onclick = () => {
    state.prefs.geminiApiKey = $('geminiKeyInput').value.trim();
    state.prefs.geminiModel = $('geminiModelSelect').value;
    state.prefs.primaryLang = $('defaultLangSelect').value;
    savePrefs();
    $('aiDot').style.display = state.prefs.geminiApiKey ? 'block' : 'none';
    $('settingsModal').classList.remove('open');
    showToast('✅ Đã lưu cấu hình AI & ngôn ngữ phụ đề!');
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
