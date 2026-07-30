#!/usr/bin/env node

const admin = require("firebase-admin");
const { buildDemoData } = require("./demoData");

const args = new Set(process.argv.slice(2));
const size = valueFor("--size") || "small";
const seedBatchId = valueFor("--batch") || `demo_${size}_v1`;
const write = args.has("--write");
const dryRun = !write || args.has("--dry-run");

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});

async function main() {
  const data = buildDemoData({ size, seedBatchId });
  printSummary(data, dryRun);
  if (dryRun) {
    console.log("\nDry run only. Nothing was written to Firebase.");
    return;
  }

  initializeAdmin();
  const db = admin.firestore();

  await upsertAuthUsers(data.users);
  await writeFirestoreData(db, data);
  console.log(`\nDemo seed complete for batch ${seedBatchId}.`);
}

async function upsertAuthUsers(users) {
  for (const user of users) {
    try {
      await admin.auth().getUser(user.uid);
      await admin.auth().updateUser(user.uid, {
        email: user.email,
        password: user.password,
        displayName: `${user.firstName} ${user.lastName}`,
        disabled: false
      });
    } catch (error) {
      if (error.code !== "auth/user-not-found") throw error;
      await admin.auth().createUser({
        uid: user.uid,
        email: user.email,
        password: user.password,
        displayName: `${user.firstName} ${user.lastName}`,
        emailVerified: true,
        disabled: false
      });
    }
  }
}

async function writeFirestoreData(db, data) {
  const writes = [];
  data.users.forEach((user) => {
    const { password, ...profile } = user;
    writes.push({ ref: db.collection("users").doc(user.uid), data: profile });
  });
  data.recommendationProfiles.forEach((profile) => {
    writes.push({ ref: db.doc(profile.path), data: profile.data });
  });
  data.studios.forEach((studio) => {
    writes.push({ ref: db.collection("studios").doc(studio.id), data: studio });
  });
  data.posts.forEach((post) => {
    writes.push({ ref: db.collection("posts").doc(post.postId), data: post });
  });
  data.jobs.forEach((job) => {
    writes.push({ ref: db.collection("jobs").doc(job.jobId), data: job });
  });
  data.jobApplications.forEach((application) => {
    writes.push({ ref: db.collection("jobApplications").doc(application.applicationId), data: application });
  });
  data.savedJobs.forEach((saved) => {
    writes.push({ ref: db.collection("users").doc(saved.uid).collection("savedJobs").doc(saved.jobId), data: saved.data });
  });
  data.notifications.forEach((notification) => {
    writes.push({ ref: db.collection("users").doc(notification.uid).collection("notifications").doc(notification.notificationId), data: notification.data });
  });
  data.likes.forEach((like) => {
    writes.push({ ref: db.collection("posts").doc(like.postId).collection("likes").doc(like.uid), data: like.data });
  });
  data.comments.forEach((comment) => {
    writes.push({ ref: db.collection("posts").doc(comment.postId).collection("comments").doc(comment.commentId), data: comment.data });
  });
  data.savedItems.forEach((saved) => {
    writes.push({ ref: db.collection("users").doc(saved.uid).collection("savedItems").doc(saved.itemId), data: saved.data });
  });
  data.follows.forEach((follow) => {
    writes.push({ ref: db.collection("users").doc(follow.followerUid).collection(follow.type).doc(follow.targetUid), data: follow.data });
    writes.push({
      ref: db.collection("users").doc(follow.targetUid).collection("followers").doc(follow.followerUid),
      data: {
        followerId: follow.followerUid,
        userId: follow.followerUid,
        followedAt: "__SERVER_TIMESTAMP__",
        isDemo: true,
        seedBatchId: data.seedBatchId
      }
    });
  });
  data.activityEvents.forEach((event) => {
    writes.push({ ref: db.collection("userActivityEvents").doc(event.id), data: event.data });
  });

  for (const chunk of chunks(writes, 450)) {
    const batch = db.batch();
    chunk.forEach((write) => batch.set(write.ref, withServerTimestamps(write.data), { merge: true }));
    await batch.commit();
  }
}

function withServerTimestamps(value) {
  if (value === "__SERVER_TIMESTAMP__") return admin.firestore.FieldValue.serverTimestamp();
  if (Array.isArray(value)) return value.map(withServerTimestamps);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, nested]) => [key, withServerTimestamps(nested)]));
  }
  return value;
}

function initializeAdmin() {
  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    throw new Error("Set GOOGLE_APPLICATION_CREDENTIALS to a local Firebase service account JSON file before using --write.");
  }
  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: process.env.FIREBASE_PROJECT_ID
    });
  }
}

function printSummary(data, isDryRun) {
  console.log(`THE FLOW demo seed ${isDryRun ? "dry run" : "WRITE MODE"}`);
  console.log(`Batch: ${data.seedBatchId}`);
  console.log(`Size: ${data.size}`);
  console.log(`Users: ${data.users.length}`);
  console.log(`Studios: ${data.studios.length}`);
  console.log(`Posts: ${data.posts.length}`);
  console.log(`Jobs: ${data.jobs.length}`);
  console.log(`Job applications: ${data.jobApplications.length}`);
  console.log(`Likes: ${data.likes.length}`);
  console.log(`Comments: ${data.comments.length}`);
  console.log(`Saved items: ${data.savedItems.length}`);
  console.log(`Follows: ${data.follows.length}`);
  console.log(`Activity events: ${data.activityEvents.length}`);
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
