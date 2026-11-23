const LANGUAGE_INSTRUCTIONS = {
  vi: 'natural cinema-quality Vietnamese (Tiếng Việt, Latin script with Vietnamese diacritics — NEVER output Chinese/Hanzi, Japanese, or Korean)',
  en: 'natural cinema-quality English (Latin script only — NEVER output Chinese/Hanzi, Japanese, or Korean)',
  th: 'natural cinema-quality Thai (ภาษาไทยธรรมชาติ — NEVER output Chinese/Hanzi)',
  zh: 'natural cinema-quality Simplified Chinese (简体中文)',
  'zh-tw': 'natural cinema-quality Traditional Chinese (繁體中文)',
  ko: 'natural cinema-quality Korean (한국어)',
  ja: 'natural cinema-quality Japanese (日本語)',
  fr: 'natural cinema-quality French (Français)',
  es: 'natural cinema-quality Spanish (Español)',
  de: 'natural cinema-quality German (Deutsch)',
  id: 'natural cinema-quality Indonesian (Bahasa Indonesia)',
  ru: 'natural cinema-quality Russian (Русский)',
  pt: 'natural cinema-quality Portuguese (Português)',
  it: 'natural cinema-quality Italian (Italiano)',
  ar: 'natural cinema-quality Modern Standard Arabic (العربية)',
  hi: 'natural cinema-quality Hindi (हिन्दी)',
  ms: 'natural cinema-quality Malay (Bahasa Melayu)',
  lo: 'natural cinema-quality Lao (ພາສາລາວ)',
  km: 'natural cinema-quality Khmer (ភាសាខ្មែរ)',
};

const LANGUAGE_DISPLAY_NAMES = {
  vi: 'Vietnamese (Tiếng Việt)',
  en: 'English',
  th: 'Thai (ภาษาไทย)',
  zh: 'Simplified Chinese (简体中文)',
  'zh-tw': 'Traditional Chinese (繁體中文)',
  ko: 'Korean (한국어)',
  ja: 'Japanese (日本語)',
  fr: 'French',
  es: 'Spanish',
  de: 'German',
  id: 'Indonesian',
  ru: 'Russian',
  pt: 'Portuguese',
  it: 'Italian',
  ar: 'Arabic',
  hi: 'Hindi',
  ms: 'Malay',
  lo: 'Lao',
  km: 'Khmer',
};

export function resolveLangInstruction(targetLang) {
  const clean = String(targetLang || 'vi').trim();
  const key = clean.toLowerCase();
  return LANGUAGE_INSTRUCTIONS[key] || `natural cinema-quality movie subtitles in ${clean}`;
}

export function resolveLangDisplayName(targetLang) {
  const clean = String(targetLang || 'vi').trim();
  const key = clean.toLowerCase();
  return LANGUAGE_DISPLAY_NAMES[key] || clean;
}

const RE_HAN = /[\u4e00-\u9fff\u3400-\u4dbf]/g;
const RE_KANA = /[\u3040-\u30ff]/g;
const RE_HANGUL = /[\uac00-\ud7af]/g;
const RE_ALL_LETTERS = /[\p{L}]/gu;

function countForbiddenScriptChars(text, langKey) {
  const s = String(text || '');
  if (!s) return { forbidden: 0, letters: 0 };
  const letters = (s.match(RE_ALL_LETTERS) || []).length;
  if (!letters) return { forbidden: 0, letters: 0 };

  const han = (s.match(RE_HAN) || []).length;
  const kana = (s.match(RE_KANA) || []).length;
  const hangul = (s.match(RE_HANGUL) || []).length;

  if (langKey === 'zh' || langKey === 'zh-tw' || langKey === 'zh-cn') {
    return { forbidden: kana + hangul, letters };
  }
  if (langKey === 'ja') {
    return { forbidden: hangul, letters };
  }
  if (langKey === 'ko') {
    // Allow occasional Hanja, reject pure Chinese text lacking Hangul
    return { forbidden: hangul === 0 ? han + kana : kana, letters };
  }
  // All non-CJK languages (vi, en, th, fr, es, de, id, ru, pt, it, ar, hi, ms, lo, km, ...)
  return { forbidden: han + kana + hangul, letters };
}

