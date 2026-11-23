/**
 * ChillFilm x NKI Cloud Sync & Centralized AI Client (Web App)
 * Connects to All-in-One Vercel Functions (/api/*) backed by Neon Postgres + Vercel Blob.
 */

const CLOUD_SESSION_KEY = 'chillfilm_nki_cloud_session_v1';

export function normalizeMovieSlug(raw) {
  const s = String(raw || '')
    .trim()
    .toLowerCase()
    .replace(/^(modern|ophim|kkphim|phimapi|curated|custom)_+/i, '')
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return (s || 'unknown').slice(0, 120);
}

export function normalizeEpisodeKey(rawNameOrSlug) {
  const ascii = String(rawNameOrSlug || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .trim()
    .toLowerCase();
  if (!ascii || ascii === 'full' || ascii === 'full hd' || ascii === 'hd') return 'full';
  const tapMatch = ascii.match(/(?:tap|ep|episode)\s*[-_:]?\s*0*(\d+)([a-z]?)/);
  if (tapMatch) return `tap-${tapMatch[1]}${tapMatch[2] || ''}`;
  const numOnly = ascii.match(/^0*(\d+)([a-z]?)$/);
  if (numOnly) return `tap-${numOnly[1]}${numOnly[2] || ''}`;
  const slug = ascii.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  return (slug || 'full').slice(0, 64);
}

export function getCloudApiBase() {
  if (typeof window !== 'undefined' && window.location?.origin?.startsWith('http')) {
    return window.location.origin;
  }
  return 'https://chillfilm.vercel.app';
}

export function getCloudSession() {
  try {
    const raw = localStorage.getItem(CLOUD_SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || !parsed.token || !parsed.user) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveCloudSession(session) {
  try {
    if (!session) {
      localStorage.removeItem(CLOUD_SESSION_KEY);
    } else {
      localStorage.setItem(CLOUD_SESSION_KEY, JSON.stringify(session));
    }
  } catch {
    // ignore storage errors
  }
}

export function clearCloudSession() {
  saveCloudSession(null);
}

export function isCloudSignedIn() {
  return Boolean(getCloudSession()?.token);
}

async function apiFetch(path, { method = 'GET', body = null, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const session = getCloudSession();
  if (auth && session?.token) {
    headers.Authorization = `Bearer ${session.token}`;
  }
  const res = await fetch(`${getCloudApiBase()}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = {};
  try {
    data = await res.json();
  } catch {
    data = {};
  }
  if (res.status === 401 && auth && session?.token) {
    // Token revoked or expired
    clearCloudSession();
  }
  if (!res.ok) {
    const err = new Error(data.error || `HTTP ${res.status}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export async function fetchCloudHealth() {
  return apiFetch('/api/health', { auth: false });
}

export async function cloudLogin(username, password) {
  const data = await apiFetch('/api/auth/login', {
    method: 'POST',
    auth: false,
    body: { username, password },
  });
  const session = {
    token: data.token,
    user: data.user,
    serverAiReady: Boolean(data.serverAiReady),
    defaultModel: data.defaultModel || 'gemini-3-flash-preview',
    updatedAt: Date.now(),
  };
  saveCloudSession(session);
  return session;
}

export async function cloudRegister({ username, password, displayName, inviteCode }) {
  const data = await apiFetch('/api/auth/register', {
    method: 'POST',
    auth: false,
    body: { username, password, displayName, inviteCode },
  });
  const session = {
    token: data.token,
    user: data.user,
    serverAiReady: Boolean(data.serverAiReady),
    defaultModel: data.defaultModel || 'gemini-3-flash-preview',
    updatedAt: Date.now(),
  };
  saveCloudSession(session);
  return session;
}

export async function cloudRefreshMe() {
  const cur = getCloudSession();
  if (!cur?.token) return null;
  const data = await apiFetch('/api/auth/me', { method: 'GET', auth: true });
  const next = {
    ...cur,
    user: data.user,
    serverAiReady: Boolean(data.serverAiReady),
    defaultModel: data.defaultModel || cur.defaultModel || 'gemini-3-flash-preview',
    updatedAt: Date.now(),
  };
  saveCloudSession(next);
  return next;
}

export async function cloudUpdateProfile({
  displayName = undefined,
  currentPassword = undefined,
  newPassword = undefined,
  revokeOtherSessions = false,
} = {}) {
  const cur = getCloudSession();
  if (!cur?.token) throw new Error('Bạn chưa đăng nhập NKI Cloud');
  const body = {};
  if (typeof displayName === 'string') body.displayName = displayName.trim();
  if (typeof newPassword === 'string' && newPassword.length > 0) {
    body.currentPassword = currentPassword || '';
    body.newPassword = newPassword;
  }
  if (revokeOtherSessions) body.revokeOtherSessions = true;

  const data = await apiFetch('/api/auth/profile', {
    method: 'POST',
    auth: true,
    body,
  });
  const next = {
    ...cur,
    token: data.token || cur.token,
    user: data.user || cur.user,
    serverAiReady: typeof data.serverAiReady === 'boolean' ? data.serverAiReady : cur.serverAiReady,
    defaultModel: data.defaultModel || cur.defaultModel || 'gemini-2.5-flash',
    updatedAt: Date.now(),
  };
  saveCloudSession(next);
  return { session: next, changed: data.changed || [] };
}

export async function cloudFetchAdminConfig() {
  return apiFetch('/api/admin/config', { method: 'GET', auth: true });
}

export async function cloudAdminAction(payload) {
  return apiFetch('/api/admin/config', { method: 'POST', auth: true, body: payload });
}

export async function cloudPullState(since = 0) {
  if (!isCloudSignedIn()) return null;
  const q = since > 0 ? `?since=${encodeURIComponent(String(since))}` : '';
  return apiFetch(`/api/sync/state${q}`, { method: 'GET', auth: true });
}

export async function cloudPushState({
  watchProgress = [],
  watchProgressRemoved = [],
  myList = [],
  myListRemoved = [],
  prefs = null,
  prefsUpdatedAt = 0,
  useBeacon = false,
} = {}) {
  const session = getCloudSession();
  if (!session?.token) return null;
  if (!watchProgress.length && !watchProgressRemoved.length && !myList.length && !myListRemoved.length && !prefs) return null;

  const payload = { watchProgress };
  if (watchProgressRemoved.length) payload.watchProgressRemoved = watchProgressRemoved;
  if (myList.length) payload.myList = myList;
  if (myListRemoved.length) payload.myListRemoved = myListRemoved;
  if (prefs) {
    payload.prefs = prefs;
    payload.prefsUpdatedAt = prefsUpdatedAt || Date.now();
  }

  if (useBeacon && typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
    try {
      const beaconPayload = JSON.stringify({ ...payload, token: session.token });
      const blob = new Blob([beaconPayload], { type: 'text/plain;charset=UTF-8' });
      navigator.sendBeacon(`${getCloudApiBase()}/api/sync/state`, blob);
      return { ok: true, viaBeacon: true };
    } catch {
      // fall through to keepalive fetch
    }
  }

  const res = await fetch(`${getCloudApiBase()}/api/sync/state`, {
    method: 'POST',
    keepalive: Boolean(useBeacon),
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) return null;
  return res.json().catch(() => null);
}

export async function cloudFetchSubtitle(movieSlug, episodeNameOrSlug, lang) {
  if (!isCloudSignedIn()) return null;
  const m = normalizeMovieSlug(movieSlug);
  const ep = normalizeEpisodeKey(episodeNameOrSlug);
  const l = String(lang || 'vi').trim().toLowerCase();
  if (!m || m === 'unknown' || m === 'direct' || m === 'local') return null;
  try {
    const data = await apiFetch(
      `/api/sync/subtitles?movieSlug=${encodeURIComponent(m)}&episodeKey=${encodeURIComponent(ep)}&lang=${encodeURIComponent(l)}`,
      { method: 'GET', auth: true }
    );
    if (data?.hit && data.bundle && Array.isArray(data.bundle.primaryCues) && data.bundle.primaryCues.length >= 3) {
      return data.bundle;
    }
  } catch {
    // non-fatal cloud miss
  }
  return null;
}

export async function cloudSaveSubtitle({
  movieSlug,
  episodeNameOrSlug,
  primaryLangCode,
  secondaryLangCode = '',
  sourceLangCode = 'auto',
  trackLabel = 'NKI Cloud',
  sourceCues = [],
  primaryCues = [],
  secondaryCues = [],
  isFullyTranslated = true,
  forceOverwrite = false,
}) {
  if (!isCloudSignedIn()) return false;
  const m = normalizeMovieSlug(movieSlug);
  if (!m || m === 'unknown' || m === 'direct' || m === 'local' || primaryCues.length < 3) return false;
  const ep = normalizeEpisodeKey(episodeNameOrSlug);
  try {
    await apiFetch('/api/sync/subtitles', {
      method: 'PUT',
      auth: true,
      body: {
        movieSlug: m,
        episodeKey: ep,
        primaryLangCode,
        secondaryLangCode,
        sourceLangCode,
        sourcePlatform: trackLabel,
        isFullyTranslated,
        forceOverwrite: Boolean(forceOverwrite),
        sourceCues: sourceCues.map((c, idx) => [c.id || idx + 1, c.startMs || 0, c.endMs || 0, c.text || '']),
        primaryCues: primaryCues.map((c, idx) => [c.id || idx + 1, c.startMs || 0, c.endMs || 0, c.text || '']),
        secondaryCues: secondaryCues.map((c, idx) => [c.id || idx + 1, c.startMs || 0, c.endMs || 0, c.text || '']),
      },
    });
    return true;
  } catch {
    return false;
  }
}

export async function cloudTranslateCueBatch({ cues, targetLang, model, movieContext = null }) {
  const session = getCloudSession();
  if (!session?.token || !cues?.length) return null;
  const data = await apiFetch('/api/ai/translate', {
    method: 'POST',
    auth: true,
    body: {
      texts: cues.map((c) => c.text),
      targetLang,
      model: model || session.defaultModel || 'gemini-3-flash-preview',
      movieContext: movieContext || undefined,
    },
  });
  if (!Array.isArray(data?.translated) || data.translated.length !== cues.length) {
    return null;
  }
  return cues.map((c, idx) => ({ ...c, text: (data.translated[idx] || c.text).trim() }));
}

export async function cloudTranscribeAudioChunk({
  audioBase64,
  mimeType = 'audio/webm',
  chunkStartOffsetMs = 0,
  chunkDurationMs = 18000,
  targetLang = 'vi',
  model = '',
  movieContext = null,
}) {
  const session = getCloudSession();
  if (!session?.token || !audioBase64) return null;
  return apiFetch('/api/ai/audio-sub', {
    method: 'POST',
    auth: true,
    body: {
      audioBase64,
      mimeType,
      chunkStartOffsetMs,
      chunkDurationMs,
      targetLang,
      model: model || session.defaultModel || 'gemini-3-flash-preview',
      preferTurboFlash: true,
      movieContext: movieContext || undefined,
    },
  });
}

/**
 * Fetches LIVE available Gemini models from Google API via /api/ai/models
 * (uses centralized server GEMINI_API_KEY when signed in, or optional personal key when provided).
 */
export async function cloudFetchLiveModels(personalApiKey = '') {
  const cleanKey = String(personalApiKey || '').trim();
  if (cleanKey.length >= 15) {
    return apiFetch('/api/ai/models', {
      method: 'POST',
      auth: isCloudSignedIn(),
      body: { apiKey: cleanKey },
    });
  }
  return apiFetch('/api/ai/models', { method: 'GET', auth: true });
}

/**
 * Fetches auto-updating multi-source live movie catalog & enriched Hero Billboard slides.
 */
export async function fetchLiveCatalogFeed(forceRefresh = false) {
  const q = forceRefresh ? '?refresh=1' : '';
  const res = await fetch(`${getCloudApiBase()}/api/catalog/feed${q}`);
  if (!res.ok) throw new Error(`Catalog HTTP ${res.status}`);
  return res.json();
}

export async function fetchCatalogBrowse(params = {}) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      qs.set(k, String(v).trim());
    }
  });
  const res = await fetch(`${getCloudApiBase()}/api/catalog/browse?${qs.toString()}`);
  if (!res.ok) throw new Error(`Browse HTTP ${res.status}`);
  return res.json();
}

export async function fetchCatalogDetail({ source = 'phimapi', slug, sourceId = '' }) {
  const qs = new URLSearchParams({ source, slug });
  if (sourceId) qs.set('sourceId', sourceId);
  const res = await fetch(`${getCloudApiBase()}/api/catalog/detail?${qs.toString()}`);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error || `Detail HTTP ${res.status}`);
  }
  return data.detail;
}

/**
 * Discovers online subtitle tracks via server-side Cinemeta IMDb lookup + OpenSubtitles v3,
 * returning tracks equipped with CORS-safe proxyUrl fallbacks.
 */
export async function fetchCatalogSubtitles({
  imdbId = '',
  title = '',
  originTitle = '',
  year = '',
  isSeries = false,
  season = 1,
  episode = 1,
} = {}) {
  const qs = new URLSearchParams();
  if (imdbId) qs.set('imdbId', imdbId);
  if (title) qs.set('title', title);
  if (originTitle) qs.set('originTitle', originTitle);
  if (year) qs.set('year', String(year));
  qs.set('type', isSeries ? 'series' : 'movie');
  qs.set('season', String(season || 1));
  qs.set('episode', String(episode || 1));

  try {
    const res = await fetch(`${getCloudApiBase()}/api/catalog/subtitles?${qs.toString()}`);
    if (!res.ok) return { imdbId: imdbId || '', tracks: [], resolvedViaCinemeta: false };
    const data = await res.json().catch(() => ({}));
    return {
      imdbId: data.imdbId || imdbId || '',
      tracks: Array.isArray(data.tracks) ? data.tracks : [],
      resolvedViaCinemeta: Boolean(data.resolvedViaCinemeta),
    };
  } catch {
    return { imdbId: imdbId || '', tracks: [], resolvedViaCinemeta: false };
  }
}

/**
 * Downloads raw .SRT/.VTT/.ASS text reliably: tries direct CORS fetch first, then falls back
 * to ChillFilm's CORS-free /api/catalog/subproxy endpoint.
 */
export async function fetchSubtitleTextSmart(track, signal = undefined) {
  if (!track || !track.url) return '';
  const base = getCloudApiBase();
  const proxyUrl = track.proxyUrl
    ? (track.proxyUrl.startsWith('http') ? track.proxyUrl : `${base}${track.proxyUrl}`)
    : `${base}/api/catalog/subproxy?url=${encodeURIComponent(track.url)}`;

  // Try direct fetch first if HTTPS (fast when CDN sets ACAO: *)
  try {
    const directRes = await fetch(track.url, { signal });
    if (directRes.ok) {
      const txt = await directRes.text();
      if (txt && txt.trim().length > 20) return txt;
    }
  } catch {
    // Fall through to server-side CORS proxy
  }

  // Fallback to CORS-safe allowlisted server proxy
  const proxyRes = await fetch(proxyUrl, { signal });
  if (!proxyRes.ok) throw new Error(`Subtitle proxy HTTP ${proxyRes.status}`);
  return proxyRes.text();
}

/* ==================== WATCH PARTY 2.0 CLIENT (/api/party) ==================== */
export async function partyListLobby() {
  return apiFetch('/api/party?op=lobby', { method: 'GET', auth: false });
}

export async function partyCreate({
  movie,
  episode,
  nickname,
  roomTitle = '',
  visibility = 'PRIVATE',
  controlMode = 'HOST_ONLY',
  passcode = '',
  positionSec = 0,
  isPlaying = false,
}) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: {
      action: 'create',
      movie,
      episode,
      nickname,
      roomTitle,
      visibility,
      controlMode,
      passcode,
      positionSec,
      isPlaying,
    },
  });
}

