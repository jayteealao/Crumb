// Hand-rolled in-memory Firestore fake for daily-poll / trigger-poll tests.
// Records every operation into `journal` so tests can assert on the exact
// sequence of reads/writes/transactions/batch commits.
//
// Why hand-rolled (not firebase-functions-test wrapper)?
//   - firebase-functions-test does not support v2 onSchedule
//     (firebase/firebase-functions-test#210, open since 2023, still open May 2026).
//   - This fake mirrors the surface used by lib/poll.ts and nothing more.
//   - PO Round 2 Q7 + Q8 picked hand-rolled.
//
// Surface implemented:
//   - db.doc(path) -> { path, get(), set(data, opts), update(data) }
//   - db.collection(path) -> { path, doc(id), orderBy(), limit(), select(), where(), get() }
//   - db.batch() -> { set(ref, data, opts), commit() }
//   - db.runTransaction(cb) -> calls cb with tx { get(ref), set(ref, data, opts) }
//   - db.collectionGroup(name) -> { where(field, op, value), get() }
//
// Unsupported (intentionally — none of the daily-poll code path uses them):
//   - listCollections, listDocuments, snapshot listeners, recursive deletes.

export type JournalEntry =
  | { op: "set"; path: string; data: Record<string, unknown>; merge: boolean }
  | { op: "update"; path: string; data: Record<string, unknown> }
  | { op: "get"; path: string }
  | { op: "delete"; path: string }
  | { op: "queryGet"; path: string; orderBy?: { field: string; dir: "asc" | "desc" }; limit?: number; where?: Array<{ field: string; op: string; value: unknown }>; collectionGroup?: string }
  | { op: "batchCommit"; count: number }
  | { op: "transactionStart" }
  | { op: "transactionCommit" };

export interface FakeRefForDoc {
  path: string;
  id: string;
  parent: { path: string; parent: FakeRefForDoc | null };
}

export interface FakeDocSnap {
  exists: boolean;
  id: string;
  ref: FakeRefForDoc;
  data: () => Record<string, unknown> | undefined;
}

export interface FakeQuerySnap {
  empty: boolean;
  docs: FakeDocSnap[];
}

export interface FakeDocRef {
  path: string;
  id: string;
  parent: { path: string; parent: FakeDocRef | null };
  get: () => Promise<FakeDocSnap>;
  set: (data: Record<string, unknown>, opts?: { merge?: boolean }) => Promise<void>;
  update: (data: Record<string, unknown>) => Promise<void>;
  delete: () => Promise<void>;
}

export interface FakeQuery {
  orderBy: (field: string, dir?: "asc" | "desc") => FakeQuery;
  limit: (n: number) => FakeQuery;
  select: (...fields: string[]) => FakeQuery;
  where: (field: string, op: string, value: unknown) => FakeQuery;
  get: () => Promise<FakeQuerySnap>;
}

export interface FakeCollectionRef extends FakeQuery {
  path: string;
  doc: (id: string) => FakeDocRef;
}

export interface FakeWriteBatch {
  set: (ref: { path: string }, data: Record<string, unknown>, opts?: { merge?: boolean }) => FakeWriteBatch;
  delete: (ref: { path: string }) => FakeWriteBatch;
  commit: () => Promise<void>;
}

export interface FakeTransaction {
  get: (ref: FakeDocRef) => Promise<FakeDocSnap>;
  set: (ref: FakeDocRef, data: Record<string, unknown>, opts?: { merge?: boolean }) => FakeTransaction;
  update: (ref: FakeDocRef, data: Record<string, unknown>) => FakeTransaction;
}

export interface FakeDb {
  doc: (path: string) => FakeDocRef;
  collection: (path: string) => FakeCollectionRef;
  collectionGroup: (name: string) => FakeQuery;
  batch: () => FakeWriteBatch;
  runTransaction: <T>(cb: (tx: FakeTransaction) => Promise<T>) => Promise<T>;
}

export interface FakeContext {
  db: FakeDb;
  journal: JournalEntry[];
  store: Map<string, Record<string, unknown>>;
  seed: (path: string, data: Record<string, unknown>) => void;
  // Inject a failure on the next matching `set()` (single-shot). When
  // `predicate` is omitted, the very next set() throws. Used by the
  // finally-block visibility test to throw on the success-path sync_status
  // write without disturbing the prior lease-tx write.
  failNextSet: (
    reason: string,
    predicate?: (path: string, data: Record<string, unknown>) => boolean,
  ) => void;
}

function parentOf(path: string): { path: string; isDoc: boolean } | null {
  const parts = path.split("/");
  if (parts.length <= 1) return null;
  const trimmed = parts.slice(0, -1);
  return { path: trimmed.join("/"), isDoc: trimmed.length % 2 === 0 };
}

