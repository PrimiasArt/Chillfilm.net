/**
 * ChillFilm Universal Multi-Language AI Subtitle Engine (Web App)
 * Supports OpenSubtitles v3 auto-fetch, local .SRT/.VTT/.ASS upload,
 * Gemini AI cinema translation (ANY language) + free Neural fallback,
 * and Dual-Language rendering with independent size/color/sync controls.
 */

export const SUBTITLE_LANGUAGES = [
  { code: 'vi', badge: 'VI', name: 'Tiếng Việt', native: 'Tiếng Việt', os3: 'vie', prompt: 'natural cinema-quality Vietnamese movie subtitles' },
  { code: 'en', badge: 'EN', name: 'English', native: 'English', os3: 'eng', prompt: 'natural cinema-quality English movie subtitles' },
  { code: 'th', badge: 'TH', name: 'Tiếng Thái', native: 'ภาษาไทย', os3: 'tha', prompt: 'natural cinema-quality Thai movie subtitles (ภาษาไทยธรรมชาติ)' },
  { code: 'zh', badge: 'ZH', name: 'Tiếng Trung', native: '简体中文', os3: 'chi', prompt: 'natural cinema-quality Simplified Chinese subtitles' },
  { code: 'zh-TW', badge: 'TW', name: 'Trung Phồn Thể', native: '繁體中文', os3: 'zht', prompt: 'natural cinema-quality Traditional Chinese subtitles' },
  { code: 'ko', badge: 'KO', name: 'Tiếng Hàn', native: '한국어', os3: 'kor', prompt: 'natural cinema-quality Korean movie subtitles' },
  { code: 'ja', badge: 'JA', name: 'Tiếng Nhật', native: '日本語', os3: 'jpn', prompt: 'natural cinema-quality Japanese movie subtitles' },
  { code: 'fr', badge: 'FR', name: 'Tiếng Pháp', native: 'Français', os3: 'fre', prompt: 'natural cinema-quality French movie subtitles' },
  { code: 'es', badge: 'ES', name: 'Tây Ban Nha', native: 'Español', os3: 'spa', prompt: 'natural cinema-quality Spanish movie subtitles' },
  { code: 'de', badge: 'DE', name: 'Tiếng Đức', native: 'Deutsch', os3: 'ger', prompt: 'natural cinema-quality German movie subtitles' },
  { code: 'id', badge: 'ID', name: 'Indonesian', native: 'Bahasa Indonesia', os3: 'ind', prompt: 'natural cinema-quality Indonesian movie subtitles' },
  { code: 'ru', badge: 'RU', name: 'Tiếng Nga', native: 'Русский', os3: 'rus', prompt: 'natural cinema-quality Russian movie subtitles' },
  { code: 'pt', badge: 'PT', name: 'Bồ Đào Nha', native: 'Português', os3: 'por', prompt: 'natural cinema-quality Portuguese movie subtitles' },
  { code: 'it', badge: 'IT', name: 'Tiếng Ý', native: 'Italiano', os3: 'ita', prompt: 'natural cinema-quality Italian movie subtitles' },
  { code: 'ar', badge: 'AR', name: 'Tiếng Ả Rập', native: 'العربية', os3: 'ara', prompt: 'natural cinema-quality Modern Standard Arabic subtitles' },
  { code: 'hi', badge: 'HI', name: 'Tiếng Hindi', native: 'हिन्दी', os3: 'hin', prompt: 'natural cinema-quality Hindi movie subtitles' },
  { code: 'ms', badge: 'MS', name: 'Mã Lai', native: 'Bahasa Melayu', os3: 'may', prompt: 'natural cinema-quality Malay movie subtitles' },
  { code: 'lo', badge: 'LO', name: 'Tiếng Lào', native: 'ພາສາລາວ', os3: 'lao', prompt: 'natural cinema-quality Lao movie subtitles' },
  { code: 'km', badge: 'KM', name: 'Tiếng Khmer', native: 'ភាសាខ្មែរ', os3: 'khm', prompt: 'natural cinema-quality Khmer movie subtitles' },
];

export function resolveLanguage(codeOrName) {
  const clean = (codeOrName || 'vi').trim();
  const lower = clean.toLowerCase();
  const found = SUBTITLE_LANGUAGES.find(
    (l) => l.code.toLowerCase() === lower || l.os3 === lower || l.name.toLowerCase() === lower
  );
  if (found) return found;
  const badge = clean.replace(/[^A-Za-z]/g, '').slice(0, 3).toUpperCase() || 'AI';
  return {
    code: clean,
    badge,
    name: clean,
    native: clean,
    os3: lower.slice(0, 3),
    prompt: `natural cinema-quality movie subtitles in ${clean}`,
  };
}

const RE_HAN_WEB = /[\u4e00-\u9fff\u3400-\u4dbf]/g;
const RE_KANA_WEB = /[\u3040-\u30ff]/g;
const RE_HANGUL_WEB = /[\uac00-\ud7af]/g;
const RE_LETTERS_WEB = /[\p{L}]/gu;

function countForbiddenScriptWeb(text, langKey) {
  const s = String(text || '');
  if (!s) return { forbidden: 0, letters: 0 };
  const letters = (s.match(RE_LETTERS_WEB) || []).length;
  if (!letters) return { forbidden: 0, letters: 0 };
  const han = (s.match(RE_HAN_WEB) || []).length;
  const kana = (s.match(RE_KANA_WEB) || []).length;
  const hangul = (s.match(RE_HANGUL_WEB) || []).length;
  if (langKey === 'zh' || langKey === 'zh-tw' || langKey === 'zh-cn') {
    return { forbidden: kana + hangul, letters };
  }
  if (langKey === 'ja') return { forbidden: hangul, letters };
  if (langKey === 'ko') return { forbidden: hangul === 0 ? han + kana : kana, letters };
  return { forbidden: han + kana + hangul, letters };
}

export function isSingleLineWrongScript(text, targetLang = 'vi') {
  const key = String(targetLang || 'vi').trim().toLowerCase();
  const { forbidden, letters } = countForbiddenScriptWeb(text, key);
  return forbidden >= 2 && letters > 0 && forbidden / letters >= 0.3;
}

