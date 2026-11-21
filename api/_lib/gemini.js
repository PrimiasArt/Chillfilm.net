const LANGUAGE_INSTRUCTIONS = {
  vi: 'natural cinema-quality Vietnamese movie subtitles',
  en: 'natural cinema-quality English movie subtitles',
  th: 'natural cinema-quality Thai movie subtitles (ภาษาไทยธรรมชาติ)',
  zh: 'natural cinema-quality Simplified Chinese subtitles',
  'zh-tw': 'natural cinema-quality Traditional Chinese subtitles',
  ko: 'natural cinema-quality Korean movie subtitles',
  ja: 'natural cinema-quality Japanese movie subtitles',
  fr: 'natural cinema-quality French movie subtitles',
  es: 'natural cinema-quality Spanish movie subtitles',
  de: 'natural cinema-quality German movie subtitles',
  id: 'natural cinema-quality Indonesian movie subtitles',
  ru: 'natural cinema-quality Russian movie subtitles',
  pt: 'natural cinema-quality Portuguese movie subtitles',
  it: 'natural cinema-quality Italian movie subtitles',
  ar: 'natural cinema-quality Modern Standard Arabic subtitles',
  hi: 'natural cinema-quality Hindi movie subtitles',
  ms: 'natural cinema-quality Malay movie subtitles',
  lo: 'natural cinema-quality Lao movie subtitles',
  km: 'natural cinema-quality Khmer movie subtitles',
};

export function resolveLangInstruction(targetLang) {
  const clean = String(targetLang || 'vi').trim();
  const key = clean.toLowerCase();
  return LANGUAGE_INSTRUCTIONS[key] || `natural cinema-quality movie subtitles in ${clean}`;
}

export function buildLowLatencyGenConfig(modelId, includeThinkingConfig = true) {
  const cfg = {
    temperature: 0.15,
    responseMimeType: 'application/json',
  };
  if (!includeThinkingConfig) return cfg;
  const clean = String(modelId || '').trim().replace(/^models\//, '').toLowerCase();
  if (clean.startsWith('gemini-3') && clean.includes('flash')) {
    cfg.thinkingConfig = { thinkingLevel: 'MINIMAL' };
  } else if (clean.startsWith('gemini-3')) {
    cfg.thinkingConfig = { thinkingLevel: 'LOW' };
  } else if (clean.startsWith('gemini-2.5') && clean.includes('flash')) {
    cfg.thinkingConfig = { thinkingBudget: 0 };
  } else if (clean.startsWith('gemini-2.5')) {
    cfg.thinkingConfig = { thinkingBudget: 128 };
  }
  return cfg;
}

export function buildModelFallbackChain(preferredModel, preferTurboFlash = false) {
  const clean = String(preferredModel || 'gemini-3-flash-preview')
    .trim()
    .replace(/^models\//, '') || 'gemini-3-flash-preview';
  const ordered = preferTurboFlash && clean.toLowerCase().includes('pro')
    ? ['gemini-3-flash-preview', 'gemini-2.5-flash', clean, 'gemini-2.0-flash']
    : [clean, 'gemini-2.5-flash', 'gemini-2.0-flash'];
  return [...new Set(ordered)];
}

async function callGeminiWithFallback({ apiKey, contents, preferredModel, preferTurboFlash = false }) {
  const chain = buildModelFallbackChain(preferredModel, preferTurboFlash);
  let lastError = 'Gemini request failed';

  for (let i = 0; i < chain.length; i++) {
    const model = chain[i];
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;

    for (const useThinking of [true, false]) {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents,
          generationConfig: buildLowLatencyGenConfig(model, useThinking),
        }),
      });

      const bodyText = await res.text();
      if (!res.ok) {
        let errDetail = '';
        try {
          errDetail = JSON.parse(bodyText)?.error?.message || '';
        } catch {
          errDetail = bodyText.slice(0, 140);
        }
        lastError = `Gemini (${model}) HTTP ${res.status}: ${errDetail.slice(0, 140)}`;
        if (useThinking && res.status === 400 && errDetail.toLowerCase().includes('thinking')) {
          continue; // retry same model without thinkingConfig
        }
        const retryable = [404, 429, 500, 502, 503].includes(res.status) ||
          (res.status === 400 && errDetail.toLowerCase().includes('model'));
        if (!retryable || i === chain.length - 1) {
          throw new Error(lastError);
        }
        break; // move to next model in chain
      }

      const json = JSON.parse(bodyText);
      const parts = json?.candidates?.[0]?.content?.parts || [];
      let rawText = '';
      for (const p of parts) {
        if (!p.thought && p.text) rawText = String(p.text).trim();
      }
      return { rawText, modelUsed: model };
    }
  }
  throw new Error(lastError);
}

