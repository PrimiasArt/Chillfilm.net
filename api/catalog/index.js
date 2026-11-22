import { optionsResponse } from '../_lib/auth.js';
import {
  handleCatalogAiSearch,
  handleCatalogBrowse,
  handleCatalogDetail,
  handleCatalogFeed,
  handleCatalogGlossary,
  handleCatalogSubProxy,
  handleCatalogSubtitles,
} from '../_lib/catalogHandlers.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  const url = new URL(request.url);
  const op = (url.searchParams.get('op') || '').toLowerCase();
  if (op === 'browse' || url.pathname.endsWith('/browse')) {
    return handleCatalogBrowse(request);
  }
  if (op === 'detail' || url.pathname.endsWith('/detail')) {
    return handleCatalogDetail(request);
  }
  if (op === 'subtitles' || url.pathname.endsWith('/subtitles')) {
    return handleCatalogSubtitles(request);
  }
  if (op === 'subproxy' || url.pathname.endsWith('/subproxy')) {
    return handleCatalogSubProxy(request);
  }
  if (op === 'glossary' || url.pathname.endsWith('/glossary')) {
    return handleCatalogGlossary(request);
  }
  if (op === 'aisearch' || url.pathname.endsWith('/ai-search')) {
    return handleCatalogAiSearch(request);
  }
  return handleCatalogFeed(request);
}

export async function POST(request) {
  const url = new URL(request.url);
  const op = (url.searchParams.get('op') || '').toLowerCase();
  if (op === 'glossary' || url.pathname.endsWith('/glossary')) {
    return handleCatalogGlossary(request);
  }
  return optionsResponse();
}