export function hasWrongScriptForLang(cuesOrTexts, targetLang = 'vi') {
  if (!Array.isArray(cuesOrTexts) || !cuesOrTexts.length) return false;
  const key = String(targetLang || 'vi').trim().toLowerCase();
  let nonEmpty = 0;
  let badCues = 0;
  let totalForbidden = 0;
  let totalLetters = 0;
  for (const item of cuesOrTexts) {
    const txt = typeof item === 'string' ? item : String(item?.text ?? item?.t ?? '');
    const { forbidden, letters } = countForbiddenScriptWeb(txt, key);
    if (!letters) continue;
    nonEmpty += 1;
    totalForbidden += forbidden;
    totalLetters += letters;
    if (forbidden >= 2 && forbidden / letters >= 0.3) badCues += 1;
  }
  if (!nonEmpty || !totalLetters) return false;
  if (nonEmpty <= 5) {
    return badCues >= 1 && totalForbidden >= 4 && totalForbidden / totalLetters >= 0.22;
  }
  return badCues / nonEmpty >= 0.18 || totalForbidden / totalLetters >= 0.2;
}

function parseTimestampMs(raw) {
  const clean = raw.trim().replace(',', '.');
  const parts = clean.split(':');
  if (parts.length === 3) {
    return Math.round((Number(parts[0]) * 3600 + Number(parts[1]) * 60 + Number(parts[2])) * 1000);
  }
  if (parts.length === 2) {
    return Math.round((Number(parts[0]) * 60 + Number(parts[1])) * 1000);
  }
  return 0;
}

export function parseSubtitleText(raw) {
  if (!raw) return [];
  const text = raw.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');

  // ASS/SSA Dialogue lines
  if (text.includes('[Events]') && text.includes('Dialogue:')) {
    const cues = [];
    const lines = text.split('\n');
    let id = 1;
    for (const line of lines) {
      if (!line.startsWith('Dialogue:')) continue;
      const cols = line.slice(9).split(',');
      if (cols.length < 10) continue;
      const startMs = parseTimestampMs(cols[1]);
      const endMs = parseTimestampMs(cols[2]);
      const body = cols
        .slice(9)
        .join(',')
        .replace(/\{[^}]*\}/g, '')
        .replace(/\\N|\\n/g, '\n')
        .trim();
      if (body) cues.push({ id: id++, startMs, endMs: Math.max(endMs, startMs + 500), text: body });
    }
    return cues.sort((a, b) => a.startMs - b.startMs);
  }

  // SRT & WebVTT
  const blocks = text.split(/\n{2,}/);
  const cues = [];
  let id = 1;
  for (const block of blocks) {
    const lines = block.split('\n').map((l) => l.trim()).filter(Boolean);
    const timeIdx = lines.findIndex((l) => l.includes('-->'));
    if (timeIdx < 0) continue;
    const [sPart, ePartRaw] = lines[timeIdx].split('-->');
    if (!ePartRaw) continue;
    const ePart = ePartRaw.trim().split(/\s+/)[0];
    const startMs = parseTimestampMs(sPart);
    const endMs = parseTimestampMs(ePart);
    const body = lines
      .slice(timeIdx + 1)
      .join('\n')
      .replace(/<[^>]+>/g, '')
      .trim();
    if (body) cues.push({ id: id++, startMs, endMs: Math.max(endMs, startMs + 500), text: body });
  }
  return cues.sort((a, b) => a.startMs - b.startMs);
}

export function findActiveCueText(cues, effectiveMs) {
  if (!cues || !cues.length) return '';
  let lo = 0;
  let hi = cues.length - 1;
  let hitIdx = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1;
    const c = cues[mid];
    if (effectiveMs < c.startMs) hi = mid - 1;
    else if (effectiveMs > c.endMs) lo = mid + 1;
    else {
      hitIdx = mid;
      break;
    }
  }
  if (hitIdx >= 0) return cues[hitIdx].text;

  // Overlap-safe neighborhood check around insertion point (for slight overlapping AI timestamps)
  const center = Math.max(0, Math.min(cues.length - 1, lo));
  const start = Math.max(0, center - 4);
  const end = Math.min(cues.length - 1, center + 4);
  for (let i = end; i >= start; i--) {
    const c = cues[i];
    if (effectiveMs >= c.startMs && effectiveMs <= c.endMs) {
      return c.text;
    }
  }
  return '';
}

export async function fetchOpenSubtitlesV3(imdbId, isSeries, season = 1, episode = 1) {
  if (!imdbId || !imdbId.startsWith('tt')) return [];
  const endpoint = isSeries
    ? `https://opensubtitles-v3.strem.io/subtitles/series/${imdbId}:${season}:${episode}.json`
    : `https://opensubtitles-v3.strem.io/subtitles/movie/${imdbId}.json`;
  try {
    const res = await fetch(endpoint);
    if (!res.ok) return [];
    const data = await res.json();
    const list = Array.isArray(data.subtitles) ? data.subtitles : [];
    return list
      .filter((s) => s && s.url && s.lang)
      .slice(0, 24)
      .map((s, idx) => {
        const langObj = resolveLanguage(s.lang);
        return {
          id: s.id || `osv3_${idx}`,
          langCode: langObj.code,
          label: `${langObj.native} (OpenSubtitles #${idx + 1})`,
          url: s.url,
          proxyUrl: `/api/catalog/subproxy?url=${encodeURIComponent(s.url)}`,
        };
      });
  } catch {
    return [];
  }
}

export async function translateCueBatch({
  cues,
  targetLang,
  geminiApiKey,
  geminiModel,
  cloudTranslator = null,
  movieContext = null,
  onEngine = null,
}) {
  if (!cues.length) return cues;
  const langSpec = resolveLanguage(targetLang);

  // 1. Centralized Secure NKI Cloud AI Gateway (uses server-side GEMINI_API_KEY — zero key exposed on client!)
  if (typeof cloudTranslator === 'function') {
    try {
      const cloudTranslated = await cloudTranslator({
        cues,
        targetLang: langSpec.code,
        model: geminiModel || 'gemini-3-flash-preview',
        movieContext,
      });
      if (cloudTranslated && cloudTranslated.length === cues.length && !hasWrongScriptForLang(cloudTranslated, langSpec.code)) {
        onEngine?.('cloud-gemini');
        return cloudTranslated;
      }
    } catch (e) {
      console.warn('Cloud AI proxy fallback:', e);
    }
  }

  // 2. Optional Personal Local Gemini API Key override (only if user explicitly entered one)
  if (geminiApiKey && geminiApiKey.trim().length > 10) {
    try {
      const translated = await translateWithGemini(cues, langSpec, geminiApiKey.trim(), geminiModel || 'gemini-3-flash-preview', movieContext);
      if (translated && translated.length === cues.length && !hasWrongScriptForLang(translated, langSpec.code)) {
        onEngine?.('local-gemini');
        return translated;
      }
    } catch (e) {
      console.warn('Gemini batch fallback to free neural:', e);
    }
  }

  // 3. Fast Free Neural Translation fallback (works out-of-the-box with zero API key!)
  onEngine?.('neural-free');
  return translateWithGoogleFree(cues, langSpec.code);
}