function buildMovieContextLine(movieContext) {
  if (!movieContext || typeof movieContext !== 'object') return '';
  const title = String(movieContext.title || '').trim().slice(0, 100);
  const genres = Array.isArray(movieContext.genres)
    ? movieContext.genres.map((g) => String(g || '').trim()).filter(Boolean).slice(0, 4).join(', ')
    : String(movieContext.genres || '').trim().slice(0, 80);
  if (!title && !genres) return '';
  const parts = [];
  if (title) parts.push(`"${title}"`);
  if (genres) parts.push(`[${genres}]`);
  return `Movie context: ${parts.join(' ')} — match tone, era, pronouns, and honorifics naturally to the genre.`;
}

/**
 * Translates an array of dialogue texts (`string[]` or `{i, t}[]`) via server-side Gemini API.
 */
export async function translateTextsServerSide({ texts, targetLang, apiKey, preferredModel, movieContext = null }) {
  const langInstruction = resolveLangInstruction(targetLang);
  const ctxLine = buildMovieContextLine(movieContext);
  const inputPayload = texts.map((t, idx) => ({
    i: idx,
    t: String(t || '').replace(/\r?\n/g, ' ').trim(),
  }));

  const prompt = [
    `Cinema subtitle translator: translate each movie dialogue line into ${langInstruction}.`,
    ...(ctxLine ? [ctxLine] : []),
    'Keep concise, natural, idiomatic, easy to read on screen.',
    'Return ONLY JSON array: [{"i":0,"t":"..."}]',
    'Input:',
    JSON.stringify(inputPayload),
  ].join('\n');

  const { rawText, modelUsed } = await callGeminiWithFallback({
    apiKey,
    preferredModel,
    contents: [{ parts: [{ text: prompt }] }],
  });

  const cleaned = rawText
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/```\s*$/, '')
    .trim();
  const s = cleaned.indexOf('[');
  const e = cleaned.lastIndexOf(']');
  if (s < 0 || e <= s) {
    throw new Error('Gemini returned non-JSON output');
  }
  const arr = JSON.parse(cleaned.slice(s, e + 1));
  const map = new Map();
  arr.forEach((o, idx) => {
    const key = typeof o?.i === 'number' ? o.i : idx;
    if (typeof o?.t === 'string') map.set(key, o.t.trim());
  });
  const translated = texts.map((orig, idx) => map.get(idx) || String(orig || ''));
  return { translated, modelUsed };
}

/**
 * Batch-translates up to 20 catalog items (foreign MacCMS titles + short synopses)
 * into BOTH natural English (`en`, `de`) and Vietnamese (`vi`, `dv`) in a single Gemini call.
 */
export async function translateCatalogItemsBilingual({ items, apiKey, preferredModel }) {
  if (!Array.isArray(items) || !items.length || !apiKey) return [];
  const payload = items.map((it, idx) => ({
    i: idx,
    t: String(it.name || '').trim().slice(0, 110),
    o: String(it.origin_name || '').trim().slice(0, 90),
    c: String(it.content || '').trim().slice(0, 180),
  }));

  const prompt = [
    'Cinema metadata translator: for each movie/series below, provide clean official-style English title ("en"), natural Vietnamese title ("vi"), concise 1-sentence English synopsis ("de"), and concise 1-sentence Vietnamese synopsis ("dv").',
    'If a title is already English or Vietnamese, keep/polish it naturally.',
    'Return ONLY JSON array: [{"i":0,"en":"...","vi":"...","de":"...","dv":"..."}]',
    'Input:',
    JSON.stringify(payload),
  ].join('\n');

  const { rawText } = await callGeminiWithFallback({
    apiKey,
    preferredModel: preferredModel || 'gemini-2.5-flash',
    preferTurboFlash: true,
    contents: [{ parts: [{ text: prompt }] }],
  });

  const cleaned = rawText
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/```\s*$/, '')
    .trim();
  const s = cleaned.indexOf('[');
  const e = cleaned.lastIndexOf(']');
  if (s < 0 || e <= s) return [];
  const arr = JSON.parse(cleaned.slice(s, e + 1));
  const map = new Map();
  arr.forEach((o, idx) => {
    if (!o) return;
    const key = typeof o.i === 'number' ? o.i : idx;
    map.set(key, {
      nameEn: String(o.en || '').trim().slice(0, 180),
      nameVi: String(o.vi || '').trim().slice(0, 180),
      contentEn: String(o.de || '').trim().slice(0, 280),
      contentVi: String(o.dv || '').trim().slice(0, 280),
    });
  });
  return items.map((it, idx) => ({
    itemKey: it.item_key,
    ...(map.get(idx) || { nameEn: '', nameVi: '', contentEn: '', contentVi: '' }),
  }));
}

