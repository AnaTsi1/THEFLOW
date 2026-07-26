#!/usr/bin/env node

const readline = require("node:readline/promises");
const { stdin: input, stdout: output } = require("node:process");
const admin = require("firebase-admin");

const args = new Set(process.argv.slice(2));
const seedBatchId = valueFor("--batch") || "demo_small_v1";
const write = args.has("--write");
const confirmed = valueFor("--confirm") === seedBatchId;
const dryRun = !write || args.has("--dry-run");

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});

async function main() {
  initializeAdmin();
  const db = admin.firestore();
  const targets = await findDemoDocuments(db, seedBatchId);
  printSummary(targets, dryRun);

  if (dryRun) {
    console.log("\nDry run only. Nothing was deleted.");
    return;
  }

  if (!confirmed) {
    throw new Error(`Refusing to delete. Re-run with --write --confirm ${seedBatchId}.`);
  }

  const rl = readline.createInterface({ input, output });
  const answer = await rl.question(`Type DELETE ${seedBatchId} to delete only this demo batch: `);
  rl.close();
  if (answer !== `DELETE ${seedBatchId}`) {
    throw new Error("Deletion cancelled.");
  }

  await deleteDocuments(db, targets.documents);
  await deleteAuthUsers(targets.userUids);
  console.log(`\nDeleted demo batch ${seedBatchId}.`);
}

async function findDemoDocuments(db, batchId) {
  const documents = [];
  const userUids = [];

  for (const collection of ["users", "studios", "posts", "studioClaims", "userActivityEvents"]) {
    const snapshot = await db.collection(collection)
      .where("isDemo", "==", true)
      .where("seedBatchId", "==", batchId)
      .get();
    snapshot.docs.forEach((doc) => {
      documents.push(doc.ref);
      if (collection === "users") userUids.push(doc.id);
    });
  }

  for (const group of ["likes", "comments", "savedItems", "followingDancers", "followingTeachers", "followingStudios", "followers", "recommendationProfile"]) {
    const snapshot = await db.collectionGroup(group)
      .where("isDemo", "==", true)
      .where("seedBatchId", "==", batchId)
      .get();
    snapshot.docs.forEach((doc) => documents.push(doc.ref));
  }

  return { documents, userUids };
}

async function deleteDocuments(db, refs) {
  for (const chunk of chunks(refs, 450)) {
    const batch = db.batch();
    chunk.forEach((ref) => batch.delete(ref));
    await batch.commit();
  }
}

async function deleteAuthUsers(uids) {
  for (const uid of uids) {
    try {
      await admin.auth().deleteUser(uid);
    } catch (error) {
      if (error.code !== "auth/user-not-found") throw error;
    }
  }
}

function printSummary(targets, isDryRun) {
  console.log(`THE FLOW demo delete ${isDryRun ? "dry run" : "WRITE MODE"}`);
  console.log(`Batch: ${seedBatchId}`);
  console.log(`Firestore documents matched: ${targets.documents.length}`);
  console.log(`Auth users matched through demo user docs: ${targets.userUids.length}`);
}

function initializeAdmin() {
  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    throw new Error("Set GOOGLE_APPLICATION_CREDENTIALS to a local Firebase service account JSON file before deleting demo data.");
  }
  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: process.env.FIREBASE_PROJECT_ID
    });
  }
}

function valueFor(flag) {
  const index = process.argv.indexOf(flag);
  return index >= 0 ? process.argv[index + 1] : "";
}

function chunks(items, size) {
  const result = [];
  for (let i = 0; i < items.length; i += size) result.push(items.slice(i, i + size));
  return result;
}