export async function transcribeAudioChunkDirect({
  audioBase64,
  mimeType = 'audio/webm',
  chunkStartOffsetMs = 0,
  chunkDurationMs = 18000,
  targetLang = 'vi',
  apiKey = '',
  modelId = 'gemini-3-flash-preview',
  movieContext = null,
}) {
  if (!apiKey || apiKey.trim().length < 15 || !audioBase64) return [];
  const langSpec = resolveLanguage(targetLang);
  const cleanModel = (modelId || 'gemini-3-flash-preview').replace(/^models\//, '');
  const durationSec = (Number(chunkDurationMs || 18000) / 1000).toFixed(1);
  const ctxHint = movieContext?.title
    ? `Movie context: "${movieContext.title}"${movieContext.genres?.length ? ` [${movieContext.genres.join(', ')}]` : ''} — adapt tone and pronouns naturally.\n`
    : '';
  const systemText = `You are a professional movie subtitle translator. Listen to spoken dialogue in ANY language and output subtitles WRITTEN STRICTLY IN ${langSpec.name} (${langSpec.native}). NEVER transcribe or output in the original spoken language or Chinese characters unless the target language itself is Chinese.`;
  const prompt = `Listen to this ${durationSec}-second movie audio clip (starts at 0.0s).\nTRANSLATE ALL spoken dialogue directly into ${langSpec.prompt}.\nCRITICAL RULE: Every "t" MUST be 100% in ${langSpec.name} (${langSpec.native}). NEVER output the original spoken language (e.g. NEVER output Chinese characters when translating into Vietnamese).\n${ctxHint}Rules:\n- "s" and "e" = float seconds relative to 0.0 (0.0 <= s < e <= ${durationSec}), 1.2s-4.8s per cue.\n- Skip music/silence; if no speech, return [].\nReturn ONLY JSON array: [{"s":1.2,"e":3.6,"t":"subtitle in ${langSpec.name}"}]`;

  const genConfig = { temperature: 0.15, responseMimeType: 'application/json' };
  const lower = cleanModel.toLowerCase();
  if (lower.startsWith('gemini-3') && lower.includes('flash')) genConfig.thinkingConfig = { thinkingLevel: 'MINIMAL' };
  else if (lower.startsWith('gemini-2.5') && lower.includes('flash')) genConfig.thinkingConfig = { thinkingBudget: 0 };

  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(cleanModel)}:generateContent?key=${encodeURIComponent(apiKey.trim())}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: systemText }] },
      contents: [
        {
          parts: [
            { inline_data: { mime_type: mimeType, data: audioBase64 } },
            { text: prompt },
          ],
        },
      ],
      generationConfig: genConfig,
    }),
  });
  if (!res.ok) throw new Error(`Gemini Audio HTTP ${res.status}`);
  const json = await res.json();
  const parts = json?.candidates?.[0]?.content?.parts || [];
  let raw = '';
  for (const p of parts) {
    if (!p.thought && p.text) raw = p.text.trim();
  }
  const s = raw.indexOf('[');
  const e = raw.lastIndexOf(']');
  if (s < 0 || e <= s) return [];
  const arr = JSON.parse(raw.slice(s, e + 1));
  const out = [];
  for (let i = 0; i < arr.length; i++) {
    const o = arr[i];
    const txt = String(o?.t || o?.text || '').trim();
    const sSec = Number(o?.s ?? o?.start);
    if (!txt || !Number.isFinite(sSec)) continue;
    const eSec = Number.isFinite(Number(o?.e ?? o?.end)) ? Number(o?.e ?? o?.end) : sSec + 2.5;
    const relStart = Math.max(0, Math.min(chunkDurationMs + 4000, Math.round(sSec * 1000)));
    const relEnd = Math.max(relStart + 500, Math.min(chunkDurationMs + 5000, Math.round(eSec * 1000)));
    out.push({ id: i + 1, startMs: chunkStartOffsetMs + relStart, endMs: chunkStartOffsetMs + relEnd, text: txt });
  }
  // Safety net: if direct Gemini audio slipped into wrong script (e.g. Chinese Hanzi for Vietnamese), repair via text translation!
  if (out.length && (hasWrongScriptForLang(out, langSpec.code) || out.some((c) => isSingleLineWrongScript(c.text, langSpec.code)))) {
    try {
      const repaired = await translateWithGemini(out, langSpec, apiKey.trim(), cleanModel, movieContext);
      if (repaired.length === out.length && !hasWrongScriptForLang(repaired, langSpec.code)) {
        return repaired;
      }
      return await translateWithGoogleFree(out, langSpec.code);
    } catch {
      return await translateWithGoogleFree(out, langSpec.code);
    }
  }
  return out;
}

/* ==================== PERSISTENT TRANSLATED SUBTITLE CACHE (INDEXEDDB — ATOMIC MONOTONIC MERGE) ==================== */
const SUB_DB_NAME = 'chillfilm_nki_sub_cache_v1';
const SUB_STORE_NAME = 'episode_subs';
const MAX_CACHED_EPISODES = 160;
let subDbPromise = null;

function canonicalSubSlug(raw) {
  const s = String(raw || 'unknown')
    .trim()
    .toLowerCase()
    .replace(/^(modern|ophim|kkphim|phimapi|curated|custom)_+/i, '')
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return (s || 'unknown').slice(0, 120);
}

