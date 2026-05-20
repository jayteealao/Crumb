import { setGlobalOptions } from "firebase-functions/v2/options";

setGlobalOptions({ region: "europe-west2", maxInstances: 10 });

export { mintOAuthState } from "./handlers/mintOAuthState";
export { oauthCallback } from "./handlers/oauthCallback";
export { warmUp } from "./handlers/warmUp";
export { dailyPoll } from "./handlers/dailyPoll";
export { triggerPoll } from "./handlers/triggerPoll";
