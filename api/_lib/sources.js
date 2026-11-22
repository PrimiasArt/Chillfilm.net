/**
 * Multi-Source Global Cinema Connectors (All 100% filtered by Hard SFW Guard):
 * 1. KKPhim / PhimAPI v3 (36 countries, direct 1080p HLS .m3u8)
 * 2. Internet Archive Global Cinema (29,600+ legal international films, direct .mp4 + .SRT/.VTT)
 * 3. Public Clean Movie Live Channels (iptv-org SFW Movies)
 * 4. Dynamic Admin Sources stored in Neon Postgres:
 *    - PHIMAPI_JSON (PhimAPI / OPhim / KKPhim compatible REST APIs)
 *    - MACCMS_JSON (MacCMS v10 `api.php/provide/vod/?ac=detail` — huge multi-country Asian/Global ecosystem)
 *    - M3U_PLAYLIST (Global M3U/M3U8 cinema playlists)
 *    - CUSTOM_JSON (Simple `{ items: [...] }` catalog feeds)
 */

import { ensureSchema, getSql, getVaultConfigValue, setVaultConfigValue } from './db.js';
import { filterSafeMovies, isBlockedCategoryTag, isBlockedContent, isSafeSourceUrl } from './safety.js';

export const PHIM_API = 'https://phimapi.com';
export const PHIM_CDN = 'https://phimimg.com';

export const GLOBAL_COUNTRIES = [
  { slug: 'au-my', name: 'US / UK & Hollywood', flag: '🇺🇸' },
  { slug: 'han-quoc', name: 'South Korea', flag: '🇰🇷' },
  { slug: 'trung-quoc', name: 'China', flag: '🇨🇳' },
  { slug: 'nhat-ban', name: 'Japan', flag: '🇯🇵' },
  { slug: 'thai-lan', name: 'Thailand', flag: '🇹🇭' },
  { slug: 'anh', name: 'United Kingdom', flag: '🇬🇧' },
  { slug: 'an-do', name: 'India (Bollywood)', flag: '🇮🇳' },
  { slug: 'phap', name: 'France', flag: '🇫🇷' },
  { slug: 'duc', name: 'Germany', flag: '🇩🇪' },
  { slug: 'tay-ban-nha', name: 'Spain', flag: '🇪🇸' },
  { slug: 'y', name: 'Italy', flag: '🇮🇹' },
  { slug: 'nga', name: 'Russia', flag: '🇷🇺' },
  { slug: 'tho-nhi-ky', name: 'Turkey', flag: '🇹🇷' },
  { slug: 'dai-loan', name: 'Taiwan', flag: '🇹🇼' },
  { slug: 'hong-kong', name: 'Hong Kong', flag: '🇭🇰' },
  { slug: 'canada', name: 'Canada', flag: '🇨🇦' },
  { slug: 'uc', name: 'Australia', flag: '🇦🇺' },
  { slug: 'brazil', name: 'Brazil', flag: '🇧🇷' },
  { slug: 'mexico', name: 'Mexico', flag: '🇲🇽' },
  { slug: 'indonesia', name: 'Indonesia', flag: '🇮🇩' },
  { slug: 'philippines', name: 'Philippines', flag: '🇵🇭' },
  { slug: 'malaysia', name: 'Malaysia', flag: '🇲🇾' },
  { slug: 'singapore', name: 'Singapore', flag: '🇸🇬' },
  { slug: 'viet-nam', name: 'Vietnam', flag: '🇻🇳' },
  { slug: 'ba-lan', name: 'Poland', flag: '🇵🇱' },
  { slug: 'ha-lan', name: 'Netherlands', flag: '🇳🇱' },
  { slug: 'na-uy', name: 'Norway', flag: '🇳🇴' },
  { slug: 'thuy-dien', name: 'Sweden', flag: '🇸🇪' },
  { slug: 'dan-mach', name: 'Denmark', flag: '🇩🇰' },
  { slug: 'bo-dao-nha', name: 'Portugal', flag: '🇵🇹' },
  { slug: 'argentina', name: 'Argentina', flag: '🇦🇷' },
  { slug: 'colombia', name: 'Colombia', flag: '🇨🇴' },
  { slug: 'nam-phi', name: 'South Africa', flag: '🇿🇦' },
  { slug: 'chau-phi', name: 'Africa', flag: '🌍' },
  { slug: 'uae', name: 'Middle East / UAE', flag: '🇦🇪' },
  { slug: 'quoc-gia-khac', name: 'Other Countries', flag: '🌐' },
];

