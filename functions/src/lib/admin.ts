import { getApps, initializeApp, type App } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";

let _app: App | null = null;
let _db: Firestore | null = null;

export function app(): App {
  if (_app) return _app;
  _app = getApps().length ? getApps()[0]! : initializeApp();
  return _app;
}

export function db(): Firestore {
  if (_db) return _db;
  _db = getFirestore(app());
  // ignoreUndefinedProperties: silently drop keys whose value is `undefined`
  // when batch/set is invoked. Without this, the X-API pass-through writers
  // in poll.ts (camelCase aliases overlaying optional snake_case fields like
  // pinned_tweet_id, verified_type, in_reply_to_user_id) throw
  //   `Cannot use "undefined" as a Firestore value (found in field "<name>")`
  // for every X object that omits the optional field, aborting the whole poll
  // batch.
  _db.settings({ preferRest: true, ignoreUndefinedProperties: true });
  return _db;
}
