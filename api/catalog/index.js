import { optionsResponse } from '../_lib/auth.js';
import {
  handleCatalogBrowse,
  handleCatalogDetail,
  handleCatalogFeed,
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
  return handleCatalogFeed(request);
}
