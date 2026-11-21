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
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1;
    const c = cues[mid];
    if (effectiveMs < c.startMs) hi = mid - 1;
    else if (effectiveMs > c.endMs) lo = mid + 1;
    else return c.text;
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
        };
      });
  } catch {
    return [];
  }
}

export async function translateCueBatch({ cues, targetLang, geminiApiKey, geminiModel }) {
  if (!cues.length) return cues;
  const langSpec = resolveLanguage(targetLang);

  // 1. Gemini AI Cinema Translation when key is set
  if (geminiApiKey && geminiApiKey.trim().length > 10) {
    try {
      const translated = await translateWithGemini(cues, langSpec, geminiApiKey.trim(), geminiModel || 'gemini-3-flash-preview');
      if (translated && translated.length === cues.length) return translated;
    } catch (e) {
      console.warn('Gemini batch fallback to free neural:', e);
    }
  }

  // 2. Fast Free Neural Translation fallback (works out-of-the-box with zero API key!)
  return translateWithGoogleFree(cues, langSpec.code);
}

async function translateWithGemini(cues, langSpec, apiKey, modelId) {
  const cleanModel = modelId.replace(/^models\//, '') || 'gemini-3-flash-preview';
  const inputPayload = cues.map((c, idx) => ({ i: idx, t: c.text.replace(/\n/g, ' ') }));
  const prompt = `Cinema subtitle translator: translate each line into ${langSpec.prompt}.\nKeep concise, natural, idiomatic.\nReturn ONLY JSON array: [{"i":0,"t":"..."}]\nInput:\n${JSON.stringify(inputPayload)}`;

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

async function translateWithGoogleFree(cues, targetLang) {
  const tl = targetLang === 'zh' ? 'zh-CN' : targetLang;
  const delim = '\n[[[C]]]\n';
  const joined = cues.map((c) => c.text.replace(/\n/g, ' ')).join(delim);
  try {
    const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${encodeURIComponent(tl)}&dt=t&q=${encodeURIComponent(joined)}`;
    const res = await fetch(url);
    if (!res.ok) return cues;
    const data = await res.json();
    const full = (data[0] || []).map((seg) => seg[0] || '').join('');
    const split = full.split(/\[\[\[C\]\]\]/i);
    if (split.length === cues.length) {
      return cues.map((c, i) => ({ ...c, text: split[i].trim() || c.text }));
    }
    return cues;
  } catch {
    return cues;
  }
}