export async function partyJoin({ roomCode, nickname, memberId = '', passcode = '', hostToken = '', silentRejoin = false }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'join', roomCode, nickname, memberId, passcode, hostToken, silentRejoin },
  });
}

export async function partyPoll({
  roomCode,
  sinceMsgId = 0,
  sinceReactionTs = 0,
  memberId = '',
  nickname = '',
  hostToken = '',
  status = 'playing',
  pos = 0,
}) {
  const qs = new URLSearchParams({
    room: roomCode,
    sinceMsgId: String(sinceMsgId || 0),
    sinceReactionTs: String(sinceReactionTs || 0),
    memberId,
    nickname,
    status,
    pos: String(Math.round(pos || 0)),
  });
  if (hostToken) qs.set('hostToken', hostToken);
  return apiFetch(`/api/party?${qs.toString()}`, { method: 'GET', auth: false });
}

export async function partyControl({
  roomCode,
  hostToken = '',
  nickname = '',
  isPlaying,
  positionSec,
  playbackRate = 1,
  movie = null,
  episode = null,
  countdown = false,
  actionNote = '',
}) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: {
      action: 'control',
      roomCode,
      hostToken,
      nickname,
      isPlaying,
      positionSec,
      playbackRate,
      movie,
      episode,
      countdown,
      actionNote,
    },
  });
}