function canonicalSubEpKey(episode) {
  const raw = String(episode?.name || episode?.slug || '').trim();
  const ascii = raw
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

export function mergeTimeRanges(ranges) {
  if (!Array.isArray(ranges) || !ranges.length) return [];
  const pairs = [];
  for (const r of ranges) {
    if (Array.isArray(r) && r.length >= 2) {
      const s = Math.max(0, Math.round(Number(r[0]) || 0));
      const e = Math.max(0, Math.round(Number(r[1]) || 0));
      if (e > s) pairs.push([s, e]);
    } else if (r && typeof r === 'object') {
      const s = Math.max(0, Math.round(Number(r.startMs ?? r.s ?? 0) || 0));
      const e = Math.max(0, Math.round(Number(r.endMs ?? r.e ?? 0) || 0));
      if (e > s) pairs.push([s, e]);
    }
  }
  if (!pairs.length) return [];
  pairs.sort((a, b) => a[0] - b[0]);
  const out = [pairs[0]];
  for (let i = 1; i < pairs.length; i++) {
    const prev = out[out.length - 1];
    if (pairs[i][0] <= prev[1] + 2500) {
      prev[1] = Math.max(prev[1], pairs[i][1]);
    } else {
      out.push(pairs[i]);
    }
  }
  return out.slice(0, 400);
}

function mergeCuesMonotonicLocal(a, b) {
  const combined = [...(a || []), ...(b || [])]
    .filter((c) => c && c.text && Number.isFinite(c.startMs))
    .sort((x, y) => x.startMs - y.startMs);
  const out = [];
  for (const c of combined) {
    const prev = out[out.length - 1];
    if (prev && Math.abs(prev.startMs - c.startMs) < 900 && prev.text === c.text) continue;
    out.push({
      id: out.length + 1,
      startMs: Math.max(0, Math.round(c.startMs)),
      endMs: Math.max(Math.round(c.endMs || c.startMs + 2500), Math.round(c.startMs) + 450),
      text: String(c.text).trim(),
    });
  }
  return out;
}

export function buildSubCacheKey(movieSlug, episode, primaryLangCode) {
  const m = canonicalSubSlug(movieSlug);
  const epName = canonicalSubEpKey(episode);
  const urlPart = (m === 'direct' || m === 'local')
    ? String(episode?.link_m3u8 || '').split('?')[0].trim().toLowerCase()
    : '';
  const lang = String(primaryLangCode || 'vi').trim().toLowerCase();
  return `${m}|${epName}|${urlPart}|${lang}`;
}

export function buildLegacySubCacheKey(movieSlug, episode, primaryLangCode) {
  const m = String(movieSlug || 'unknown').trim().toLowerCase();
  const epName = String(episode?.slug || episode?.name || '').trim().toLowerCase();
  const urlPart = (m === 'direct' || m === 'local')
    ? String(episode?.link_m3u8 || '').split('?')[0].trim().toLowerCase()
    : '';
  const lang = String(primaryLangCode || 'vi').trim().toLowerCase();
  return `${m}|${epName}|${urlPart}|${lang}`;
}

function openSubCacheDb() {
  if (subDbPromise) return subDbPromise;
  if (typeof indexedDB === 'undefined') return Promise.resolve(null);
  subDbPromise = new Promise((resolve) => {
    try {
      const req = indexedDB.open(SUB_DB_NAME, 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(SUB_STORE_NAME)) {
          const store = db.createObjectStore(SUB_STORE_NAME, { keyPath: 'key' });
          store.createIndex('savedAt', 'savedAt', { unique: false });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
  return subDbPromise;
}

export async function deleteCachedEpisodeSub(cacheKey) {
  if (!cacheKey) return;
  try {
    const db = await openSubCacheDb();
    if (db) {
      await new Promise((resolve) => {
        try {
          const tx = db.transaction(SUB_STORE_NAME, 'readwrite');
          tx.objectStore(SUB_STORE_NAME).delete(cacheKey);
          tx.oncomplete = () => resolve(true);
          tx.onerror = () => resolve(false);
        } catch {
          resolve(false);
        }
      });
    }
  } catch { /* ignore */ }
  try { localStorage.removeItem(`cf_sub_${cacheKey}`); } catch { /* ignore */ }
}

export async function loadCachedEpisodeSub(cacheKey, fallbackKey = '') {
  if (!cacheKey) return null;
  const keysToTry = fallbackKey && fallbackKey !== cacheKey ? [cacheKey, fallbackKey] : [cacheKey];
  for (const k of keysToTry) {
    const keyLang = String(k).split('|').pop() || 'vi';
    const validateEntry = (entry) => {
      if (!entry || !Array.isArray(entry.primaryCues) || entry.primaryCues.length < 3) return null;
      const targetLang = entry.primaryLangCode || keyLang;
      if (hasWrongScriptForLang(entry.primaryCues, targetLang)) {
        deleteCachedEpisodeSub(k).catch(() => {});
        return null;
      }
      return {
        ...entry,
        audioMode: Boolean(entry.audioMode || entry.isAiAudio),
        translatedWindows: Array.isArray(entry.translatedWindows)
          ? entry.translatedWindows
          : Array.isArray(entry.completedAudioSlots)
            ? entry.completedAudioSlots
            : [],
        translatedRanges: mergeTimeRanges(entry.translatedRanges),
      };
    };
    try {
      const db = await openSubCacheDb();
      if (db) {
        const entry = await new Promise((resolve) => {
          try {
            const tx = db.transaction(SUB_STORE_NAME, 'readonly');
            const req = tx.objectStore(SUB_STORE_NAME).get(k);
            req.onsuccess = () => resolve(req.result || null);
            req.onerror = () => resolve(null);
          } catch {
            resolve(null);
          }
        });
        const ok = validateEntry(entry);
        if (ok) return ok;
      }
    } catch {
      // Fall through to localStorage fallback
    }

    try {
      const raw = localStorage.getItem(`cf_sub_${k}`);
      if (raw) {
        const parsed = JSON.parse(raw);
        const ok = validateEntry(parsed);
        if (ok) return ok;
      }
    } catch {
      // Ignore
    }
  }
  return null;
}

export async function saveCachedEpisodeSub(cacheKey, payload, { forceOverwrite = false } = {}) {
  if (!cacheKey || !payload || !Array.isArray(payload.primaryCues) || payload.primaryCues.length < 3) {
    return false;
  }
  const targetLang = payload.primaryLangCode || String(cacheKey).split('|').pop() || 'vi';
  if (hasWrongScriptForLang(payload.primaryCues, targetLang)) {
    return false;
  }

  const buildMergedRecord = (existing) => {
    const canMerge = Boolean(
      !forceOverwrite
      && existing
      && Array.isArray(existing.primaryCues)
      && existing.primaryCues.length >= 3
      && !hasWrongScriptForLang(existing.primaryCues, targetLang)
    );
    const incomingAudio = Boolean(payload.audioMode || payload.isAiAudio);
    const existingAudio = Boolean(existing?.audioMode || existing?.isAiAudio);

    if (canMerge && (incomingAudio || existingAudio)) {
      const mergedPrimary = mergeCuesMonotonicLocal(existing.primaryCues, payload.primaryCues);
      const mergedSource = payload.sourceCues?.length
        ? mergeCuesMonotonicLocal(existing.sourceCues || existing.primaryCues, payload.sourceCues)
        : mergedPrimary;
      const mergedWins = [...new Set([
        ...(Array.isArray(existing.translatedWindows) ? existing.translatedWindows : []),
        ...(Array.isArray(payload.translatedWindows) ? payload.translatedWindows : []),
      ])];
      const mergedRanges = mergeTimeRanges([
        ...(Array.isArray(existing.translatedRanges) ? existing.translatedRanges : []),
        ...(Array.isArray(payload.translatedRanges) ? payload.translatedRanges : []),
      ]);
      const maxRangeEnd = mergedRanges.length ? mergedRanges[mergedRanges.length - 1][1] : 0;
      const maxCueEnd = mergedPrimary.length ? (mergedPrimary[mergedPrimary.length - 1].endMs || 0) : 0;
      return {
        key: cacheKey,
        primaryLangCode: targetLang,
        secondaryLangCode: payload.secondaryLangCode || existing.secondaryLangCode || '',
        sourceLangCode: payload.sourceLangCode || existing.sourceLangCode || 'auto',
        trackLabel: payload.trackLabel || existing.trackLabel || 'Đã lưu',
        sourceCues: mergedSource,
        primaryCues: mergedPrimary,
        secondaryCues: payload.secondaryCues?.length ? payload.secondaryCues : (existing.secondaryCues || []),
        isFullyTranslated: Boolean(existing.isFullyTranslated || payload.isFullyTranslated),
        translatedCount: Math.max(Number(existing.translatedCount || 0), Number(payload.translatedCount || 0), mergedPrimary.length),
        audioMode: true,
        audioTranslatedUntilMs: Math.max(
          Number(existing.audioTranslatedUntilMs || 0),
          Number(payload.audioTranslatedUntilMs || 0),
          maxRangeEnd,
          maxCueEnd
        ),
        totalDurationMs: Math.max(Number(existing.totalDurationMs || 0), Number(payload.totalDurationMs || 0)),
        translatedWindows: mergedWins,
        translatedRanges: mergedRanges,
        savedAt: Date.now(),
      };
    }

    // For non-audio SRT cache, never overwrite a complete cache with an incomplete/smaller one
    if (canMerge && existing.isFullyTranslated && !payload.isFullyTranslated && payload.primaryCues.length <= existing.primaryCues.length) {
      return { ...existing, savedAt: Date.now() };
    }

    const ranges = mergeTimeRanges(payload.translatedRanges);
    return {
      key: cacheKey,
      primaryLangCode: targetLang,
      secondaryLangCode: payload.secondaryLangCode || '',
      sourceLangCode: payload.sourceLangCode || 'auto',
      trackLabel: payload.trackLabel || 'Đã lưu',
      sourceCues: payload.sourceCues || payload.primaryCues,
      primaryCues: payload.primaryCues,
      secondaryCues: payload.secondaryCues || [],
      isFullyTranslated: Boolean(payload.isFullyTranslated),
      translatedCount: Number(payload.translatedCount || payload.primaryCues.length),
      audioMode: incomingAudio,
      audioTranslatedUntilMs: Number(payload.audioTranslatedUntilMs || 0),
      totalDurationMs: Number(payload.totalDurationMs || 0),
      translatedWindows: Array.isArray(payload.translatedWindows) ? payload.translatedWindows : [],
      translatedRanges: ranges,
      savedAt: Date.now(),
    };
  };

  try {
    const db = await openSubCacheDb();
    if (db) {
      await new Promise((resolve) => {
        try {
          const tx = db.transaction(SUB_STORE_NAME, 'readwrite');
          const store = tx.objectStore(SUB_STORE_NAME);
          const getReq = store.get(cacheKey);
          getReq.onsuccess = () => {
            const record = buildMergedRecord(getReq.result || null);
            store.put(record);
            // Prune oldest records if cache exceeds MAX_CACHED_EPISODES
            const countReq = store.count();
            countReq.onsuccess = () => {
              if (countReq.result > MAX_CACHED_EPISODES) {
                const idx = store.index('savedAt');
                let toDelete = countReq.result - MAX_CACHED_EPISODES;
                idx.openCursor().onsuccess = (ev) => {
                  const cursor = ev.target.result;
                  if (cursor && toDelete > 0) {
                    cursor.delete();
                    toDelete--;
                    cursor.continue();
                  }
                };
              }
            };
          };
          tx.oncomplete = () => resolve(true);
          tx.onerror = () => resolve(false);
        } catch {
          resolve(false);
        }
      });
      return true;
    }
  } catch {
    // Fall back to localStorage below
  }

  try {
    let existing = null;
    try { existing = JSON.parse(localStorage.getItem(`cf_sub_${cacheKey}`) || 'null'); } catch {}
    const record = buildMergedRecord(existing);
    localStorage.setItem(`cf_sub_${cacheKey}`, JSON.stringify(record));
    return true;
  } catch {
    return false;
  }
}

async function translateWithGemini(cues, langSpec, apiKey, modelId, movieContext = null) {
  const cleanModel = modelId.replace(/^models\//, '') || 'gemini-3-flash-preview';
  const inputPayload = cues.map((c, idx) => ({ i: idx, t: c.text.replace(/\n/g, ' ') }));
  const ctxHint = movieContext?.title
    ? `Movie context: "${movieContext.title}"${movieContext.genres?.length ? ` [${movieContext.genres.join(', ')}]` : ''} — adapt pronouns & tone naturally.\n`
    : '';
  const systemText = `You are a professional cinema subtitle translator. EVERY translated line MUST be written strictly in ${langSpec.name} (${langSpec.native}). Never leave source text untranslated or output Chinese characters unless target language is Chinese.`;
  const prompt = `Translate EVERY line into ${langSpec.prompt}.\nSTRICT RULE: Output MUST be 100% in ${langSpec.name} (${langSpec.native}).\n${ctxHint}Keep concise, natural, idiomatic.\nReturn ONLY JSON array: [{"i":0,"t":"..."}]\nInput:\n${JSON.stringify(inputPayload)}`;

  const genConfig = { temperature: 0.15, responseMimeType: 'application/json' };
  const lower = cleanModel.toLowerCase();
  if (lower.startsWith('gemini-3') && lower.includes('flash')) {
    genConfig.thinkingConfig = { thinkingLevel: 'MINIMAL' };
  } else if (lower.startsWith('gemini-2.5') && lower.includes('flash')) {
    genConfig.thinkingConfig = { thinkingBudget: 0 };
  }

  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(cleanModel)}:generateContent?key=${encodeURIComponent(apiKey)}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: systemText }] },
      contents: [{ parts: [{ text: prompt }] }],
      generationConfig: genConfig,
    }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  const parts = json?.candidates?.[0]?.content?.parts || [];
  let raw = '';
  for (const p of parts) {
    if (!p.thought && p.text) raw = p.text.trim();
  }
  const s = raw.indexOf('[');
  const e = raw.lastIndexOf(']');
  if (s < 0 || e <= s) throw new Error('Invalid JSON');
  const arr = JSON.parse(raw.slice(s, e + 1));
  const map = new Map();
  arr.forEach((o, idx) => map.set(typeof o.i === 'number' ? o.i : idx, o.t));
  return cues.map((c, idx) => ({ ...c, text: (map.get(idx) || c.text).trim() }));
}

async function translateGoogleFreeSlice(cues, tl) {
  if (!cues.length) return [];
  const delim = '\n[[[C]]]\n';
  const joined = cues.map((c) => c.text.replace(/\r?\n/g, ' ').trim()).join(delim);
  try {
    const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${encodeURIComponent(tl)}&dt=t&q=${encodeURIComponent(joined)}`;
    const res = await fetch(url);
    if (!res.ok) return cues;
    const data = await res.json();
    const full = (data[0] || []).map((seg) => seg[0] || '').join('');
    const split = full.split(/\[\s*\[\s*\[\s*C\s*\]\s*\]\s*\]/i);
    if (split.length === cues.length) {
      return cues.map((c, i) => ({ ...c, text: split[i].trim() || c.text }));
    }
    // If delimiter got altered on a larger slice, bisect recursively so lines still translate!
    if (cues.length > 4) {
      const mid = Math.floor(cues.length / 2);
      const [left, right] = await Promise.all([
        translateGoogleFreeSlice(cues.slice(0, mid), tl),
        translateGoogleFreeSlice(cues.slice(mid), tl),
      ]);
      return [...left, ...right];
    }
    // Tiny slice fallback: translate individually in parallel
    return Promise.all(
      cues.map(async (c) => {
        try {
          const u = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${encodeURIComponent(tl)}&dt=t&q=${encodeURIComponent(c.text.replace(/\r?\n/g, ' '))}`;
          const r = await fetch(u);
          if (!r.ok) return c;
          const d = await r.json();
          const txt = (d[0] || []).map((seg) => seg[0] || '').join('').trim();
          return { ...c, text: txt || c.text };
        } catch {
          return c;
        }
      })
    );
  } catch {
    return cues;
  }
}