// Build a FakeRefForDoc with the parent.parent chain wired up by walking
// ancestors. `users/uid/sync_status/state` → parent = sync_status collection
// (path users/uid/sync_status), parent.parent = uid doc ref (path users/uid),
// parent.parent.id = "uid".
function buildDocRefChain(path: string): FakeRefForDoc {
  const id = lastSegment(path);
  const parentInfo = parentOf(path);
  const parentPath = parentInfo?.path ?? "";
  let grandRef: FakeRefForDoc | null = null;
  if (parentInfo) {
    const grandInfo = parentOf(parentInfo.path);
    if (grandInfo && isDocPath(grandInfo.path)) {
      grandRef = buildDocRefChain(grandInfo.path);
    }
  }
  return {
    path,
    id,
    parent: {
      path: parentPath,
      parent: grandRef,
    },
  };
}

function lastSegment(path: string): string {
  const parts = path.split("/");
  return parts[parts.length - 1] ?? "";
}

function isDocPath(path: string): boolean {
  return path.split("/").length % 2 === 0;
}

function deepMerge(
  target: Record<string, unknown>,
  source: Record<string, unknown>,
): Record<string, unknown> {
  const result: Record<string, unknown> = { ...target };
  for (const [k, v] of Object.entries(source)) {
    if (
      v !== null &&
      typeof v === "object" &&
      !Array.isArray(v) &&
      typeof result[k] === "object" &&
      result[k] !== null &&
      !Array.isArray(result[k])
    ) {
      result[k] = deepMerge(result[k] as Record<string, unknown>, v as Record<string, unknown>);
    } else {
      result[k] = v;
    }
  }
  return result;
}