function parseFlexibleSeconds(val) {
  if (typeof val === 'number' && Number.isFinite(val)) return val;
  const s = String(val ?? '').trim();
  if (!s) return null;
  if (s.includes(':')) {
    const parts = s.split(':').map(Number);
    if (parts.some((n) => !Number.isFinite(n))) return null;
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    if (parts.length === 2) return parts[0] * 60 + parts[1];
  }
  const n = Number(s.replace(',', '.'));
  return Number.isFinite(n) ? n : null;
}

/**
 * Transcribes + translates a base64 audio chunk into time-aligned subtitles via server-side Gemini.
 */
export async function transcribeAudioChunkServerSide({
  audioBase64,
  mimeType,
  chunkStartOffsetMs,
  chunkDurationMs,
  targetLang,
  apiKey,
  preferredModel,
  preferTurboFlash = false,
  movieContext = null,
}) {
  const durationSec = (Number(chunkDurationMs || 60000) / 1000).toFixed(1);
  const langInstruction = resolveLangInstruction(targetLang);
  const ctxLine = buildMovieContextLine(movieContext);
  const prompt = [
    `Universal cinema subtitle creator: listen to this ${durationSec}-second movie audio clip (starts at 0.0s).`,
    `Transcribe ALL spoken dialogue (from ANY spoken language) directly into ${langInstruction}.`,
    ...(ctxLine ? [ctxLine] : []),
    'Rules:',
    `- "s" and "e" = float seconds relative to 0.0 of this clip (0.0 <= s < e <= ${durationSec}), 1.2s-4.8s per cue.`,
    '- Skip music/silence; if no speech, return [].',
    'Return ONLY JSON array: [{"s":1.2,"e":3.6,"t":"translated subtitle text"}]',
  ].join('\n');

  const { rawText, modelUsed } = await callGeminiWithFallback({
    apiKey,
    preferredModel,
    preferTurboFlash,
    contents: [
      {
        parts: [
          {
            inline_data: {
              mime_type: mimeType || 'audio/aac',
              data: audioBase64,
            },
          },
          { text: prompt },
        ],
      },
    ],
  });

  const cleaned = rawText
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/```\s*$/, '')
    .trim();

  const cues = [];
  const s = cleaned.indexOf('[');
  const e = cleaned.lastIndexOf(']');
  if (s >= 0 && e > s) {
    const arr = JSON.parse(cleaned.slice(s, e + 1));
    const maxRelMs = Number(chunkDurationMs || 60000) + 5000;
    const baseOffset = Number(chunkStartOffsetMs || 0);
    for (let i = 0; i < arr.length; i++) {
      const obj = arr[i];
      if (!obj) continue;
      const text = String(obj.t || obj.text || '').trim();
      if (!text) continue;
      const sSec = parseFlexibleSeconds(obj.s ?? obj.start);
      if (sSec === null) continue;
      const eSec = parseFlexibleSeconds(obj.e ?? obj.end) ?? (sSec + 2.5);
      const relStart = Math.max(0, Math.min(maxRelMs, Math.round(sSec * 1000)));
      const relEnd = Math.max(relStart + 400, Math.min(maxRelMs + 2000, Math.round(eSec * 1000)));
      cues.push({
        id: i + 1,
        startMs: baseOffset + relStart,
        endMs: baseOffset + relEnd,
        text,
      });
    }
  }
  return { cues, modelUsed };
}

/**
 * Fetches all live generative models directly from Google Gemini API (v1beta/models),
 * filtering strictly to `generateContent`-capable chat/multimodal models and sorting newest first.
 */
export async function fetchLiveGeminiModels(apiKey) {
  const collected = [];
  let pageToken = '';
  for (let page = 0; page < 3; page++) {
    const tokenParam = pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : '';
    const url = `https://generativelanguage.googleapis.com/v1beta/models?pageSize=100${tokenParam}&key=${encodeURIComponent(apiKey)}`;
    const res = await fetch(url);
    const bodyText = await res.text();
    if (!res.ok) {
      let msg = `Gemini models HTTP ${res.status}`;
      try {
        msg = JSON.parse(bodyText)?.error?.message || msg;
      } catch {
        // ignore
      }
      throw new Error(msg);
    }
    const json = JSON.parse(bodyText);
    const list = Array.isArray(json.models) ? json.models : [];
    for (const m of list) {
      const methods = Array.isArray(m.supportedGenerationMethods) ? m.supportedGenerationMethods : [];
      if (!methods.includes('generateContent')) continue;
      const rawName = String(m.name || '').trim();
      const id = rawName.replace(/^models\//, '');
      if (!id) continue;
      const lower = id.toLowerCase();
      // Exclude non-text/non-subtitle specialized models (embeddings, imagen, veo, tts, audio-only robotics)
      if (
        lower.includes('embedding') ||
        lower.includes('imagen') ||
        lower.includes('veo') ||
        lower.includes('aqa') ||
        lower.includes('tts') ||
        lower.includes('image-generation') ||
        lower.includes('robotics') ||
        lower.includes('computer-use') ||
        lower.includes('deep-research')
      ) {
        continue;
      }
      if (!lower.startsWith('gemini') && !lower.startsWith('gemma')) continue;
      collected.push({
        id,
        displayName: String(m.displayName || id).trim(),
        description: String(m.description || '').trim().slice(0, 140),
        inputTokenLimit: Number(m.inputTokenLimit || 0),
        outputTokenLimit: Number(m.outputTokenLimit || 0),
      });
    }
    pageToken = json.nextPageToken || '';
    if (!pageToken) break;
  }

  // Deduplicate and rank newest/highest-version Gemini models first
  const seen = new Set();
  const deduped = collected.filter((m) => {
    if (seen.has(m.id)) return false;
    seen.add(m.id);
    return true;
  });

  const versionScore = (id) => {
    const low = id.toLowerCase();
    const isGemini = low.startsWith('gemini') ? 1000 : 0;
    const verMatch = low.match(/gemini-(\d+)(?:\.(\d+))?/);
    const major = verMatch ? Number(verMatch[1]) : 0;
    const minor = verMatch && verMatch[2] ? Number(verMatch[2]) : 0;
    const tier = low.includes('flash-lite')
      ? 25
      : low.includes('flash')
        ? 40
        : low.includes('pro')
          ? 35
          : 10;
    const stableBonus = low.includes('exp') || low.includes('001') || low.includes('002') ? -5 : 5;
    return isGemini + major * 100 + minor * 10 + tier + stableBonus;
  };

  deduped.sort((a, b) => {
    const diff = versionScore(b.id) - versionScore(a.id);
    return diff !== 0 ? diff : a.id.localeCompare(b.id);
  });

  return deduped;
}