async function translateWithGoogleFree(cues, targetLang) {
  const tl = targetLang === 'zh' ? 'zh-CN' : targetLang;
  // Pack into safe character-budget sub-slices (<= 1400 raw chars) so GET URL never hits length limits
  const slices = [];
  let cur = [];
  let curLen = 0;
  for (const c of cues) {
    const len = String(c.text || '').length + 12;
    if (cur.length >= 18 || (cur.length > 0 && curLen + len > 1400)) {
      slices.push(cur);
      cur = [];
      curLen = 0;
    }
    cur.push(c);
    curLen += len;
  }
  if (cur.length) slices.push(cur);

  const results = await Promise.all(slices.map((s) => translateGoogleFreeSlice(s, tl)));
  return results.flat();
}

/* ==================== PURE-JS HLS LOOKAHEAD AUDIO EXTRACTOR (MPEG-TS -> ADTS AAC) ==================== */
export function uint8ToBase64(u8) {
  let binary = '';
  const CHUNK = 0x8000;
  for (let i = 0; i < u8.length; i += CHUNK) {
    binary += String.fromCharCode.apply(null, u8.subarray(i, i + CHUNK));
  }
  return btoa(binary);
}

function parseExtXKeyAttrs(line, baseUrl) {
  const methodMatch = line.match(/METHOD=([^,\s]+)/i);
  const method = methodMatch ? methodMatch[1].toUpperCase() : 'NONE';
  if (method === 'NONE') return null;
  const uriMatch = line.match(/URI="([^"]+)"/i);
  const ivMatch = line.match(/IV=0x([0-9a-fA-F]+)/i);
  if (method !== 'AES-128' || !uriMatch) return null;
  return {
    method: 'AES-128',
    uri: new URL(uriMatch[1], baseUrl).href,
    ivHex: ivMatch ? ivMatch[1] : '',
  };
}