export async function partyUpdateSettings({ roomCode, hostToken, controlMode, visibility, roomTitle, passcode }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'settings', roomCode, hostToken, controlMode, visibility, roomTitle, passcode },
  });
}

export async function partyClaimHost({ roomCode, memberId, nickname }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'claim_host', roomCode, memberId, nickname },
  });
}

export async function partyQueueAdd({ roomCode, movie, episode, nickname, memberId }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'queue_add', roomCode, movie, episode, nickname, memberId },
  });
}

export async function partyQueueVote({ roomCode, itemId, memberId }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'queue_vote', roomCode, itemId, memberId },
  });
}

export async function partyQueueRemove({ roomCode, itemId, memberId, hostToken = '' }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'queue_remove', roomCode, itemId, memberId, hostToken },
  });
}

export async function partyQueuePlay({ roomCode, itemId = '', nickname = '', hostToken = '' }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'queue_play', roomCode, itemId, nickname, hostToken },
  });
}

export async function partyReact({ roomCode, emoji, nickname }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'react', roomCode, emoji, nickname },
  });
}

export async function partySendChat({ roomCode, nickname, text, hostToken = '' }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'chat', roomCode, nickname, text, hostToken },
  });
}

export async function partyLeave({ roomCode, memberId }) {
  return apiFetch('/api/party', {
    method: 'POST',
    auth: false,
    body: { action: 'leave', roomCode, memberId },
  });
}

