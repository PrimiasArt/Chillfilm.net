import { handleSchedule } from '@vercel/schedules';
import { runCatalogCrawlAndHealthCheck } from '../_lib/crawler.js';

/**
 * Private Vercel Scheduled Function (`schedule/v1beta`).
 * Runs every 3 hours (Asia/Ho_Chi_Minh) to verify all global movie sources,
 * auto-pause offline upstreams, recover restored ones, and crawl newly released
 * VN-Safe films into Neon Postgres.
 */
export const POST = handleSchedule(async (metadata) => {
  console.log(
    `[schedule:crawl] start ${metadata.name} exec=${metadata.executionId} scheduledAt=${metadata.scheduledAt.toISOString()}`
  );
  const report = await runCatalogCrawlAndHealthCheck({
    triggerType: 'SCHEDULE',
    executionId: metadata.executionId,
    scheduledAt: metadata.scheduledAt,
  });
  console.log(
    `[schedule:crawl] done exec=${metadata.executionId} healthy=${report.sourcesHealthy}/${report.sourcesChecked} new=${report.itemsNew} blocked=${report.itemsBlockedSfw} total=${report.totalIndexed}`
  );
});