export function isSingleLineWrongScript(text, targetLang = 'vi') {
  const key = String(targetLang || 'vi').trim().toLowerCase();
  const { forbidden, letters } = countForbiddenScriptChars(text, key);
  return forbidden >= 2 && letters > 0 && forbidden / letters >= 0.3;
}

/**
 * Returns true if a batch/bundle of subtitles is predominantly in a forbidden foreign script
 * for `targetLang` (e.g. Chinese Hanzi characters inside a Vietnamese `vi` subtitle track).
 */
export function hasWrongScriptForTarget(cuesOrTexts, targetLang = 'vi') {
  if (!Array.isArray(cuesOrTexts) || !cuesOrTexts.length) return false;
  const key = String(targetLang || 'vi').trim().toLowerCase();
  let nonEmpty = 0;
  let badCues = 0;
  let totalForbidden = 0;
  let totalLetters = 0;

  for (const item of cuesOrTexts) {
    const txt = typeof item === 'string' ? item : String(item?.text ?? item?.t ?? '');
    const { forbidden, letters } = countForbiddenScriptChars(txt, key);
    if (letters === 0) continue;
    nonEmpty += 1;
    totalForbidden += forbidden;
    totalLetters += letters;
    if (forbidden >= 2 && forbidden / letters >= 0.3) {
      badCues += 1;
    }
  }
  if (!nonEmpty || !totalLetters) return false;
  if (nonEmpty <= 5) {
    return badCues >= 1 && totalForbidden >= 4 && totalForbidden / totalLetters >= 0.22;
  }
  return badCues / nonEmpty >= 0.18 || totalForbidden / totalLetters >= 0.2;
}

export function buildLowLatencyGenConfig(modelId, includeThinkingConfig = true, maxOutputTokens = 0) {
  const cfg = {
    temperature: 0.15,
    responseMimeType: 'application/json',
  };
  if (maxOutputTokens > 0) {
    cfg.maxOutputTokens = maxOutputTokens;
  }
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
  const clean = String(preferredModel || 'gemini-2.5-flash')
    .trim()
    .replace(/^models\//, '') || 'gemini-2.5-flash';
  const low = clean.toLowerCase();
  // For turbo audio/batch subtitles, prioritize stable ultra-low-latency gemini-2.5-flash over slow pro/preview models to prevent 504 timeouts
  const ordered = preferTurboFlash && (low.includes('pro') || low.includes('preview'))
    ? ['gemini-2.5-flash', clean, 'gemini-2.0-flash', 'gemini-2.5-flash-lite']
    : [clean, 'gemini-2.5-flash', 'gemini-2.0-flash', 'gemini-2.5-flash-lite'];
  return [...new Set(ordered)];
}

async function callGeminiWithFallback({
  apiKey,
  contents,
  systemInstruction = '',
  preferredModel,
  preferTurboFlash = false,
  perAttemptTimeoutMs = 18000,
  maxOutputTokens = 0,
}) {
  const chain = buildModelFallbackChain(preferredModel, preferTurboFlash);
  let lastError = 'Gemini request failed';

  for (let i = 0; i < chain.length; i++) {
    const model = chain[i];
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;

    for (const useThinking of [true, false]) {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), perAttemptTimeoutMs);
      let res;
      let bodyText = '';
      try {
        const reqBody = {
          contents,
          generationConfig: buildLowLatencyGenConfig(model, useThinking, maxOutputTokens),
        };
        if (systemInstruction) {
          reqBody.systemInstruction = { parts: [{ text: systemInstruction }] };
        }
        res = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal: ctrl.signal,
          body: JSON.stringify(reqBody),
        });
        bodyText = await res.text();
      } catch (fetchErr) {
        clearTimeout(timer);
        const isTimeout = fetchErr?.name === 'AbortError';
        lastError = isTimeout
          ? `Gemini (${model}) timed out after ${Math.round(perAttemptTimeoutMs / 1000)}s`
          : `Gemini (${model}) network error: ${String(fetchErr?.message || fetchErr).slice(0, 120)}`;
        if (i === chain.length - 1) throw new Error(lastError);
        break; // move immediately to next fast fallback model instead of hitting Vercel 504!
      }
      clearTimeout(timer);

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
        const retryable = [404, 408, 429, 500, 502, 503, 504].includes(res.status) ||
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
  const lines = [];
  if (title || genres) {
    const parts = [];
    if (title) parts.push(`"${title}"`);
    if (genres) parts.push(`[${genres}]`);
    lines.push(`Movie context: ${parts.join(' ')} — match tone, era, pronouns, and honorifics naturally to the genre.`);
  }
  const gl = movieContext.glossary;
  if (gl && typeof gl === 'object') {
    const terms = Array.isArray(gl.terms)
      ? gl.terms
          .filter((t) => t && t.src && t.dst)
          .slice(0, 22)
          .map((t) => `${String(t.src).trim()}=${String(t.dst).trim()}`)
          .join('; ')
      : '';
    const tone = String(gl.tone || '').trim().slice(0, 180);
    if (terms) lines.push(`Strict character/term glossary (ALWAYS keep consistent): ${terms}`);
    if (tone) lines.push(`Pronoun & style rule: ${tone}`);
  }
  return lines.join('\n');
}