/**
 * Fetches an HLS (.m3u8) master or media playlist, selects the lowest-bandwidth variant
 * (fastest segment downloads with identical audio!), and returns time-indexed segments.
 */
export async function parseHlsMediaSegments(m3u8Url, signal = undefined) {
  if (!m3u8Url || !m3u8Url.includes('.m3u8')) {
    throw new Error('Not an HLS (.m3u8) stream');
  }
  const fetchText = async (url) => {
    const res = await fetch(url, { signal });
    if (!res.ok) throw new Error(`HLS playlist HTTP ${res.status}`);
    return res.text();
  };

  let mediaUrl = m3u8Url;
  let text = await fetchText(mediaUrl);

  // Follow master playlist -> pick lowest-bandwidth variant for 3x faster audio extraction
  if (text.includes('#EXT-X-STREAM-INF')) {
    const lines = text.split(/\r?\n/).map((l) => l.trim());
    const variants = [];
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].startsWith('#EXT-X-STREAM-INF')) {
        const bwMatch = lines[i].match(/BANDWIDTH=(\d+)/i);
        const bw = bwMatch ? Number(bwMatch[1]) : 99999999;
        for (let j = i + 1; j < lines.length; j++) {
          if (lines[j] && !lines[j].startsWith('#')) {
            variants.push({ bw, url: new URL(lines[j], mediaUrl).href });
            break;
          }
        }
      }
    }
    if (variants.length) {
      variants.sort((a, b) => a.bw - b.bw);
      mediaUrl = variants[0].url;
      text = await fetchText(mediaUrl);
    }
  }

  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  let mediaSeq = 0;
  let curKey = null;
  let pendingDurSec = 0;
  let accMs = 0;
  const segments = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.startsWith('#EXT-X-MEDIA-SEQUENCE:')) {
      mediaSeq = Number(line.split(':')[1]) || 0;
    } else if (line.startsWith('#EXT-X-KEY:')) {
      curKey = parseExtXKeyAttrs(line, mediaUrl);
    } else if (line.startsWith('#EXTINF:')) {
      const num = parseFloat(line.slice(8).split(',')[0]);
      pendingDurSec = Number.isFinite(num) && num > 0 ? num : 4;
    } else if (!line.startsWith('#')) {
      const durMs = Math.round((pendingDurSec || 4) * 1000);
      segments.push({
        index: segments.length,
        seq: mediaSeq + segments.length,
        url: new URL(line, mediaUrl).href,
        startMs: accMs,
        durationMs: durMs,
        endMs: accMs + durMs,
        key: curKey,
      });
      accMs += durMs;
      pendingDurSec = 0;
    }
  }

  if (!segments.length) {
    throw new Error('HLS playlist contains no media segments');
  }
  return { mediaUrl, segments, totalDurationMs: accMs };
}

