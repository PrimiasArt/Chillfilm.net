/**
 * Hard Anti-Porn & Vietnam-Safe Content Filter (SFW Guard)
 *
 * Policy:
 * - ALLOWS legitimate mainstream 18+ / R / TV-MA cinema (action, horror, thriller, drama
 *   such as Deadpool, John Wick, Oppenheimer, Exhuma, Squid Game, Game of Thrones, etc.).
 * - STRICTLY BLOCKS:
 *   1) Pornography, phim sex, JAV, Hentai, phim cấp 3 / adult-only categories (`phim-18`,
 *      `xxx`, `porn`, `jav`, `hentai`, `erotica`, `伦理片`, `福利`, `里番`, etc.).
 *   2) Explicit adult metadata flags (`adult: true`, `NC-17` / `X` / `XXX`).
 *   3) Films officially prohibited / banned from release in Vietnam (e.g. nine-dash line /
 *      sovereignty violations or explicit banned releases) + Admin custom blocklist!
 */

const BLOCKED_CATEGORY_SLUGS = new Set([
  'phim-18',
  '18',
  '18+',
  'adult',
  'adults',
  'adult-only',
  'xxx',
  'porn',
  'pornography',
  'porno',
  'jav',
  'hentai',
  'ecchi-18',
  'erotic',
  'erotica',
  'softcore',
  'sex',
  'phim-sex',
  'phim-cap-3',
  'cap-3',
  'phim-nguoi-lon',
  'phim-khieu-dam',
  'khieu-dam',
  'clip-nong',
  'uncensored',
  'av',
  'cam',
  'webcam',
  'onlyfans',
  'luan-ly',
  '伦理片',
  '伦理',
  '福利',
  '福利片',
  '情色',
  '色情',
  '里番',
  '成人',
  '无码',
  '有码',
  '無修正',
  'アダルト',
]);

// Known mainstream legal titles containing the word "sex" that must NOT be falsely blocked
const MAINSTREAM_SAFE_TITLE_WHITELIST = [
  /\bsex\s+education\b/i,
  /\bsex\s+and\s+the\s+city\b/i,
  /\bsex\s+tape\b/i,
  /\bsussex\b/i,
  /\bessex\b/i,
  /\bmiddlesex\b/i,
  /\bsextant\b/i,
];

// Explicit pornography, JAV studio codes, phim cấp 3 & adult-only patterns
const EXPLICIT_PORN_REGEX = new RegExp(
  [
    '\\b(?:porn|porno|pornhub|xvideos|xnxx|xhamster|brazzers|bangbros|naughtyamerica|realitykings|onlyfans|camgirl|chaturbate|stripchat)\\b',
    '\\b(?:hentai|jav\\s*hd|jav\\s*sub|av\\s*idol|uncensored\\s*jav|softcore|erotica|xxx\\b)',
    '\\b(?:fc2[-_\\s]?ppv|ssis|stars|ipx|ipzz|mide|midv|abp|abw|pred|juq|jul|sone|sdde|snis|meyd|dasd|kawd|ebod|pppd|rbd|shkd|atid|adn|miaa|miab|cawd|venx|blk|hmn|dldss|fsdss|1pondo|caribbeancom|heyzo|tokyo[-_\\s]?hot)[-_\\s]?\\d{2,6}\\b',
    '(?:phim\\s*sex|phim\\s*heo|phim\\s*cấp\\s*3|phim\\s*cap\\s*3|phim\\s*khiêu\\s*dâm|phim\\s*khieu\\s*dam|clip\\s*nóng|clip\\s*nong|sex\\s*không\\s*che|jav\\s*không\\s*che|phim\\s*người\\s*lớn|phim\\s*18\\s*cấp\\s*3)',
    '(?:无码|有码|中出|巨乳|人妻|痴汉|里番|色情片|三级片|倫理片|伦理片|福利片|アダルト|無修正)',
  ].join('|'),
  'i'
);

// Built-in list of titles officially prohibited/banned from release in Vietnam
// (e.g. nine-dash line sovereignty violations / Vietnam Cinema Department bans)
const BUILTIN_VN_BANNED_SLUGS_AND_TITLES = [
  'abominable',
  'everest-nguoi-tuyet-be-nho',
  'uncharted',
  'tho-san-co-vat',
  'barbie-2023',
  'flight-to-you',
  'huong-gio-ma-di',
  'pine-gap',
  'put-your-head-on-my-shoulder',
  'gui-thoi-thanh-xuan-am-ap',
  'love-is-true',
  'madam-secretary',
  'the-vietnam-war-ban',
  'fifty-shades-of-grey-uncut-xxx',
];

const BLOCKED_SOURCE_DOMAINS = [
  'pornhub',
  'xvideos',
  'xnxx',
  'xhamster',
  'redtube',
  'youporn',
  'spankbang',
  'missav',
  'javhd',
  'jable',
  'hanime',
  'hentai',
  'nhentai',
  'vlxx',
  'sextop1',
  'phimsex',
  'heovl',
  'xxnx',
  'onlyfans',
  'chaturbate',
  'stripchat',
  'brazzers',
  'avgle',
  'netflav',
  'javguru',
  'supjav',
];