export function normalizeImage(url, cdn = PHIM_CDN) {
  const s = String(url || '').trim();
  if (!s) return '';
  if (s.startsWith('http://') || s.startsWith('https://')) return s;
  if (s.startsWith('//')) return `https:${s}`;
  return `${cdn.replace(/\/+$/, '')}/${s.replace(/^\/+/, '')}`;
}

export function stripHtml(raw) {
  return String(raw || '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

export async function fetchTextSafe(url, timeoutMs = 8000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'ChillFilm-NKI/3.3 (+https://chillfilm.vercel.app)' },
    });
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export async function fetchJsonSafe(url, timeoutMs = 8000) {
  const txt = await fetchTextSafe(url, timeoutMs);
  if (!txt) return null;
  try {
    return JSON.parse(txt);
  } catch {
    return null;
  }
}

export async function getCustomBlockedTerms() {
  try {
    const raw = await getVaultConfigValue('blocked_terms');
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map((s) => String(s || '').trim()).filter(Boolean) : [];
  } catch {
    return [];
  }
}

export async function setCustomBlockedTerms(terms) {
  const clean = [...new Set((Array.isArray(terms) ? terms : []).map((s) => String(s || '').trim()).filter((s) => s.length >= 2))].slice(0, 200);
  await setVaultConfigValue('blocked_terms', clean.length ? JSON.stringify(clean) : '');
  return clean;
}

let _dynamicSourcesCache = null; // { all, expiresAt }
const SOURCES_CACHE_TTL_MS = 60_000;

export function invalidateDynamicSourcesCache() {
  _dynamicSourcesCache = null;
}

export async function listDynamicSources({ onlyEnabled = true, skipOffline = false, bypassCache = false } = {}) {
  try {
    let allMapped = null;
    if (!bypassCache && _dynamicSourcesCache && _dynamicSourcesCache.expiresAt > Date.now()) {
      allMapped = _dynamicSourcesCache.all;
    } else {
      await ensureSchema();
      const sql = getSql();
      const rows = await sql`
        SELECT id, name, source_type, base_url, country_hint, enabled, priority, created_at,
               is_builtin, last_checked_at, last_status, last_latency_ms, last_error,
               consecutive_failures, items_crawled, total_remote
        FROM nki_sources
        ORDER BY priority ASC, created_at DESC
        LIMIT 60
      `;
      allMapped = rows.map((r) => ({
        id: r.id,
        name: r.name,
        sourceType: r.source_type,
        baseUrl: r.base_url,
        countryHint: r.country_hint,
        enabled: Boolean(r.enabled),
        priority: Number(r.priority || 50),
        createdAt: Number(r.created_at || 0),
        isBuiltin: Boolean(r.is_builtin),
        lastCheckedAt: Number(r.last_checked_at || 0),
        lastStatus: String(r.last_status || 'PENDING'),
        lastLatencyMs: Number(r.last_latency_ms || 0),
        lastError: String(r.last_error || ''),
        consecutiveFailures: Number(r.consecutive_failures || 0),
        itemsCrawled: Number(r.items_crawled || 0),
        totalRemote: Number(r.total_remote || 0),
      }));
      _dynamicSourcesCache = { all: allMapped, expiresAt: Date.now() + SOURCES_CACHE_TTL_MS };
    }
    let out = onlyEnabled ? allMapped.filter((s) => s.enabled) : allMapped;
    if (skipOffline) {
      out = out.filter((s) => s.consecutiveFailures < 3 && s.lastStatus !== 'OFFLINE');
    }
    return out;
  } catch {
    return [];
  }
}