export function createFakeDb(): FakeContext {
  const journal: JournalEntry[] = [];
  const store = new Map<string, Record<string, unknown>>();
  let nextSetFailure: {
    reason: string;
    predicate?: (path: string, data: Record<string, unknown>) => boolean;
  } | null = null;

  function shouldFailSet(path: string, data: Record<string, unknown>): string | null {
    if (!nextSetFailure) return null;
    if (nextSetFailure.predicate && !nextSetFailure.predicate(path, data)) return null;
    const reason = nextSetFailure.reason;
    nextSetFailure = null;
    return reason;
  }

  function makeDocRef(path: string): FakeDocRef {
    const id = lastSegment(path);
    const parentInfo = parentOf(path);
    const parent = {
      path: parentInfo?.path ?? "",
      parent: parentInfo && parentInfo.path
        ? (() => {
            const grand = parentOf(parentInfo.path);
            if (grand && isDocPath(parentInfo.path)) {
              return makeDocRef(parentInfo.path);
            }
            return null;
          })()
        : null,
    };

    const ref: FakeDocRef = {
      path,
      id,
      parent,
      get: async () => {
        journal.push({ op: "get", path });
        const data = store.get(path);
        return {
          exists: data !== undefined,
          id,
          ref: buildDocRefChain(path),
          data: () => (data ? { ...data } : undefined),
        };
      },
      set: async (data, opts) => {
        const failReason = shouldFailSet(path, data);
        if (failReason) throw new Error(failReason);
        const merge = opts?.merge === true;
        journal.push({ op: "set", path, data, merge });
        const prev = store.get(path);
        if (merge && prev) {
          store.set(path, deepMerge(prev, data));
        } else {
          store.set(path, { ...data });
        }
      },
      update: async (data) => {
        journal.push({ op: "update", path, data });
        const prev = store.get(path) ?? {};
        store.set(path, { ...prev, ...data });
      },
      delete: async () => {
        journal.push({ op: "delete", path });
        store.delete(path);
      },
    };
    return ref;
  }

  function makeQuery(collectionPath: string, options: {
    orderBy?: { field: string; dir: "asc" | "desc" };
    limit?: number;
    where: Array<{ field: string; op: string; value: unknown }>;
    collectionGroup?: string;
  }): FakeQuery {
    const exec = async (): Promise<FakeQuerySnap> => {
      journal.push({
        op: "queryGet",
        path: collectionPath,
        orderBy: options.orderBy,
        limit: options.limit,
        where: options.where.length ? options.where : undefined,
        collectionGroup: options.collectionGroup,
      });
      // Match documents whose path starts with collectionPath + "/" and that have
      // exactly one trailing segment (i.e. direct children) — unless this is a
      // collectionGroup query, in which case any collection name matches.
      const docs: FakeDocSnap[] = [];
      for (const [path, data] of store.entries()) {
        let matches = false;
        if (options.collectionGroup) {
          const segments = path.split("/");
          // collection name is the second-to-last segment for doc paths.
          if (segments.length >= 2 && segments[segments.length - 2] === options.collectionGroup) {
            matches = true;
          }
        } else if (path.startsWith(collectionPath + "/")) {
          const trailing = path.slice(collectionPath.length + 1);
          if (!trailing.includes("/")) matches = true;
        }
        if (!matches) continue;
        let pass = true;
        for (const w of options.where) {
          // FieldPath.documentId() is mocked to the literal "__name__" — the
          // underlying field-path string Firestore uses for the doc id.
          if (w.field === "__name__") {
            const id = lastSegment(path);
            if (w.op === "in" && Array.isArray(w.value)) {
              if (!w.value.includes(id)) pass = false;
            } else if (w.op === "==" && id !== w.value) {
              pass = false;
            }
          } else {
            const v = (data as Record<string, unknown>)[w.field];
            if (w.op === "==" && v !== w.value) pass = false;
            if (w.op === "in" && Array.isArray(w.value) && !w.value.includes(v)) {
              pass = false;
            }
          }
        }
        if (!pass) continue;
        docs.push({
          exists: true,
          id: lastSegment(path),
          ref: buildDocRefChain(path),
          data: () => ({ ...data }),
        });
      }
      if (options.orderBy) {
        docs.sort((a, b) => {
          const av = (a.data() as Record<string, unknown> | undefined)?.[options.orderBy!.field];
          const bv = (b.data() as Record<string, unknown> | undefined)?.[options.orderBy!.field];
          const aStr = String(av ?? "");
          const bStr = String(bv ?? "");
          const cmp = aStr < bStr ? -1 : aStr > bStr ? 1 : 0;
          return options.orderBy!.dir === "desc" ? -cmp : cmp;
        });
      }
      const limited = options.limit !== undefined ? docs.slice(0, options.limit) : docs;
      return { empty: limited.length === 0, docs: limited };
    };

    const q: FakeQuery = {
      orderBy: (field, dir = "asc") =>
        makeQuery(collectionPath, { ...options, orderBy: { field, dir } }),
      limit: (n) => makeQuery(collectionPath, { ...options, limit: n }),
      select: () => makeQuery(collectionPath, { ...options }),
      where: (field, op, value) =>
        makeQuery(collectionPath, {
          ...options,
          where: [...options.where, { field, op, value }],
        }),
      get: exec,
    };
    return q;
  }

  function makeCollectionRef(path: string): FakeCollectionRef {
    const baseQuery = makeQuery(path, { where: [] });
    return {
      path,
      doc: (id) => makeDocRef(`${path}/${id}`),
      orderBy: baseQuery.orderBy,
      limit: baseQuery.limit,
      select: baseQuery.select,
      where: baseQuery.where,
      get: baseQuery.get,
    };
  }

  const fakeDb: FakeDb = {
    doc: (path) => makeDocRef(path),
    collection: (path) => makeCollectionRef(path),
    collectionGroup: (name) =>
      makeQuery("", { where: [], collectionGroup: name }),
    batch: () => {
      // A queued op is either a set (data + merge) or a delete (data === null).
      const queued: Array<[string, Record<string, unknown> | null, boolean]> = [];
      const batch: FakeWriteBatch = {
        set: (ref, data, opts) => {
          queued.push([ref.path, data, opts?.merge === true]);
          return batch;
        },
        delete: (ref) => {
          queued.push([ref.path, null, false]);
          return batch;
        },
        commit: async () => {
          for (const [path, data, merge] of queued) {
            if (data === null) {
              journal.push({ op: "delete", path });
              store.delete(path);
              continue;
            }
            journal.push({ op: "set", path, data, merge });
            const prev = store.get(path);
            if (merge && prev) {
              store.set(path, deepMerge(prev, data));
            } else {
              store.set(path, { ...data });
            }
          }
          journal.push({ op: "batchCommit", count: queued.length });
          queued.length = 0;
        },
      };
      return batch;
    },
    runTransaction: async (cb) => {
      journal.push({ op: "transactionStart" });
      const tx: FakeTransaction = {
        get: async (ref) => {
          journal.push({ op: "get", path: ref.path });
          const data = store.get(ref.path);
          return {
            exists: data !== undefined,
            id: ref.id,
            ref: buildDocRefChain(ref.path),
            data: () => (data ? { ...data } : undefined),
          };
        },
        set: (ref, data, opts) => {
          const failReason = shouldFailSet(ref.path, data);
          if (failReason) throw new Error(failReason);
          const merge = opts?.merge === true;
          journal.push({ op: "set", path: ref.path, data, merge });
          const prev = store.get(ref.path);
          if (merge && prev) {
            store.set(ref.path, deepMerge(prev, data));
          } else {
            store.set(ref.path, { ...data });
          }
          return tx;
        },
        update: (ref, data) => {
          journal.push({ op: "update", path: ref.path, data });
          const prev = store.get(ref.path) ?? {};
          store.set(ref.path, { ...prev, ...data });
          return tx;
        },
      };
      const result = await cb(tx);
      journal.push({ op: "transactionCommit" });
      return result;
    },
  };

  return {
    db: fakeDb,
    journal,
    store,
    seed: (path, data) => {
      store.set(path, { ...data });
    },
    failNextSet: (reason, predicate) => {
      nextSetFailure = { reason, predicate };
    },
  };
}
