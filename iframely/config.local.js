// iframely runtime configuration for the Cloud Run self-hosted deployment.
//
// GROUP_LINKS: true — required for the structured links.thumbnail / links.icon
//   shape that IframelyAdapter expects. Without this flag, iframely returns a
//   flat links array and the thumbnail lookup will not work.
//
// SKIP_IFRAMELY_RENDERS: true — skips Puppeteer-based screenshot rendering.
//   Not available on Alpine without a Chromium install, and not needed for
//   metadata-only OG enrichment (title + image from og: tags).
//
// CACHE_ENGINE / CACHE_TTL — in-process node-cache avoids redundant fetches
//   when the backfill hits the same domain multiple times. TTL of 1 hour is
//   appropriate for a personal-use deployment; Cloud Run's max-instance=0
//   means the cache is ephemeral across cold starts.
//
// IGNORE_DOMAINS_RE (iframely default) is kept on — it already blocks
//   169.254.169.254 (GCP metadata) and localhost. Defence-in-depth on top of
//   IframelyAdapter's assertSafeHop gate and our isSafePublicUrl re-validation
//   of the returned image URL.

module.exports = {
  GROUP_LINKS: true,
  SKIP_IFRAMELY_RENDERS: true,
  CACHE_ENGINE: 'node-cache',
  CACHE_TTL: 3600,
};