/* ==================== #2 AI GLOSSARY & #9 AI VIBE SEARCH CLIENT ==================== */
export async function fetchMovieGlossary({ slug, title = '', originTitle = '', synopsis = '', genres = [], lang = 'vi', generate = false, force = false } = {}) {
  if (!slug) return { terms: [], tone: '' };
  const qs = new URLSearchParams({ slug: String(slug) });
  if (title) qs.set('title', title.slice(0, 120));
  if (originTitle) qs.set('originTitle', originTitle.slice(0, 120));
  if (synopsis) qs.set('synopsis', synopsis.slice(0, 400));
  if (Array.isArray(genres) && genres.length) qs.set('genres', genres.slice(0, 4).join(','));
  if (lang) qs.set('lang', lang);
  if (generate) qs.set('generate', '1');
  if (force) qs.set('force', '1');
  try {
    const res = await fetch(`${getCloudApiBase()}/api/catalog/glossary?${qs.toString()}`);
    if (!res.ok) return { terms: [], tone: '' };
    const data = await res.json().catch(() => ({}));
    return data.glossary || { terms: [], tone: '' };
  } catch {
    return { terms: [], tone: '' };
  }
}

export async function saveMovieGlossary({ slug, terms = [], tone = '' }) {
  return apiFetch('/api/catalog/glossary', {
    method: 'POST',
    auth: false,
    body: { slug, terms, tone },
  });
}

export async function fetchAiVibeSearch(query) {
  const res = await fetch(`${getCloudApiBase()}/api/catalog/ai-search?q=${encodeURIComponent(String(query || '').trim())}`);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `AI Search HTTP ${res.status}`);
  return data;
}