/* ==================== 1. KKPHIM / PHIMAPI COMPATIBLE PARSER ==================== */
export function mapPhimItem(m, { cdnDomain = PHIM_CDN, source = 'phimapi', sourceId = '', sourceName = 'KKPhim' } = {}) {
  if (!m || !m.slug || !m.name) return null;
  const categories = Array.isArray(m.category)
    ? m.category.map((c) => c?.name).filter(Boolean).slice(0, 3)
    : [];
  const countries = Array.isArray(m.country)
    ? m.country.map((c) => c?.name).filter(Boolean).slice(0, 2)
    : [];
  const voteAvg = Number(m.tmdb?.vote_average || m.imdb?.vote_average || 0);
  const yearNum = Number(m.year || 0);
  return {
    slug: String(m.slug).trim(),
    source,
    sourceId,
    sourceName,
    name: String(m.name).trim(),
    origin_name: String(m.origin_name || '').trim(),
    type: String(m.type || '').trim(),
    year: yearNum ? String(yearNum) : '',
    quality: String(m.quality || 'FHD').trim(),
    lang: String(m.lang || '').trim(),
    time: String(m.time || '').trim(),
    episode_current: String(m.episode_current || '').trim(),
    poster: normalizeImage(m.poster_url || m.thumb_url, cdnDomain),
    backdrop: normalizeImage(m.thumb_url || m.poster_url, cdnDomain),
    genres: categories,
    countries,
    imdbId: String(m.imdb?.id || '').startsWith('tt') ? String(m.imdb.id) : '',
    tmdbScore: Number.isFinite(voteAvg) && voteAvg > 0 ? Math.round(voteAvg * 10) / 10 : 0,
    modifiedAt: m.modified?.time || m.modified || '',
    content: '',
  };
}

export async function fetchPhimApiList(url, blockedTerms = [], opts = {}) {
  const json = await fetchJsonSafe(url, opts.timeoutMs || 6500);
  if (!json) return [];
  const cdn = json?.data?.APP_DOMAIN_CDN_IMAGE || opts.cdnDomain || PHIM_CDN;
  const raw = json?.items || json?.data?.items || [];
  const mapped = raw.map((item) => mapPhimItem(item, { ...opts, cdnDomain: cdn })).filter(Boolean);
  return filterSafeMovies(mapped, blockedTerms);
}