/**
 * Pure-JS 188-byte MPEG-TS -> raw ADTS AAC audio demuxer (PAT -> PMT -> AAC ES PID -> strip PES).
 * Runs in < 5ms per segment in every browser with zero WASM/FFmpeg dependencies!
 */
export function extractAdtsFromTs(u8) {
  if (!u8 || u8.length < 188) return new Uint8Array(0);
  let pmtPid = -1;
  let audioPid = -1;

  for (let off = 0; off + 188 <= u8.length; off += 188) {
    if (u8[off] !== 0x47) {
      const nextSync = u8.indexOf(0x47, off);
      if (nextSync < 0) break;
      off = nextSync - 188;
      continue;
    }
    const pusi = (u8[off + 1] & 0x40) !== 0;
    const pid = ((u8[off + 1] & 0x1f) << 8) | u8[off + 2];
    const afc = (u8[off + 3] >> 4) & 0x03;
    if ((afc & 1) === 0) continue;
    let p = off + 4;
    if ((afc & 2) !== 0) p += 1 + u8[p];
    if (p >= off + 188) continue;

    if (pid === 0 && pusi && pmtPid < 0) {
      const ptr = u8[p];
      const sec = p + 1 + ptr;
      if (sec + 8 < off + 188) {
        const secLen = ((u8[sec + 1] & 0x0f) << 8) | u8[sec + 2];
        const end = Math.min(off + 188, sec + 3 + secLen - 4);
        for (let i = sec + 8; i + 4 <= end; i += 4) {
          const progNum = (u8[i] << 8) | u8[i + 1];
          const mapPid = ((u8[i + 2] & 0x1f) << 8) | u8[i + 3];
          if (progNum !== 0) {
            pmtPid = mapPid;
            break;
          }
        }
      }
    } else if (pid === pmtPid && pusi && audioPid < 0) {
      const ptr = u8[p];
      const sec = p + 1 + ptr;
      if (sec + 12 < off + 188) {
        const secLen = ((u8[sec + 1] & 0x0f) << 8) | u8[sec + 2];
        const progInfoLen = ((u8[sec + 10] & 0x0f) << 8) | u8[sec + 11];
        const end = Math.min(off + 188, sec + 3 + secLen - 4);
        let i = sec + 12 + progInfoLen;
        while (i + 5 <= end) {
          const streamType = u8[i];
          const esPid = ((u8[i + 1] & 0x1f) << 8) | u8[i + 2];
          const esInfoLen = ((u8[i + 3] & 0x0f) << 8) | u8[i + 4];
          // 0x0f = ADTS AAC, 0x11 = LATM AAC, 0x03/0x04 = MP3 audio
          if (streamType === 0x0f || streamType === 0x11 || streamType === 0x03 || streamType === 0x04) {
            audioPid = esPid;
            break;
          }
          i += 5 + esInfoLen;
        }
      }
    }
    if (audioPid >= 0) break;
  }

  // Fallback: detect PID carrying PES audio stream_id 0xC0..0xDF if PAT/PMT was absent
  if (audioPid < 0) {
    for (let off = 0; off + 188 <= u8.length; off += 188) {
      if (u8[off] !== 0x47) continue;
      const pusi = (u8[off + 1] & 0x40) !== 0;
      if (!pusi) continue;
      const afc = (u8[off + 3] >> 4) & 0x03;
      if ((afc & 1) === 0) continue;
      let p = off + 4;
      if ((afc & 2) !== 0) p += 1 + u8[p];
      if (p + 4 <= off + 188 && u8[p] === 0 && u8[p + 1] === 0 && u8[p + 2] === 1 && u8[p + 3] >= 0xc0 && u8[p + 3] <= 0xdf) {
        audioPid = ((u8[off + 1] & 0x1f) << 8) | u8[off + 2];
        break;
      }
    }
  }
  if (audioPid < 0) return new Uint8Array(0);

  const chunks = [];
  let total = 0;
  for (let off = 0; off + 188 <= u8.length; off += 188) {
    if (u8[off] !== 0x47) continue;
    const pid = ((u8[off + 1] & 0x1f) << 8) | u8[off + 2];
    if (pid !== audioPid) continue;
    const pusi = (u8[off + 1] & 0x40) !== 0;
    const afc = (u8[off + 3] >> 4) & 0x03;
    if ((afc & 1) === 0) continue;
    let p = off + 4;
    if ((afc & 2) !== 0) p += 1 + u8[p];
    if (pusi && p + 9 <= off + 188 && u8[p] === 0 && u8[p + 1] === 0 && u8[p + 2] === 1) {
      const pesHdrLen = u8[p + 8];
      p += 9 + pesHdrLen;
    }
    if (p < off + 188) {
      const slice = u8.subarray(p, off + 188);
      chunks.push(slice);
      total += slice.length;
    }
  }

  const out = new Uint8Array(total);
  let pos = 0;
  for (const c of chunks) {
    out.set(c, pos);
    pos += c.length;
  }
  return out;
}

