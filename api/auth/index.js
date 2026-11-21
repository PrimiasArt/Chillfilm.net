import { jsonResponse, optionsResponse } from '../_lib/auth.js';
import { handleAuthLogin, handleAuthMe, handleAuthRegister } from '../_lib/authHandlers.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  const op = new URL(request.url).searchParams.get('op') || 'me';
  if (op === 'me') return handleAuthMe(request);
  return jsonResponse({ error: `Unsupported GET auth op: ${op}` }, 405);
}

export async function POST(request) {
  const op = new URL(request.url).searchParams.get('op') || 'login';
  if (op === 'login') return handleAuthLogin(request);
  if (op === 'register') return handleAuthRegister(request);
  return jsonResponse({ error: `Unsupported POST auth op: ${op}` }, 405);
}