/**
 * Translates an array of dialogue texts (`string[]` or `{i, t}[]`) via server-side Gemini API.
 * Includes strict target-language enforcement + automatic single-pass repair if any line echoes CJK/wrong script.
 */
export async function translateTextsServerSide({
  texts,
  targetLang,
  apiKey,
  preferredModel,
  movieContext = null,
  _isRepairPass = false,
}) {
  const langInstruction = resolveLangInstruction(targetLang);
  const langName = resolveLangDisplayName(targetLang);
  const ctxLine = buildMovieContextLine(movieContext);
  const inputPayload = texts.map((t, idx) => ({
    i: idx,
    t: String(t || '').replace(/\r?\n/g, ' ').trim(),
  }));

  const systemInstruction = `You are a professional cinema subtitle translator. EVERY output subtitle line MUST be written strictly in ${langName}. Never leave source-language text untranslated and never output Chinese/Japanese/Korean characters unless ${langName} itself uses them.`;

  const prompt = [
    `Translate EVERY movie dialogue line below into ${langInstruction}.`,
    `STRICT RULE: Output text MUST be 100% in ${langName}. Do NOT copy or keep the original language.`,
    ...(ctxLine ? [ctxLine] : []),
    'Keep each line concise, natural, idiomatic, easy to read on screen.',
    'Return ONLY JSON array with exact matching indices: [{"i":0,"t":"..."}]',
    'Input:',
    JSON.stringify(inputPayload),
  ].join('\n');

  const { rawText, modelUsed } = await callGeminiWithFallback({
    apiKey,
    systemInstruction,
    preferredModel,
    preferTurboFlash: true,
    perAttemptTimeoutMs: 18000,
    maxOutputTokens: 4096,
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

  // Auto-repair any lines that slipped through in a forbidden script (e.g. Hanzi when translating to Vietnamese)
  if (!_isRepairPass) {
    const badIndices = [];
    for (let idx = 0; idx < translated.length; idx++) {
      if (isSingleLineWrongScript(translated[idx], targetLang)) {
        badIndices.push(idx);
      }
    }
    if (badIndices.length > 0 && badIndices.length <= 60) {
      try {
        const badTexts = badIndices.map((idx) => texts[idx] || translated[idx]);
        const repair = await translateTextsServerSide({
          texts: badTexts,
          targetLang,
          apiKey,
          preferredModel,
          movieContext,
          _isRepairPass: true,
        });
        for (let k = 0; k < badIndices.length; k++) {
          const fixed = repair.translated?.[k];
          if (fixed && !isSingleLineWrongScript(fixed, targetLang)) {
            translated[badIndices[k]] = fixed;
          }
        }
      } catch (repairErr) {
        console.warn('[gemini] wrong-script repair pass failed:', repairErr?.message || repairErr);
      }
    }
  }

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
  const langName = resolveLangDisplayName(targetLang);
  const ctxLine = buildMovieContextLine(movieContext);
  const systemInstruction = `You are a professional movie subtitle translator. Listen to spoken dialogue in ANY language (Chinese, English, Korean, Japanese, etc.) and output time-aligned subtitles WRITTEN STRICTLY IN ${langName}. NEVER transcribe or output in the original spoken language. NEVER output Chinese characters (汉字/中文) when translating into ${langName}.`;
  const prompt = [
    `Listen to this ${durationSec}-second movie audio clip (starts at 0.0s).`,
    `TRANSLATE ALL spoken dialogue directly into ${langInstruction}.`,
    `CRITICAL LANGUAGE RULE: Every "t" field MUST be written 100% in ${langName}. Even if the characters speak Chinese/Korean/Japanese/English, NEVER output their original script — ALWAYS translate into ${langName}.`,
    ...(ctxLine ? [ctxLine] : []),
    'Rules:',
    `- "s" and "e" = float seconds relative to 0.0 of this clip (0.0 <= s < e <= ${durationSec}), 1.2s-4.8s per cue.`,
    '- Skip music/silence; if no speech, return [].',
    `Return ONLY JSON array: [{"s":1.2,"e":3.6,"t":"subtitle in ${langName}"}]`,
  ].join('\n');

  const { rawText, modelUsed } = await callGeminiWithFallback({
    apiKey,
    systemInstruction,
    preferredModel,
    preferTurboFlash: true,
    perAttemptTimeoutMs: 22000,
    maxOutputTokens: 2048,
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
  let arr = [];
  if (s >= 0 && e > s) {
    try {
      arr = JSON.parse(cleaned.slice(s, e + 1));
    } catch {
      arr = [];
    }
  }
  // Salvage complete cue objects even if output was truncated at maxOutputTokens
  if (!arr.length && s >= 0) {
    const matches = cleaned.slice(s).match(/\{[^{}]*"t"\s*:\s*"[^"]*"[^{}]*\}/g) || [];
    for (const m of matches) {
      try { arr.push(JSON.parse(m)); } catch { /* ignore */ }
    }
  }
  if (arr.length) {
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

  // Automatic Language Safety Net: if audio model slipped into ASR transcription (e.g. Chinese Hanzi
  // instead of Vietnamese), automatically translate those cues via text pass before returning!
  if (cues.length && (hasWrongScriptForTarget(cues, targetLang) || cues.some((c) => isSingleLineWrongScript(c.text, targetLang)))) {
    const badIdx = [];
    const needFull = hasWrongScriptForTarget(cues, targetLang);
    for (let i = 0; i < cues.length; i++) {
      if (needFull || isSingleLineWrongScript(cues[i].text, targetLang)) {
        badIdx.push(i);
      }
    }
    if (badIdx.length) {
      try {
        const repair = await translateTextsServerSide({
          texts: badIdx.map((i) => cues[i].text),
          targetLang,
          apiKey,
          preferredModel,
          movieContext,
          _isRepairPass: true,
        });
        for (let k = 0; k < badIdx.length; k++) {
          const fixed = repair.translated?.[k];
          if (fixed && !isSingleLineWrongScript(fixed, targetLang)) {
            cues[badIdx[k]].text = fixed;
          }
        }
      } catch (repairErr) {
        console.warn('[gemini audio-sub] wrong-script auto-translate repair failed:', repairErr?.message || repairErr);
      }
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

/**
 * Generates a consistent character/terminology & pronoun glossary for a movie/series
 * so every translated batch & episode uses the exact same names and tone (e.g. wuxia/xianxia/K-drama).
 */
export async function generateMovieGlossaryServerSide({
  title = '',
  originTitle = '',
  synopsis = '',
  genres = [],
  targetLang = 'vi',
  apiKey,
  preferredModel = 'gemini-2.5-flash',
}) {
  if (!apiKey) return { terms: [], tone: '' };
  const langInstruction = resolveLangInstruction(targetLang);
  const genreStr = Array.isArray(genres) ? genres.join(', ') : String(genres || '');
  const prompt = [
    `Cinema subtitle chief editor: create a concise translation glossary in ${langInstruction} for this movie/series so all episodes translate character names, realms, techniques, places, and pronouns consistently.`,
    `Title: "${String(title || '').slice(0, 120)}"`,
    originTitle ? `Original title: "${String(originTitle).slice(0, 120)}"` : '',
    genreStr ? `Genres: ${genreStr.slice(0, 100)}` : '',
    synopsis ? `Synopsis: ${String(synopsis).slice(0, 500)}` : '',
    'Rules:',
    '- Return ONLY JSON object: {"tone":"1 concise rule on pronouns/honorifics/style","terms":[{"src":"original name or term","dst":"consistent localized translation"}]}',
    '- Include 6 to 18 most important character names, factions, places, or iconic terms (especially for xianxia/wuxia/anime/historical/sci-fi).',
    '- If it is Vietnamese xianxia/wuxia (tiên hiệp/kiếm hiệp), use canonical Hán-Việt translations (e.g., 萧炎 -> Tiêu Viêm, 斗帝 -> Đấu Đế) and appropriate xưng hô (huynh/đệ, tiền bối/tại hạ).',
  ].filter(Boolean).join('\n');

  const { rawText } = await callGeminiWithFallback({
    apiKey,
    preferredModel,
    preferTurboFlash: true,
    perAttemptTimeoutMs: 14000,
    maxOutputTokens: 1024,
    contents: [{ parts: [{ text: prompt }] }],
  });

  const cleaned = rawText.replace(/^```json\s*/i, '').replace(/^```\s*/i, '').replace(/```\s*$/, '').trim();
  const s = cleaned.indexOf('{');
  const e = cleaned.lastIndexOf('}');
  if (s < 0 || e <= s) return { terms: [], tone: '' };
  try {
    const obj = JSON.parse(cleaned.slice(s, e + 1));
    const terms = Array.isArray(obj.terms)
      ? obj.terms
          .filter((t) => t && t.src && t.dst)
          .slice(0, 22)
          .map((t) => ({
            src: String(t.src).trim().slice(0, 60),
            dst: String(t.dst).trim().slice(0, 80),
          }))
      : [];
    const tone = String(obj.tone || '').trim().slice(0, 200);
    return { terms, tone };
  } catch {
    return { terms: [], tone: '' };
  }
}

/**
 * Parses a natural-language movie mood/vibe query (Vietnamese or English) into structured
 * search keywords, genre, country, and year filters for AI Vibe Search.
 */
export async function parseAiVibeSearchQuery({ query, apiKey, preferredModel = 'gemini-2.5-flash' }) {
  const cleanQ = String(query || '').trim().slice(0, 220);
  if (!cleanQ || !apiKey) {
    return { keywords: cleanQ ? [cleanQ] : [], genre: '', country: '', minYear: 0, vibeSummary: '' };
  }
  const prompt = [
    'Movie catalog search assistant: convert this user mood/vibe request into structured movie search parameters.',
    `User request: "${cleanQ}"`,
    'Allowed genres: action, romance, sci-fi, horror, comedy, drama, thriller, crime, historical, documentary, anime (or empty string "").',
    'Allowed country slugs: han-quoc, trung-quoc, au-my, nhat-ban, thai-lan, viet-nam, hong-kong, dai-loan, an-do, phap, anh (or empty string "").',
    'Return ONLY JSON object:',
    '{"vibeSummary":"short 1-sentence friendly Vietnamese explanation of what we are finding","keywords":["2 to 5 strong Vietnamese/English/Chinese movie title or theme keywords to search"],"genre":"","country":"","minYear":0}',
  ].join('\n');

  const { rawText } = await callGeminiWithFallback({
    apiKey,
    preferredModel,
    preferTurboFlash: true,
    perAttemptTimeoutMs: 10000,
    maxOutputTokens: 512,
    contents: [{ parts: [{ text: prompt }] }],
  });

  const cleaned = rawText.replace(/^```json\s*/i, '').replace(/^```\s*/i, '').replace(/```\s*$/, '').trim();
  const s = cleaned.indexOf('{');
  const e = cleaned.lastIndexOf('}');
  if (s < 0 || e <= s) return { keywords: [cleanQ], genre: '', country: '', minYear: 0, vibeSummary: '' };
  try {
    const obj = JSON.parse(cleaned.slice(s, e + 1));
    const keywords = Array.isArray(obj.keywords)
      ? obj.keywords.map((k) => String(k || '').trim()).filter((k) => k.length >= 2).slice(0, 5)
      : [cleanQ];
    return {
      vibeSummary: String(obj.vibeSummary || '').trim().slice(0, 200),
      keywords: keywords.length ? keywords : [cleanQ],
      genre: String(obj.genre || '').trim().toLowerCase(),
      country: String(obj.country || '').trim().toLowerCase(),
      minYear: Number(obj.minYear || 0) || 0,
    };
  } catch {
    return { keywords: [cleanQ], genre: '', country: '', minYear: 0, vibeSummary: '' };
  }
}
