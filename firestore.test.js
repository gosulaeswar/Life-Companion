const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_001";
const CAREGIVER_UID = "caregiver_uid_002";
const MEMBER_UID = "member_uid_003";
const STRANGER_UID = "stranger_uid_999";

const OWNER_EMAIL = "owner@lifeweave.test";
const CAREGIVER_EMAIL = "caregiver@lifeweave.test";
const MEMBER_EMAIL = "member@lifeweave.test";

const FAMILY_ID = "family_sharma_01";
const FREE_FAMILY_ID = "family_free_01";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function futureDate(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

function verifiedAuth(uid, email) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: true,
  }).firestore();
}

function unverifiedAuth(uid, email) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: false,
  }).firestore();
}

async function seedFamilyCircle({
  familyId = FAMILY_ID,
  subscriptionTier = "family",
  eligibleForFiveProfiles = true,
  maxProfiles = 5,
  activeProfileCount = 2,
  memberUids = [OWNER_UID, CAREGIVER_UID, MEMBER_UID],
  memberRoles = {
    [OWNER_UID]: "OWNER",
    [CAREGIVER_UID]: "CAREGIVER",
    [MEMBER_UID]: "MEMBER",
  },
  memberShareCycle = false,
  memberShareAppointments = true,
  memberAllowCaregiverReminders = true,
} = {}) {
  const ts = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(OWNER_UID).set({
      uid: OWNER_UID,
      emailLower: OWNER_EMAIL,
      displayName: "Ananya Sharma",
      subscriptionTier,
      eligibleForFiveProfiles,
      activeFamilyId: familyId,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("families").doc(familyId).set({
      ownerUid: OWNER_UID,
      familyName: "Sharma Family Circle",
      subscriptionTier,
      eligibleForFiveProfiles,
      maxProfiles,
      activeProfileCount,
      memberUids,
      memberRoles,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("families").doc(familyId).collection("members").doc(OWNER_UID).set({
      uid: OWNER_UID,
      familyId,
      displayName: "Ananya",
      relationLabel: "Self",
      role: "OWNER",
      memberUids,
      shareHydration: true,
      shareFitnessSleep: true,
      shareNutrition: true,
      shareMedicines: true,
      shareAppointments: true,
      shareCycle: false,
      allowCaregiverReminders: true,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("families").doc(familyId).collection("profiles").doc("slot_1").set({
      slotIndex: 1,
      familyId,
      ownerUid: OWNER_UID,
      linkedMemberUid: OWNER_UID,
      displayName: "Ananya",
      relationLabel: "Self",
      age: 32,
      genderIdentity: "Female",
      dailyWaterGoalMl: 2400,
      managedByCaregiver: false,
      memberUids,
      shareHydration: true,
      shareFitnessSleep: true,
      shareNutrition: true,
      shareMedicines: true,
      shareAppointments: true,
      shareCycle: false,
      allowCaregiverReminders: true,
      createdAt: ts,
      updatedAt: ts,
    });
    if (memberUids.includes(CAREGIVER_UID)) {
      await db.collection("families").doc(familyId).collection("members").doc(CAREGIVER_UID).set({
        uid: CAREGIVER_UID,
        familyId,
        displayName: "Rohan",
        relationLabel: "Partner",
        role: "CAREGIVER",
        memberUids,
        shareHydration: true,
        shareFitnessSleep: true,
        shareNutrition: false,
        shareMedicines: true,
        shareAppointments: true,
        shareCycle: false,
        allowCaregiverReminders: true,
        createdAt: ts,
        updatedAt: ts,
      });
    }
    if (memberUids.includes(MEMBER_UID)) {
      await db.collection("families").doc(familyId).collection("members").doc(MEMBER_UID).set({
        uid: MEMBER_UID,
        familyId,
        displayName: "Meera",
        relationLabel: "Mother",
        role: "MEMBER",
        memberUids,
        shareHydration: true,
        shareFitnessSleep: false,
        shareNutrition: true,
        shareMedicines: true,
        shareAppointments: memberShareAppointments,
        shareCycle: memberShareCycle,
        allowCaregiverReminders: memberAllowCaregiverReminders,
        createdAt: ts,
        updatedAt: ts,
      });
      await db.collection("families").doc(familyId).collection("profiles").doc("slot_2").set({
        slotIndex: 2,
        familyId,
        ownerUid: OWNER_UID,
        linkedMemberUid: MEMBER_UID,
        displayName: "Meera",
        relationLabel: "Mother",
        age: 61,
        genderIdentity: "Female",
        dailyWaterGoalMl: 2200,
        managedByCaregiver: true,
        memberUids,
        shareHydration: true,
        shareFitnessSleep: false,
        shareNutrition: true,
        shareMedicines: true,
        shareAppointments: memberShareAppointments,
        shareCycle: memberShareCycle,
        allowCaregiverReminders: memberAllowCaregiverReminders,
        createdAt: ts,
        updatedAt: ts,
      });
    }
  });
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

test("1. Unauthenticated users cannot read or write any Family Circle collection", async () => {
  await seedFamilyCircle();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("users").doc(OWNER_UID).get());
  await assertFails(unauthDb.collection("families").doc(FAMILY_ID).get());
  await assertFails(unauthDb.collection("families").where("memberUids", "array-contains", OWNER_UID).get());
  await assertFails(unauthDb.collection("familyInvitations").get());
  await assertFails(
    unauthDb.collection("families").doc(FAMILY_ID).collection("caregiverReminders").doc("r1").set({
      familyId: FAMILY_ID,
      title: "Hack",
    })
  );
});

test("2. Eligible Family subscription allows up to 5 profile slots; 6th slot & ineligible 2nd slot are rejected on backend", async () => {
  const ts = pastDate(3000);
  // Seed an eligible family circle + an ineligible free family circle
  await seedFamilyCircle({
    familyId: FAMILY_ID,
    subscriptionTier: "family",
    eligibleForFiveProfiles: true,
    maxProfiles: 5,
    activeProfileCount: 5,
    memberUids: [OWNER_UID],
    memberRoles: { [OWNER_UID]: "OWNER" },
  });
  await seedFamilyCircle({
    familyId: FREE_FAMILY_ID,
    subscriptionTier: "free",
    eligibleForFiveProfiles: false,
    maxProfiles: 1,
    activeProfileCount: 1,
    memberUids: [OWNER_UID],
    memberRoles: { [OWNER_UID]: "OWNER" },
  });

  const ownerDb = verifiedAuth(OWNER_UID, OWNER_EMAIL);

  // Eligible family can create slot_2 through slot_5
  for (const idx of [2, 3, 4, 5]) {
    await assertSucceeds(
      ownerDb.collection("families").doc(FAMILY_ID).collection("profiles").doc(`slot_${idx}`).set({
        slotIndex: idx,
        familyId: FAMILY_ID,
        ownerUid: OWNER_UID,
        displayName: `Member ${idx}`,
        relationLabel: "Family",
        age: 25 + idx,
        genderIdentity: "Prefer not to say",
        dailyWaterGoalMl: 2300,
        managedByCaregiver: true,
        memberUids: [OWNER_UID],
        shareHydration: true,
        shareFitnessSleep: true,
        shareNutrition: true,
        shareMedicines: true,
        shareAppointments: true,
        shareCycle: false,
        allowCaregiverReminders: true,
        createdAt: ts,
        updatedAt: ts,
      })
    );
  }

  // Attempting to create 6th profile slot_6 MUST fail on backend
  await assertFails(
    ownerDb.collection("families").doc(FAMILY_ID).collection("profiles").doc("slot_6").set({
      slotIndex: 6,
      familyId: FAMILY_ID,
      ownerUid: OWNER_UID,
      displayName: "Sixth Member",
      relationLabel: "Cousin",
      age: 22,
      genderIdentity: "Female",
      dailyWaterGoalMl: 2200,
      managedByCaregiver: true,
      memberUids: [OWNER_UID],
      shareHydration: true,
      shareFitnessSleep: true,
      shareNutrition: true,
      shareMedicines: true,
      shareAppointments: true,
      shareCycle: false,
      allowCaregiverReminders: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Ineligible free tier cannot create slot_2
  await assertFails(
    ownerDb.collection("families").doc(FREE_FAMILY_ID).collection("profiles").doc("slot_2").set({
      slotIndex: 2,
      familyId: FREE_FAMILY_ID,
      ownerUid: OWNER_UID,
      displayName: "Unauthorized Second Profile",
      relationLabel: "Partner",
      age: 30,
      genderIdentity: "Male",
      dailyWaterGoalMl: 2500,
      managedByCaregiver: true,
      memberUids: [OWNER_UID],
      shareHydration: true,
      shareFitnessSleep: true,
      shareNutrition: true,
      shareMedicines: true,
      shareAppointments: true,
      shareCycle: false,
      allowCaregiverReminders: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("3. Invitations: email spoofing/unverified fails; verified invitee joins atomically with assigned role (no OWNER escalation); terminal state locked", async () => {
  const ts = pastDate(4000);
  await seedFamilyCircle({
    memberUids: [OWNER_UID],
    memberRoles: { [OWNER_UID]: "OWNER" },
    activeProfileCount: 1,
  });

  const ownerDb = verifiedAuth(OWNER_UID, OWNER_EMAIL);
  const memberDb = verifiedAuth(MEMBER_UID, MEMBER_EMAIL);
  const unverifiedMemberDb = unverifiedAuth(MEMBER_UID, MEMBER_EMAIL);
  const strangerDb = verifiedAuth(STRANGER_UID, "stranger@lifeweave.test");

  // Owner cannot invite someone as OWNER
  await assertFails(
    ownerDb.collection("familyInvitations").doc("inv_bad_role").set({
      familyId: FAMILY_ID,
      familyName: "Sharma Family Circle",
      inviterUid: OWNER_UID,
      inviterName: "Ananya",
      inviteeEmailLower: MEMBER_EMAIL,
      assignedRole: "OWNER",
      status: "PENDING",
      inviteNote: "Join us",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner creates valid PENDING CAREGIVER/MEMBER invite
  await assertSucceeds(
    ownerDb.collection("familyInvitations").doc("inv_1").set({
      familyId: FAMILY_ID,
      familyName: "Sharma Family Circle",
      inviterUid: OWNER_UID,
      inviterName: "Ananya",
      inviteeEmailLower: MEMBER_EMAIL,
      assignedRole: "MEMBER",
      status: "PENDING",
      inviteNote: "Welcome Mom",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Stranger and unverified email cannot read or accept invite
  await assertFails(strangerDb.collection("familyInvitations").doc("inv_1").get());
  await assertFails(unverifiedMemberDb.collection("familyInvitations").doc("inv_1").get());

  // Invitee cannot escalate role to OWNER while accepting invite
  const badBatch = memberDb.batch();
  badBatch.update(memberDb.collection("familyInvitations").doc("inv_1"), {
    status: "ACCEPTED",
    acceptedByUid: MEMBER_UID,
    updatedAt: pastDate(1000),
  });
  badBatch.update(memberDb.collection("families").doc(FAMILY_ID), {
    memberUids: [OWNER_UID, MEMBER_UID],
    memberRoles: { [OWNER_UID]: "OWNER", [MEMBER_UID]: "OWNER" },
    activeProfileCount: 2,
    lastAcceptedInviteId: "inv_1",
    updatedAt: pastDate(1000),
  });
  await assertFails(badBatch.commit());

  // Valid atomic accept batch with assignedRole == "MEMBER" succeeds
  const goodBatch = memberDb.batch();
  goodBatch.update(memberDb.collection("familyInvitations").doc("inv_1"), {
    status: "ACCEPTED",
    acceptedByUid: MEMBER_UID,
    updatedAt: pastDate(1000),
  });
  goodBatch.update(memberDb.collection("families").doc(FAMILY_ID), {
    memberUids: [OWNER_UID, MEMBER_UID],
    memberRoles: { [OWNER_UID]: "OWNER", [MEMBER_UID]: "MEMBER" },
    activeProfileCount: 2,
    lastAcceptedInviteId: "inv_1",
    updatedAt: pastDate(1000),
  });
  goodBatch.set(memberDb.collection("families").doc(FAMILY_ID).collection("members").doc(MEMBER_UID), {
    uid: MEMBER_UID,
    familyId: FAMILY_ID,
    displayName: "Meera",
    relationLabel: "Mother",
    role: "MEMBER",
    memberUids: [OWNER_UID, MEMBER_UID],
    shareHydration: true,
    shareFitnessSleep: false,
    shareNutrition: true,
    shareMedicines: true,
    shareAppointments: true,
    shareCycle: false,
    allowCaregiverReminders: true,
    createdAt: ts,
    updatedAt: pastDate(1000),
  });
  await assertSucceeds(goodBatch.commit());

  // Terminal state lock: once ACCEPTED, invitation cannot be mutated again
  await assertFails(
    ownerDb.collection("familyInvitations").doc("inv_1").update({
      status: "REVOKED",
      updatedAt: pastDate(500),
    })
  );
});

test("4. RBAC & Consent: Caregivers can create reminders when consented; MEMBER cannot create caregiver reminders or escalate roles", async () => {
  const ts = pastDate(3000);
  await seedFamilyCircle({ memberAllowCaregiverReminders: true });

  const caregiverDb = verifiedAuth(CAREGIVER_UID, CAREGIVER_EMAIL);
  const memberDb = verifiedAuth(MEMBER_UID, MEMBER_EMAIL);
  const ownerDb = verifiedAuth(OWNER_UID, OWNER_EMAIL);

  // Caregiver creates reminder for consenting member
  await assertSucceeds(
    caregiverDb.collection("families").doc(FAMILY_ID).collection("caregiverReminders").doc("rem_1").set({
      familyId: FAMILY_ID,
      createdByUid: CAREGIVER_UID,
      createdByName: "Rohan",
      targetProfileSlotId: "slot_2",
      targetMemberUid: MEMBER_UID,
      targetDisplayName: "Meera",
      title: "Morning Thyroid Pill",
      category: "MEDICINE",
      scheduleTime24h: "07:30",
      message: "Take before breakfast",
      status: "ACTIVE",
      memberUids: [OWNER_UID, CAREGIVER_UID, MEMBER_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Target member can mark status COMPLETED, but cannot change message/title
  await assertSucceeds(
    memberDb.collection("families").doc(FAMILY_ID).collection("caregiverReminders").doc("rem_1").update({
      status: "COMPLETED",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("caregiverReminders").doc("rem_1").update({
      title: "Tampered Title",
      updatedAt: pastDate(500),
    })
  );

  // MEMBER cannot create caregiver reminder or promote self to CAREGIVER
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("caregiverReminders").doc("rem_2").set({
      familyId: FAMILY_ID,
      createdByUid: MEMBER_UID,
      createdByName: "Meera",
      targetProfileSlotId: "slot_1",
      targetMemberUid: OWNER_UID,
      targetDisplayName: "Ananya",
      title: "Not Allowed",
      category: "WELLNESS",
      scheduleTime24h: "09:00",
      message: "",
      status: "ACTIVE",
      memberUids: [OWNER_UID, CAREGIVER_UID, MEMBER_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("members").doc(MEMBER_UID).update({
      role: "CAREGIVER",
      updatedAt: pastDate(500),
    })
  );

  // Owner cannot overwrite member's private consent switches directly on members/{memberUid}
  await assertFails(
    ownerDb.collection("families").doc(FAMILY_ID).collection("members").doc(MEMBER_UID).update({
      shareCycle: true,
      updatedAt: pastDate(500),
    })
  );
});

test("5. Explicit Consent-Based Sharing & Shared Appointments enforced on backend", async () => {
  const ts = pastDate(3000);
  await seedFamilyCircle({ memberShareCycle: false, memberShareAppointments: true });

  const ownerDb = verifiedAuth(OWNER_UID, OWNER_EMAIL);
  const caregiverDb = verifiedAuth(CAREGIVER_UID, CAREGIVER_EMAIL);
  const memberDb = verifiedAuth(MEMBER_UID, MEMBER_EMAIL);
  const strangerDb = verifiedAuth(STRANGER_UID, "stranger@lifeweave.test");

  // Member has shareHydration = true -> sharing hydration snapshot SUCCEEDS
  await assertSucceeds(
    memberDb.collection("families").doc(FAMILY_ID).collection("sharedWellnessSnapshots").doc("slot_2_HYDRATION").set({
      familyId: FAMILY_ID,
      profileSlotId: "slot_2",
      ownerUid: MEMBER_UID,
      profileName: "Meera",
      category: "HYDRATION",
      summaryLine: "1800 / 2200 ml water today",
      primaryMetricLabel: "82% goal",
      consentEnabled: true,
      memberUids: [OWNER_UID, CAREGIVER_UID, MEMBER_UID],
      updatedAt: ts,
    })
  );

  // Member has shareCycle = false -> attempting to share CYCLE snapshot is REJECTED on backend
  await assertFails(
    memberDb.collection("families").doc(FAMILY_ID).collection("sharedWellnessSnapshots").doc("slot_2_CYCLE").set({
      familyId: FAMILY_ID,
      profileSlotId: "slot_2",
      ownerUid: MEMBER_UID,
      profileName: "Meera",
      category: "CYCLE",
      summaryLine: "Private cycle info",
      primaryMetricLabel: "Day 4",
      consentEnabled: true,
      memberUids: [OWNER_UID, CAREGIVER_UID, MEMBER_UID],
      updatedAt: ts,
    })
  );

  // Caregiver can read consented hydration snapshot, and filtered list query succeeds
  await assertSucceeds(
    caregiverDb.collection("families").doc(FAMILY_ID).collection("sharedWellnessSnapshots").doc("slot_2_HYDRATION").get()
  );
  await assertSucceeds(
    caregiverDb
      .collection("families")
      .doc(FAMILY_ID)
      .collection("sharedWellnessSnapshots")
      .where("memberUids", "array-contains", CAREGIVER_UID)
      .where("consentEnabled", "==", true)
      .get()
  );

  // Unfiltered list without consentEnabled==true or memberUids fails
  await assertFails(
    caregiverDb.collection("families").doc(FAMILY_ID).collection("sharedWellnessSnapshots").get()
  );

  // Shared appointment allowed because slot_2 shareAppointments == true
  await assertSucceeds(
    caregiverDb.collection("families").doc(FAMILY_ID).collection("appointments").doc("appt_1").set({
      familyId: FAMILY_ID,
      createdByUid: CAREGIVER_UID,
      patientProfileSlotId: "slot_2",
      patientMemberUid: MEMBER_UID,
      patientName: "Meera",
      doctorName: "Dr. Kavita Rao",
      specialty: "Endocrinology",
      clinicOrHospital: "Apollo Clinic",
      scheduledEpochMs: Date.now() + 86400000,
      notes: "Quarterly TSH review",
      status: "SCHEDULED",
      consentGranted: true,
      visibleMemberUids: [OWNER_UID, CAREGIVER_UID, MEMBER_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Instant consent revocation: member turns off shareHydration on slot_2 -> Caregiver get() immediately denied!
  await assertSucceeds(
    memberDb.collection("families").doc(FAMILY_ID).collection("profiles").doc("slot_2").update({
      shareHydration: false,
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    caregiverDb.collection("families").doc(FAMILY_ID).collection("sharedWellnessSnapshots").doc("slot_2_HYDRATION").get()
  );

  // Stranger denied everywhere, ghost fields & future timestamps rejected
  await assertFails(strangerDb.collection("families").doc(FAMILY_ID).collection("appointments").doc("appt_1").get());
  await assertFails(
    ownerDb.collection("families").doc(FAMILY_ID).collection("appointments").doc("appt_ghost").set({
      familyId: FAMILY_ID,
      createdByUid: OWNER_UID,
      patientProfileSlotId: "slot_1",
      patientMemberUid: OWNER_UID,
      patientName: "Ananya",
      doctorName: "Dr. Ghost",
      specialty: "General",
      clinicOrHospital: "",
      scheduledEpochMs: Date.now() + 10000,
      notes: "",
      status: "SCHEDULED",
      consentGranted: true,
      visibleMemberUids: [OWNER_UID],
      ghostField: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    ownerDb.collection("families").doc(FAMILY_ID).update({
      familyName: "Tampered Future Time",
      updatedAt: futureDate(120000),
    })
  );
});

test("6. Free-tier account cannot spoof 5-profile Family Circle creation, and unconsented member blocks caregiver reminders", async () => {
  const ts = pastDate(3000);
  const strangerDb = verifiedAuth(STRANGER_UID, "stranger@lifeweave.test");

  // Stranger creates free-tier user doc
  await assertSucceeds(
    strangerDb.collection("users").doc(STRANGER_UID).set({
      uid: STRANGER_UID,
      emailLower: "stranger@lifeweave.test",
      displayName: "Stranger",
      subscriptionTier: "free",
      eligibleForFiveProfiles: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Free user cannot spoof creating a 5-profile Family Circle on backend
  await assertFails(
    strangerDb.collection("families").doc("spoofed_family").set({
      ownerUid: STRANGER_UID,
      familyName: "Spoofed 5-Profile Circle",
      subscriptionTier: "family",
      eligibleForFiveProfiles: true,
      maxProfiles: 5,
      activeProfileCount: 1,
      memberUids: [STRANGER_UID],
      memberRoles: { [STRANGER_UID]: "OWNER" },
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Member with allowCaregiverReminders = false blocks caregiver reminders
  await seedFamilyCircle({ memberAllowCaregiverReminders: false });
  const caregiverDb = verifiedAuth(CAREGIVER_UID, CAREGIVER_EMAIL);
  await assertFails(
    caregiverDb.collection("families").doc(FAMILY_ID).collection("caregiverReminders").doc("rem_blocked").set({
      familyId: FAMILY_ID,
      createdByUid: CAREGIVER_UID,
      createdByName: "Rohan",
      targetProfileSlotId: "slot_2",
      targetMemberUid: MEMBER_UID,
      targetDisplayName: "Meera",
      title: "Unconsented Reminder",
      category: "WELLNESS",
      scheduleTime24h: "09:00",
      message: "Blocked",
      status: "ACTIVE",
      memberUids: [OWNER_UID, CAREGIVER_UID, MEMBER_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