/* ==================== 2. INTERNET ARCHIVE GLOBAL CINEMA (29,600+ LEGAL FILMS) ==================== */
export async function fetchArchiveCinema({ query = '', country = '', page = 1, rows = 20, blockedTerms = [] } = {}) {
  const countryTerms = {
    'au-my': '(subject:american OR language:eng)',
    anh: '(subject:british OR subject:england OR subject:uk)',
    phap: '(subject:french OR language:fre OR language:fra)',
    y: '(subject:italian OR language:ita)',
    duc: '(subject:german OR language:ger OR language:deu)',
    'tay-ban-nha': '(subject:spanish OR language:spa)',
    'nhat-ban': '(subject:japanese OR subject:japan OR language:jpn)',
    'han-quoc': '(subject:korean OR subject:korea OR language:kor)',
    'trung-quoc': '(subject:chinese OR subject:"hong kong" OR language:chi)',
    'an-do': '(subject:indian OR subject:bollywood OR subject:hindi OR language:hin)',
    'thai-lan': '(subject:thai OR subject:thailand OR language:tha)',
    nga: '(subject:russian OR subject:soviet OR language:rus)',
  };

  let qParts = [
    'collection:(feature_films OR world_cinema OR sci_fi_thriller_films OR comedy_films OR Film_Noir OR classic_tv OR animationandcartoons)',
    'mediatype:movies',
  ];
  if (query && query.trim()) {
    const safeQ = query.trim().replace(/["'()]/g, ' ');
    qParts.push(`(title:(${safeQ}) OR description:(${safeQ}))`);
  }
  if (country && countryTerms[country]) {
    qParts.push(countryTerms[country]);
  }
  qParts.push('-subject:(adult OR erotic OR porn OR xxx OR sex)');

  const qStr = encodeURIComponent(qParts.join(' AND '));
  const sort = query ? 'downloads desc' : 'week desc';
  const url = `https://archive.org/advancedsearch.php?q=${qStr}&fl[]=identifier,title,year,description,subject&sort[]=${encodeURIComponent(sort)}&rows=${rows}&page=${Math.max(1, page)}&output=json`;
  const json = await fetchJsonSafe(url, 8000);
  const docs = json?.response?.docs || [];

  const mapped = docs
    .filter((d) => d && d.identifier && d.title)
    .map((d) => {
      const id = String(d.identifier).trim();
      const subjects = Array.isArray(d.subject)
        ? d.subject.map(String)
        : d.subject
          ? [String(d.subject)]
          : [];
      const thumb = `https://archive.org/services/img/${encodeURIComponent(id)}`;
      return {
        slug: id,
        source: 'archive',
        sourceId: 'archive_builtin',
        sourceName: 'Internet Archive',
        name: String(d.title).trim(),
        origin_name: 'World Public Cinema',
        type: 'single',
        year: d.year ? String(d.year).slice(0, 4) : '',
        quality: 'HD • Free',
        episode_current: 'Full',
        poster: thumb,
        backdrop: thumb,
        genres: subjects.filter((s) => s.length <= 24).slice(0, 3),
        countries: ['Global'],
        imdbId: '',
        tmdbScore: 0,
        content: stripHtml(Array.isArray(d.description) ? d.description[0] : d.description || '').slice(0, 240),
      };
    });

  return filterSafeMovies(mapped, blockedTerms);
}

export async function resolveArchiveDetail(identifier, blockedTerms = []) {
  const cleanId = String(identifier || '').trim();
  if (!cleanId) return null;
  const meta = await fetchJsonSafe(`https://archive.org/metadata/${encodeURIComponent(cleanId)}`, 8500);
  if (!meta || !meta.metadata) return null;

  const md = meta.metadata;
  const subjects = Array.isArray(md.subject) ? md.subject.map(String) : md.subject ? [String(md.subject)] : [];
  const candidate = {
    slug: cleanId,
    name: String(md.title || cleanId),
    origin_name: String(md.creator || 'World Public Cinema'),
    genres: subjects,
    content: stripHtml(Array.isArray(md.description) ? md.description[0] : md.description || ''),
  };
  if (isBlockedContent(candidate, blockedTerms)) {
    return { blocked: true };
  }

  const files = Array.isArray(meta.files) ? meta.files : [];
  const mp4Files = files
    .filter((f) => f && typeof f.name === 'string' && /\.mp4$/i.test(f.name) && !/\.ia\.mp4$/i.test(f.name) && Number(f.size || 0) > 2_000_000)
    .sort((a, b) => Number(b.size || 0) - Number(a.size || 0));
  const fallbackMp4 = mp4Files.length
    ? mp4Files
    : files.filter((f) => f && typeof f.name === 'string' && /\.mp4$/i.test(f.name));

  const subFiles = files
    .filter((f) => f && typeof f.name === 'string' && /\.(srt|vtt)$/i.test(f.name))
    .slice(0, 6)
    .map((f, idx) => ({
      id: `arch_sub_${idx}`,
      label: f.name,
      langCode: 'en',
      url: `https://archive.org/download/${encodeURIComponent(cleanId)}/${encodeURI(f.name)}`,
    }));

  const episodes = fallbackMp4.slice(0, 8).map((f, idx) => ({
    name: fallbackMp4.length === 1 ? 'Full' : `Part ${idx + 1}`,
    slug: fallbackMp4.length === 1 ? 'full' : `tap-${idx + 1}`,
    link_m3u8: `https://archive.org/download/${encodeURIComponent(cleanId)}/${encodeURI(f.name)}`,
    subtitles: subFiles,
  }));

  const thumb = `https://archive.org/services/img/${encodeURIComponent(cleanId)}`;
  return {
    slug: cleanId,
    source: 'archive',
    name: candidate.name,
    origin_name: candidate.origin_name,
    year: md.year ? String(md.year).slice(0, 4) : '',
    quality: 'HD • Archive',
    time: String(md.runtime || ''),
    content: candidate.content,
    imdbId: '',
    type: episodes.length > 1 ? 'series' : 'single',
    poster: thumb,
    backdrop: thumb,
    servers: episodes.length ? [{ server_name: 'Internet Archive Direct', episodes }] : [],
  };
}

/* ==================== 3. MACCMS v10 JSON CONNECTOR (ASIAN & GLOBAL VOD ECOSYSTEM) ==================== */
const MACCMS_AREA_MAP = {
  '美国': 'US',
  '英国': 'UK',
  '韩国': 'South Korea',
  '日本': 'Japan',
  '泰国': 'Thailand',
  '中国大陆': 'China',
  '大陆': 'China',
  '内地': 'China',
  '中国香港': 'Hong Kong',
  '香港': 'Hong Kong',
  '中国台湾': 'Taiwan',
  '台湾': 'Taiwan',
  '法国': 'France',
  '德国': 'Germany',
  '意大利': 'Italy',
  '西班牙': 'Spain',
  '印度': 'India',
  '加拿大': 'Canada',
  '澳大利亚': 'Australia',
  '俄罗斯': 'Russia',
  '波兰': 'Poland',
  '瑞典': 'Sweden',
  '挪威': 'Norway',
  '丹麦': 'Denmark',
  '巴西': 'Brazil',
  '墨西哥': 'Mexico',
  '土耳其': 'Turkey',
  '新加坡': 'Singapore',
  '马来西亚': 'Malaysia',
  '越南': 'Vietnam',
  '欧美': 'US / Europe',
  '其他': 'Global',
};

const MACCMS_GENRE_MAP = {
  '动作片': 'Action',
  '喜剧片': 'Comedy',
  '爱情片': 'Romance',
  '科幻片': 'Sci-Fi',
  '恐怖片': 'Horror',
  '剧情片': 'Drama',
  '战争片': 'War',
  '犯罪片': 'Crime',
  '悬疑片': 'Mystery',
  '惊悚片': 'Thriller',
  '纪录片': 'Documentary',
  '记录片': 'Documentary',
  '动画电影': 'Animated Film',
  '动漫电影': 'Animated Film',
  '日韩动漫': 'Anime',
  '国产动漫': 'Animation',
  '欧美动漫': 'Animation',
  '韩国剧': 'K-Drama',
  '韩剧': 'K-Drama',
  '日本剧': 'J-Drama',
  '日剧': 'J-Drama',
  '欧美剧': 'Western Series',
  '泰国剧': 'Thai Drama',
  '泰剧': 'Thai Drama',
  '国产剧': 'C-Drama',
  '香港剧': 'HK Drama',
  '台湾剧': 'TW Drama',
  '海外剧': 'Global Series',
};

function normalizeMacCmsArea(raw, fallback = 'Global') {
  const parts = String(raw || '')
    .split(/[/,、\s]+/)
    .map((p) => p.trim())
    .filter(Boolean);
  const mapped = parts.map((p) => MACCMS_AREA_MAP[p] || p);
  const unique = [...new Set(mapped)].slice(0, 2);
  return unique.length ? unique : [fallback || 'Global'];
}

function normalizeMacCmsGenres(typeName, vodClass) {
  const rawList = [typeName, ...(String(vodClass || '').split(/[,/]/))]
    .map((s) => String(s || '').trim())
    .filter(Boolean);
  const mapped = rawList.map((g) => MACCMS_GENRE_MAP[g] || g);
  return [...new Set(mapped)].slice(0, 3);
}

export async function fetchMacCmsSourceWithStats(source, { query = '', page = 1, blockedTerms = [], timeoutMs = 8500 } = {}) {
  if (!isSafeSourceUrl(source.baseUrl)) {
    return { ok: false, items: [], rawCount: 0, blockedCount: 0, totalRemote: 0, error: 'Blocked URL' };
  }
  const sep = source.baseUrl.includes('?') ? '&' : '?';
  const qParam = query ? `&wd=${encodeURIComponent(query.trim())}` : '';
  const url = `${source.baseUrl}${sep}ac=detail&pg=${Math.max(1, page)}${qParam}`;
  const json = await fetchJsonSafe(url, timeoutMs);
  if (!json || !Array.isArray(json.list)) {
    return { ok: false, items: [], rawCount: 0, blockedCount: 0, totalRemote: 0, error: 'Invalid JSON / timeout' };
  }
  const list = json.list;
  const totalRemote = Number(json.total || 0) || 0;

  const mapped = list
    .filter((v) => v && v.vod_id && v.vod_name && String(v.vod_play_url || '').includes('.m3u8'))
    .map((v) => {
      const enSub = String(v.vod_sub || '').trim();
      const originLabel = enSub && !/^[\s,]+$/.test(enSub) ? enSub : String(v.vod_en || '').trim();
      const remarks = String(v.vod_remarks || '').trim();
      const isSingle = /hd|tc|ts|bd|1080|4k|正片|full|完结电影/i.test(remarks) || String(v.type_name || '').includes('片');
      return {
        slug: String(v.vod_id),
        source: 'maccms',
        sourceId: source.id,
        sourceName: source.name,
        name: String(v.vod_name).trim(),
        origin_name: originLabel,
        type: isSingle ? 'single' : 'series',
        year: String(v.vod_year || '').trim().slice(0, 4),
        quality: (remarks || 'HD').slice(0, 18),
        episode_current: remarks.slice(0, 20),
        poster: normalizeImage(v.vod_pic || '', source.baseUrl),
        backdrop: normalizeImage(v.vod_pic_slide || v.vod_pic || '', source.baseUrl),
        genres: normalizeMacCmsGenres(v.type_name, v.vod_class),
        // Preserve raw Chinese type_name for Hard VN-Safe anti-porn inspection!
        type_name: String(v.type_name || ''),
        category: [v.type_name, ...(String(v.vod_class || '').split(','))].filter(Boolean),
        countries: normalizeMacCmsArea(v.vod_area, source.countryHint),
        imdbId: '',
        tmdbScore: Number(v.vod_douban_score || v.vod_score || 0) || 0,
        content: stripHtml(v.vod_content || v.vod_blurb || '').slice(0, 260),
      };
    });

  const safe = filterSafeMovies(mapped, blockedTerms).map(({ type_name, category, ...pub }) => pub);
  return {
    ok: true,
    items: safe,
    rawCount: mapped.length,
    blockedCount: Math.max(0, mapped.length - safe.length),
    totalRemote,
    error: '',
  };
}

export async function fetchMacCmsSource(source, { query = '', page = 1, blockedTerms = [] } = {}) {
  const res = await fetchMacCmsSourceWithStats(source, { query, page, blockedTerms });
  return res.items;
}

export async function resolveMacCmsDetail(source, vodId, blockedTerms = []) {
  if (!source || !isSafeSourceUrl(source.baseUrl)) return null;
  const sep = source.baseUrl.includes('?') ? '&' : '?';
  const url = `${source.baseUrl}${sep}ac=detail&ids=${encodeURIComponent(vodId)}`;
  const json = await fetchJsonSafe(url, 8500);
  const v = Array.isArray(json?.list) ? json.list[0] : null;
  if (!v) return null;

  const checkObj = {
    slug: String(v.vod_id || vodId),
    name: String(v.vod_name || ''),
    origin_name: String(v.vod_en || v.vod_sub || ''),
    type_name: String(v.type_name || ''),
    genres: [v.type_name, ...(String(v.vod_class || '').split(','))].filter(Boolean),
    content: stripHtml(v.vod_content || v.vod_blurb || ''),
  };
  if (isBlockedContent(checkObj, blockedTerms)) return { blocked: true };

  const playFroms = String(v.vod_play_from || 'Server 1').split('$$$');
  const playUrlGroups = String(v.vod_play_url || '').split('$$$');
  const servers = [];
  for (let i = 0; i < playUrlGroups.length; i++) {
    const groupStr = playUrlGroups[i] || '';
    const eps = [];
    for (const entry of groupStr.split('#')) {
      const parts = entry.split('$');
      const link = (parts.length >= 2 ? parts[1] : parts[0] || '').trim();
      const epName = (parts.length >= 2 ? parts[0] : `Ep ${eps.length + 1}`).trim();
      if (link.startsWith('http') && (link.includes('.m3u8') || link.includes('.mp4'))) {
        eps.push({ name: epName || `Ep ${eps.length + 1}`, slug: `tap-${eps.length + 1}`, link_m3u8: link });
      }
    }
    if (eps.length) {
      servers.push({ server_name: playFroms[i] || `${source.name} #${i + 1}`, episodes: eps });
    }
  }

  const parsePeople = (raw) =>
    String(raw || '')
      .split(/[,，、/|]+/)
      .map((x) => x.trim())
      .filter((x) => x && x.length <= 60 && !/^unknown|n\/a|đang cập nhật$/i.test(x))
      .slice(0, 12);

  return {
    slug: String(v.vod_id || vodId),
    source: 'maccms',
    sourceId: source.id,
    name: checkObj.name,
    origin_name: checkObj.origin_name,
    year: String(v.vod_year || ''),
    quality: String(v.vod_remarks || 'HD'),
    episode_current: String(v.vod_remarks || (servers[0]?.episodes?.length ? `Tập ${servers[0].episodes.length}` : 'HD')),
    time: String(v.vod_duration || ''),
    content: checkObj.content,
    imdbId: '',
    tmdbScore: Number(v.vod_douban_score || v.vod_score || 0) || 0,
    genres: checkObj.genres.slice(0, 5),
    countries: String(v.vod_area || '').split(/[,，/]+/).map((s) => s.trim()).filter(Boolean).slice(0, 3),
    actors: parsePeople(v.vod_actor),
    directors: parsePeople(v.vod_director),
    trailerUrl: '',
    type: servers[0]?.episodes?.length > 1 ? 'series' : 'single',
    poster: normalizeImage(v.vod_pic || '', source.baseUrl),
    backdrop: normalizeImage(v.vod_pic_slide || v.vod_pic || '', source.baseUrl),
    servers,
  };
}

/* ==================== 4. M3U8 CLEAN MOVIE PLAYLIST CONNECTOR ==================== */
export async function fetchM3uCinemaList(m3uUrl, { sourceId = 'iptv_movies', sourceName = 'Live Cinema', limit = 24, query = '', blockedTerms = [] } = {}) {
  if (!isSafeSourceUrl(m3uUrl)) return [];
  const raw = await fetchTextSafe(m3uUrl, 8500);
  if (!raw) return [];
  const lines = raw.split(/\r?\n/);
  const items = [];
  const qLower = query.trim().toLowerCase();
  let meta = null;

  for (const lineRaw of lines) {
    const line = lineRaw.trim();
    if (!line) continue;
    if (line.startsWith('#EXTINF')) {
      const logoMatch = line.match(/tvg-logo="([^"]*)"/i);
      const groupMatch = line.match(/group-title="([^"]*)"/i);
      const commaIdx = line.lastIndexOf(',');
      const title = commaIdx >= 0 ? line.slice(commaIdx + 1).trim() : 'Movie Channel';
      meta = {
        title,
        logo: logoMatch ? logoMatch[1] : '',
        group: groupMatch ? groupMatch[1] : 'Movies',
      };
    } else if (!line.startsWith('#') && /^https?:\/\//i.test(line) && meta) {
      const grp = meta.group || 'Movies';
      if (!isBlockedCategoryTag(grp)) {
        if (!qLower || meta.title.toLowerCase().includes(qLower) || grp.toLowerCase().includes(qLower)) {
          items.push({
            slug: `m3u_${Buffer.from(line).toString('base64url').slice(0, 48)}`,
            source: 'direct',
            sourceId,
            sourceName,
            streamUrl: line,
            name: meta.title,
            origin_name: `${sourceName} • ${grp}`,
            type: 'live',
            year: 'LIVE',
            quality: 'LIVE',
            episode_current: 'LIVE',
            poster: meta.logo || '',
            backdrop: meta.logo || '',
            genres: [grp],
            groupTitle: grp,
            countries: ['Global'],
            imdbId: '',
            tmdbScore: 0,
            content: `Live public cinema channel (${grp})`,
          });
        }
      }
      meta = null;
      if (items.length >= limit) break;
    }
  }
  return filterSafeMovies(items, blockedTerms);
}