function stripDiacritics(str) {
  return String(str || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .toLowerCase()
    .trim();
}

export function isSafeSourceUrl(rawUrl) {
  try {
    const u = new URL(String(rawUrl || '').trim());
    if (u.protocol !== 'https:' && u.protocol !== 'http:') return false;
    const host = u.hostname.toLowerCase();
    // Block localhost / private LAN SSRF targets
    if (
      host === 'localhost' ||
      host.startsWith('127.') ||
      host.startsWith('10.') ||
      host.startsWith('192.168.') ||
      host.startsWith('169.254.') ||
      host === '0.0.0.0' ||
      host.endsWith('.local')
    ) {
      return false;
    }
    const full = `${host}${u.pathname.toLowerCase()}`;
    if (BLOCKED_SOURCE_DOMAINS.some((d) => full.includes(d))) return false;
    if (/(?:phim-18|\/xxx\/|\/porn\/|\/jav\/|\/hentai\/|\/adult\/)/i.test(full)) return false;
    return true;
  } catch {
    return false;
  }
}

export function isBlockedCategoryTag(rawSlugOrName) {
  const clean = stripDiacritics(rawSlugOrName);
  const rawLower = String(rawSlugOrName || '').trim().toLowerCase();
  if (!clean && !rawLower) return false;
  if (BLOCKED_CATEGORY_SLUGS.has(clean) || BLOCKED_CATEGORY_SLUGS.has(rawLower)) return true;
  if (
    clean.includes('phim 18+') ||
    clean.includes('phim-18') ||
    clean.includes('cap 3') ||
    clean.includes('khieu dam') ||
    clean.includes('nguoi lon') ||
    clean.includes('porn') ||
    clean.includes('hentai') ||
    clean.includes('erotic') ||
    clean === 'xxx' ||
    clean === 'jav' ||
    rawLower.includes('伦理') ||
    rawLower.includes('福利') ||
    rawLower.includes('色情') ||
    rawLower.includes('情色') ||
    rawLower.includes('里番') ||
    rawLower.includes('成人')
  ) {
    return true;
  }
  return false;
}

/**
 * Returns true if the movie/item MUST be blocked (porn, phim sex, JAV, hentai, phim-18,
 * or Vietnam-prohibited / Admin-blocked titles). Legitimate T18/R cinema returns false!
 */
export function isBlockedContent(item, customBlockedTerms = []) {
  if (!item || typeof item !== 'object') return true;

  // 1. Explicit adult boolean / certification flags
  if (item.adult === true || item.isAdult === true || item.tmdb?.adult === true) return true;
  const cert = String(item.certification || item.rating || '').trim().toUpperCase();
  if (cert === 'X' || cert === 'XXX' || cert === 'NC-17' || cert === 'R18' || cert === 'R18+') {
    return true;
  }

  // 2. Category / Genre / Group tags (PhimAPI's `phim-18`, MacCMS `伦理片`/`福利`, M3U `XXX`/`Adult`)
  const rawCats = [];
  if (Array.isArray(item.category)) {
    for (const c of item.category) {
      if (typeof c === 'string') rawCats.push(c);
      else if (c) {
        if (c.slug) rawCats.push(c.slug);
        if (c.name) rawCats.push(c.name);
      }
    }
  }
  if (Array.isArray(item.genres)) rawCats.push(...item.genres);
  if (typeof item.genre === 'string' && item.genre) rawCats.push(...item.genre.split(/[•,|/]/));
  if (typeof item.type_name === 'string' && item.type_name) rawCats.push(item.type_name);
  if (typeof item.groupTitle === 'string' && item.groupTitle) rawCats.push(item.groupTitle);

  for (const tag of rawCats) {
    if (isBlockedCategoryTag(tag)) return true;
  }

  // 3. Title / Slug / Description check
  const title = String(item.name || item.title || '').trim();
  const origin = String(item.origin_name || item.originalTitle || item.original_name || '').trim();
  const slug = String(item.slug || '').trim().toLowerCase();
  const desc = String(item.content || item.description || '').slice(0, 320);
  const combinedTitle = `${title} ${origin} ${slug}`;

  // Allow known safe mainstream shows (e.g. "Sex Education") unless explicitly tagged adult above
  const isWhitelistedMainstream = MAINSTREAM_SAFE_TITLE_WHITELIST.some((re) => re.test(combinedTitle));
  if (!isWhitelistedMainstream) {
    if (EXPLICIT_PORN_REGEX.test(combinedTitle)) return true;
    if (EXPLICIT_PORN_REGEX.test(desc)) return true;
  }

  // 4. Built-in Vietnam banned releases (exact slug or normalized phrase match)
  const normSlug = stripDiacritics(slug);
  const normTitle = stripDiacritics(`${title} ${origin}`);
  for (const banned of BUILTIN_VN_BANNED_SLUGS_AND_TITLES) {
    if (normSlug === banned || normSlug.startsWith(`${banned}-`)) return true;
    const phrase = banned.replace(/-/g, ' ');
    if (phrase.length >= 8 && normTitle.includes(phrase)) return true;
  }

  // 5. Admin Custom Blocklist (stored in Neon Postgres, managed via NKI Admin Vault)
  if (Array.isArray(customBlockedTerms) && customBlockedTerms.length > 0) {
    for (const rawTerm of customBlockedTerms) {
      const term = stripDiacritics(rawTerm);
      if (term.length < 3) continue;
      if (normSlug.includes(term) || normTitle.includes(term)) return true;
    }
  }

  return false;
}

export function filterSafeMovies(list, customBlockedTerms = []) {
  if (!Array.isArray(list)) return [];
  return list.filter((item) => !isBlockedContent(item, customBlockedTerms));
}
