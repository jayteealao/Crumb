// Smoke test for handlers/dailyPoll.ts wiring against the v2 onSchedule
// surface. Isolated in its own file so it can mock ../src/lib/admin and
// ../src/lib/poll fresh without colliding with the runPoll-focused mocks in
// daily-poll.test.ts.

import { createFakeDb } from "./fakes/firestore";

const fakeCtx = createFakeDb();
// Seed three users: two linked, one not. collectionGroup("sync_status")
// .where("linked", "==", true) must yield exactly two — and the handler loop
// must call runPoll for each of those uids and skip the third.
fakeCtx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-1" });
fakeCtx.seed("users/uid2/sync_status/state", { linked: true, xUserId: "x-2" });
fakeCtx.seed("users/uid3/sync_status/state", { linked: false });

const runPollSpy = jest.fn(async (uid: string) => {
  void uid;
  return { ok: true, itemsAdded: 0, itemsFlaggedPendingDelete: 0 };
});

jest.mock("../src/lib/admin", () => ({
  db: () => fakeCtx.db,
  app: () => undefined,
}));

jest.mock("../src/lib/poll", () => ({
  runPoll: runPollSpy,
}));

import { dailyPoll } from "../src/handlers/dailyPoll";

type Runner = { run: (event: unknown) => Promise<void> };

describe("dailyPoll handler", () => {
  it("invokes runPoll for each linked user discovered via collectionGroup", async () => {
    const runner = dailyPoll as unknown as Runner;
    await runner.run({
      scheduleTime: "2026-05-20T09:00:00Z",
      jobName: "firebase-schedule-dailyPoll-europe-west2",
    });

    expect(runPollSpy).toHaveBeenCalledTimes(2);
    const uids = runPollSpy.mock.calls.map((c) => c[0]);
    expect(uids.sort()).toEqual(["uid1", "uid2"]);
  });
});