/* ==================== 5. FETCH ANY DYNAMIC ADMIN SOURCE ==================== */
export async function fetchFromDynamicSource(source, { query = '', page = 1, blockedTerms = [] } = {}) {
  if (!source || !source.enabled || !isSafeSourceUrl(source.baseUrl)) return [];
  const base = source.baseUrl.replace(/\/+$/, '');

  if (source.sourceType === 'MACCMS_JSON') {
    return fetchMacCmsSource(source, { query, page, blockedTerms });
  }
  if (source.sourceType === 'M3U_PLAYLIST') {
    return fetchM3uCinemaList(source.baseUrl, {
      sourceId: source.id,
      sourceName: source.name,
      limit: 24,
      query,
      blockedTerms,
    });
  }
  if (source.sourceType === 'PHIMAPI_JSON') {
    const url = query
      ? `${base}/v1/api/tim-kiem?keyword=${encodeURIComponent(query)}&limit=20`
      : `${base}/danh-sach/phim-moi-cap-nhat?page=${Math.max(1, page)}`;
    return fetchPhimApiList(url, blockedTerms, {
      source: 'phimapi_custom',
      sourceId: source.id,
      sourceName: source.name,
      cdnDomain: base,
    });
  }
  if (source.sourceType === 'CUSTOM_JSON') {
    const json = await fetchJsonSafe(source.baseUrl, 8000);
    const arr = Array.isArray(json) ? json : (json?.items || json?.movies || json?.data?.items || []);
    const qLower = query.trim().toLowerCase();
    const mapped = arr
      .filter((m) => m && (m.name || m.title))
      .map((m, idx) => ({
        slug: String(m.slug || m.id || `custom_${idx}`),
        source: m.streamUrl || m.link_m3u8 ? 'direct' : 'custom_json',
        sourceId: source.id,
        sourceName: source.name,
        streamUrl: String(m.streamUrl || m.link_m3u8 || ''),
        name: String(m.name || m.title).trim(),
        origin_name: String(m.origin_name || m.originalTitle || '').trim(),
        type: String(m.type || 'single'),
        year: String(m.year || ''),
        quality: String(m.quality || 'HD'),
        episode_current: String(m.episode_current || ''),
        poster: normalizeImage(m.poster || m.poster_url || m.thumb_url || '', base),
        backdrop: normalizeImage(m.backdrop || m.thumb_url || m.poster_url || '', base),
        genres: Array.isArray(m.genres) ? m.genres : [],
        countries: [String(m.country || source.countryHint || 'Global')],
        imdbId: String(m.imdbId || ''),
        tmdbScore: Number(m.tmdbScore || 0) || 0,
        content: stripHtml(m.content || m.description || '').slice(0, 260),
      }))
      .filter((m) => !qLower || `${m.name} ${m.origin_name}`.toLowerCase().includes(qLower));
    return filterSafeMovies(mapped, blockedTerms);
  }
  return [];
}
