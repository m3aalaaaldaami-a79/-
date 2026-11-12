const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_lab_mgr";
const BOB_UID = "bob_other_mgr";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validStaffPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    name: "د. سارة العلي",
    role: "أخصائية تحاليل",
    isActive: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validCheckInPayload(uid, staffId, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    staffId,
    staffName: "د. سارة العلي",
    staffRole: "أخصائية تحاليل",
    workDateUtc: "2026-11-12",
    checkInUtcIso: "2026-11-12T08:00:00Z",
    checkInDisplayTime: "11:00:00 ص",
    checkInEpochMs: 1794470400000,
    checkInSource: "timeapi.io",
    checkOutUtcIso: "",
    checkOutDisplayTime: "",
    checkOutEpochMs: 0,
    checkOutSource: "",
    status: "checked_in",
    whatsappSent: true,
    notes: "",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write staff or attendance", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("staff").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("attendance").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("staff").doc("s1").set(validStaffPayload(ALICE_UID))
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's staff or attendance", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("staff").doc("s1").set(validStaffPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("staff").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("staff").doc("s1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("staff").doc("s2").set(validStaffPayload(ALICE_UID))
  );
});

test("Owner can create staff and attendance check-in, then check-out once", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const staffRef = aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc("staff_1");
  await assertSucceeds(staffRef.set(validStaffPayload(ALICE_UID)));

  const recRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance").doc("rec_1");
  await assertSucceeds(recRef.set(validCheckInPayload(ALICE_UID, "staff_1")));

  // Valid check-out transition
  await assertSucceeds(
    recRef.update({
      status: "checked_out",
      checkOutUtcIso: "2026-11-12T16:00:00Z",
      checkOutDisplayTime: "07:00:00 م",
      checkOutEpochMs: 1794499200000,
      checkOutSource: "timeapi.io",
      updatedAt: new Date(),
    })
  );

  // Terminal state lock: cannot modify checkOutEpochMs or revert status after checked_out
  await assertFails(
    recRef.update({
      status: "checked_in",
      checkOutEpochMs: 0,
      updatedAt: new Date(),
    })
  );
});

test("Shadow update / ghost field / orphaned attendance fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Orphaned attendance (staff_missing does not exist) must fail
  const orphanRef = aliceDb.collection("users").doc(ALICE_UID).collection("attendance").doc("rec_orphan");
  await assertFails(orphanRef.set(validCheckInPayload(ALICE_UID, "staff_missing")));

  // Ghost field in staff must fail
  const staffRef = aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc("staff_1");
  await assertFails(staffRef.set(validStaffPayload(ALICE_UID, { isAdmin: true })));

  // Invalid ID with slash/too long/bad chars must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("staff").doc("bad id!").set(validStaffPayload(ALICE_UID))
  );
});
