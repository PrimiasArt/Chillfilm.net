import { ensureSchema, getServerGeminiConfig, getSql } from './_lib/db.js';
import { CORS_HEADERS, jsonResponse, optionsResponse } from './_lib/auth.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET() {
  try {
    await ensureSchema();
    const sql = getSql();
    const [countRows, aiCfg] = await Promise.all([
      sql`SELECT COUNT(*)::int AS total FROM nki_users WHERE role = 'ADMIN'`,
      getServerGeminiConfig(),
    ]);
    const hasAdmin = Number(countRows?.[0]?.total || 0) > 0;
    return new Response(
      JSON.stringify({
        ok: true,
        service: 'chillfilm-nki-cloud',
        database: 'neon-postgres',
        blobConfigured: Boolean(process.env.BLOB_READ_WRITE_TOKEN),
        serverAiReady: Boolean(aiCfg.apiKey),
        defaultModel: aiCfg.defaultModel,
        hasAdmin,
        registrationMode: hasAdmin ? aiCfg.registrationMode : 'BOOTSTRAP_ADMIN',
        serverTime: Date.now(),
      }),
      {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': 'public, s-maxage=60, stale-while-revalidate=300',
        },
      }
    );
  } catch (err) {
    console.error('[api/health] error:', err);
    return jsonResponse(
      {
        ok: false,
        error: err?.message || 'Service unavailable',
        serverTime: Date.now(),
      },
      500
    );
  }
}
