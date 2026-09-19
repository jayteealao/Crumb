// X (Twitter) v2 API constants + URL builder used by lib/poll.ts.
//
// IMPORTANT: the field-list constants below are copied verbatim from the Android
// client at feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterApiService.kt:49-59.
// Until the cutover-migration slice removes the Android-side client, BOTH COPIES
// MUST STAY IN SYNC. Adding a field here without mirroring it on the Android
// side (or vice versa) silently drops data on the un-updated reader.

export const TWEETFIELDS =
  "id,in_reply_to_user_id,lang,entities,created_at,attachments,author_id,context_annotations,conversation_id,public_metrics,referenced_tweets,text,edit_history_tweet_ids,edit_controls,note_tweet,reply_settings,possibly_sensitive";

export const EXPANSIONS =
  "attachments.media_keys,attachments.poll_ids,author_id,entities.mentions.username,in_reply_to_user_id,referenced_tweets.id,referenced_tweets.id.author_id,edit_history_tweet_ids";

export const MEDIAFIELDS =
  "alt_text,media_key,url,type,public_metrics,preview_image_url,height,duration_ms,width,variants";

export const USERFIELDS =
  "id,profile_image_url,name,username,verified,verified_type,description,created_at,location";

// X v2 bookmarks endpoint has an active pagination bug at max_results=100 (as
// of May 2026, per devcommunity.x.com/t/bookmarks-api-v2-stops-paginating-after-3-pages).
// 50 is the documented workaround.
export const MAX_RESULTS = 50;

export const X_API_BASE = "https://api.x.com/2";

export const USERS_ME_URL = `${X_API_BASE}/users/me`;

export const TOKEN_URL = `${X_API_BASE}/oauth2/token`;

export function buildBookmarksUrl(xUserId: string, paginationToken?: string): string {
  const params = new URLSearchParams({
    "tweet.fields": TWEETFIELDS,
    expansions: EXPANSIONS,
    "media.fields": MEDIAFIELDS,
    "user.fields": USERFIELDS,
    max_results: String(MAX_RESULTS),
  });
  if (paginationToken) params.set("pagination_token", paginationToken);
  return `${X_API_BASE}/users/${encodeURIComponent(xUserId)}/bookmarks?${params.toString()}`;
}
