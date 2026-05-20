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
  _db.settings({ preferRest: true });
  return _db;
}