async function decryptHlsSegmentIfNeeded(buf, keyInfo, seqNum, keyCache, signal) {
  if (!keyInfo || keyInfo.method !== 'AES-128' || !keyInfo.uri || !globalThis.crypto?.subtle) {
    return new Uint8Array(buf);
  }
  let cryptoKey = keyCache.get(keyInfo.uri);
  if (!cryptoKey) {
    const res = await fetch(keyInfo.uri, { signal });
    if (!res.ok) throw new Error(`HLS key HTTP ${res.status}`);
    const rawKey = await res.arrayBuffer();
    cryptoKey = await crypto.subtle.importKey('raw', rawKey, { name: 'AES-CBC' }, false, ['decrypt']);
    keyCache.set(keyInfo.uri, cryptoKey);
  }
  const iv = new Uint8Array(16);
  if (keyInfo.ivHex) {
    const padded = keyInfo.ivHex.padStart(32, '0').slice(-32);
    for (let i = 0; i < 16; i++) iv[i] = parseInt(padded.slice(i * 2, i * 2 + 2), 16);
  } else {
    const dv = new DataView(iv.buffer);
    dv.setUint32(12, seqNum >>> 0, false);
  }
  const plain = await crypto.subtle.decrypt({ name: 'AES-CBC', iv }, cryptoKey, buf);
  return new Uint8Array(plain);
}

/**
 * Packs HLS media segments into clean ~45-second audio windows for ultra-fast
 * parallel lookahead transcription (10 minutes ahead = ~13 windows!).
 */
export function buildHlsAudioWindows(segments, targetWindowSec = 45) {
  const targetMs = Math.max(20, Math.min(60, targetWindowSec)) * 1000;
  const windows = [];
  let curSegs = [];
  let curDur = 0;

  for (const seg of segments) {
    if (curSegs.length > 0 && curDur + seg.durationMs > targetMs + 6000) {
      windows.push({
        index: windows.length,
        startMs: curSegs[0].startMs,
        endMs: curSegs[curSegs.length - 1].endMs,
        durationMs: curSegs[curSegs.length - 1].endMs - curSegs[0].startMs,
        segments: curSegs,
      });
      curSegs = [];
      curDur = 0;
    }
    curSegs.push(seg);
    curDur += seg.durationMs;
  }
  if (curSegs.length > 0) {
    windows.push({
      index: windows.length,
      startMs: curSegs[0].startMs,
      endMs: curSegs[curSegs.length - 1].endMs,
      durationMs: curSegs[curSegs.length - 1].endMs - curSegs[0].startMs,
      segments: curSegs,
    });
  }
  return windows;
}

/**
 * Downloads all .ts segments of a 45s HLS window (4 concurrent fetches), decrypts if needed,
 * demuxes raw ADTS AAC frames, and returns base64 + mimeType ready for Gemini /api/ai/audio-sub.
 */
export async function extractHlsWindowAdtsBase64(win, keyCache = new Map(), signal = undefined) {
  const segs = win?.segments || [];
  if (!segs.length) return { audioBase64: '', mimeType: 'audio/aac', byteLength: 0 };

  const outParts = new Array(segs.length);
  let cursor = 0;
  const workerCount = Math.min(4, segs.length);

  await Promise.all(
    Array.from({ length: workerCount }, async () => {
      while (cursor < segs.length) {
        const idx = cursor++;
        const seg = segs[idx];
        const res = await fetch(seg.url, { signal });
        if (!res.ok) throw new Error(`TS segment HTTP ${res.status}`);
        const ab = await res.arrayBuffer();
        const plain = await decryptHlsSegmentIfNeeded(ab, seg.key, seg.seq, keyCache, signal);
        // Handle both standard MPEG-TS (0x47 sync) and raw ADTS AAC segments (0xFF 0xF*)
        if (plain.length > 7 && plain[0] === 0xff && (plain[1] & 0xf0) === 0xf0) {
          outParts[idx] = plain;
        } else {
          outParts[idx] = extractAdtsFromTs(plain);
        }
      }
    })
  );

  let totalBytes = 0;
  for (const p of outParts) if (p) totalBytes += p.length;
  if (totalBytes < 512) {
    return { audioBase64: '', mimeType: 'audio/aac', byteLength: 0 };
  }
  const merged = new Uint8Array(totalBytes);
  let pos = 0;
  for (const p of outParts) {
    if (p && p.length) {
      merged.set(p, pos);
      pos += p.length;
    }
  }
  return {
    audioBase64: uint8ToBase64(merged),
    mimeType: 'audio/aac',
    byteLength: totalBytes,
  };
}

/* ==================== #4 EXPORT TRANSLATED CUES TO STANDARD .SRT ==================== */
function formatSrtTimestamp(ms) {
  const safe = Math.max(0, Math.round(Number(ms) || 0));
  const hrs = Math.floor(safe / 3600000);
  const mins = Math.floor((safe % 3600000) / 60000);
  const secs = Math.floor((safe % 60000) / 1000);
  const rem = safe % 1000;
  const pad2 = (n) => String(n).padStart(2, '0');
  const pad3 = (n) => String(n).padStart(3, '0');
  return `${pad2(hrs)}:${pad2(mins)}:${pad2(secs)},${pad3(rem)}`;
}

export function exportCuesToSrt(cues) {
  if (!Array.isArray(cues) || !cues.length) return '';
  return cues
    .filter((c) => c && c.text)
    .map((c, idx) => {
      const s = formatSrtTimestamp(c.startMs);
      const e = formatSrtTimestamp(Math.max(c.endMs || c.startMs + 2200, c.startMs + 450));
      return `${idx + 1}\r\n${s} --> ${e}\r\n${String(c.text).trim()}`;
    })
    .join('\r\n\r\n') + '\r\n';
}

export function downloadSrtFile(cues, filename = 'chillfilm-sub.srt') {
  const srtText = exportCuesToSrt(cues);
  if (!srtText) return false;
  const blob = new Blob(['\uFEFF' + srtText], { type: 'application/x-subrip;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.srt') ? filename : `${filename}.srt`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
  return true;
}
