import { setGlobalOptions } from "firebase-functions/v2/options";

setGlobalOptions({
  region: "europe-west2",
  maxInstances: 10,
  serviceAccount: "crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com",
});

export { mintOAuthState } from "./handlers/mintOAuthState";
export { oauthCallback } from "./handlers/oauthCallback";
export { warmUp } from "./handlers/warmUp";
export { dailyPoll } from "./handlers/dailyPoll";
export { triggerPoll } from "./handlers/triggerPoll";
export { migrateXToken } from "./handlers/migrateXToken";
export { disconnectX } from "./handlers/disconnectX";
export { enrichTweetLinks } from "./handlers/enrichLinks";
export { backfillTweetLinks } from "./handlers/backfillLinks";
export { backfillQuotedTweets } from "./handlers/backfillQuotedTweets";
export { deleteAccount } from "./handlers/deleteAccount";
