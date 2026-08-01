#!/usr/bin/env node
// Read-only audit of the live Firestore project. Never writes anything.
// Usage: GOOGLE_APPLICATION_CREDENTIALS=<path> FIREBASE_PROJECT_ID=the-flow-54106 node auditData.js

const admin = require("firebase-admin");

if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  console.error("Set GOOGLE_APPLICATION_CREDENTIALS to a local Firebase service account JSON file.");
  process.exit(1);
}
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId: process.env.FIREBASE_PROJECT_ID
  });
}
const db = admin.firestore();

main().catch((error) => {
  console.error(error.stack || error.message || error);
  process.exit(1);
});

async function main() {
  console.log("=== THE FLOW Firestore audit ===\n");

  const [usersSnap, studiosSnap, postsSnap, activitiesSnap, jobsSnap, studioRequestsSnap, studioClaimsSnap, professionalAppsSnap, userActivityEventsSnap] = await Promise.all([
    db.collection("users").get(),
    db.collection("studios").get(),
    db.collection("posts").get(),
    db.collection("activities").get(),
    db.collection("jobs").get(),
    db.collection("studioRequests").get(),
    db.collection("studioClaims").get(),
    db.collection("professionalApplications").get(),
    db.collection("userActivityEvents").get()
  ]);

  const users = usersSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const studios = studiosSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const posts = postsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const activities = activitiesSnap.docs.map((d) => ({ id: d.id, ...d.data() }));

  console.log("--- 1. Raw counts ---");
  console.log(`users: ${users.length}`);
  console.log(`studios: ${studios.length}`);
  console.log(`posts: ${posts.length}`);
  console.log(`  of which postType=dance_activity: ${posts.filter((p) => p.postType === "dance_activity").length}`);
  console.log(`  of which postType=collaboration: ${posts.filter((p) => p.postType === "collaboration").length}`);
  console.log(`  of which postType=repost: ${posts.filter((p) => p.postType === "repost").length}`);
  console.log(`activities (dedicated collection): ${activities.length}`);
  console.log(`jobs: ${jobsSnap.size}`);
  console.log(`studioRequests: ${studioRequestsSnap.size}`);
  console.log(`studioClaims: ${studioClaimsSnap.size}`);
  console.log(`professionalApplications: ${professionalAppsSnap.size}`);
  console.log(`userActivityEvents: ${userActivityEventsSnap.size}`);

  console.log("\n--- 2. Studio manager linkage ---");
  let studiosWithManager = 0;
  let studiosWithTwoPlusManagers = 0;
  let studiosWithMissingAuthManager = 0;
  for (const studio of studios) {
    const managerUids = Array.isArray(studio.managerUids) ? studio.managerUids : [];
    if (managerUids.length > 0) studiosWithManager += 1;
    if (managerUids.length >= 2) studiosWithTwoPlusManagers += 1;
    for (const uid of managerUids) {
      const authExists = await authUserExists(uid);
      if (!authExists) studiosWithMissingAuthManager += 1;
    }
    console.log(`  [${studio.id}] "${studio.displayName || studio.name || "(no name)"}" city=${studio.city || studio.location || "?"} managerUids=${JSON.stringify(managerUids)} ownerUid=${studio.ownerUid || "(none)"} claimStatus=${studio.claimStatus || "(none)"}`);
  }
  console.log(`\nStudios with >=1 manager: ${studiosWithManager}/${studios.length}`);
  console.log(`Studios with >=2 managers (shared inbox demo-able): ${studiosWithTwoPlusManagers}/${studios.length}`);
  console.log(`Manager uids with no matching Firebase Auth user: ${studiosWithMissingAuthManager}`);

  console.log("\n--- 3. Image completeness ---");
  const usersWithProfileImg = users.filter((u) => (u.profileImageUrl || "").trim() !== "").length;
  const usersWithCoverImg = users.filter((u) => (u.coverImageUrl || "").trim() !== "").length;
  const studiosWithProfileImg = studios.filter((s) => (s.profileImageUrl || "").trim() !== "").length;
  const studiosWithCoverImg = studios.filter((s) => (s.coverImageUrl || "").trim() !== "").length;
  const activitiesWithCover = activities.filter((a) => Array.isArray(a.mediaUrls) ? a.mediaUrls.length > 0 : false).length;
  console.log(`users with profileImageUrl: ${usersWithProfileImg}/${users.length}`);
  console.log(`users with coverImageUrl: ${usersWithCoverImg}/${users.length}`);
  console.log(`studios with profileImageUrl (logo): ${studiosWithProfileImg}/${studios.length}`);
  console.log(`studios with coverImageUrl: ${studiosWithCoverImg}/${studios.length}`);
  console.log(`activities with a cover media url: ${activitiesWithCover}/${activities.length}`);

  console.log("\n--- 4. Geographic spread ---");
  const cityCounts = {};
  studios.forEach((s) => {
    const city = (s.city || s.location || "(unknown)").trim() || "(unknown)";
    cityCounts[city] = (cityCounts[city] || 0) + 1;
  });
  console.log("Studios by city:", JSON.stringify(cityCounts, null, 2));

  console.log("\n--- 5. Admin queue pending items ---");
  const pendingStudioRequests = studioRequestsSnap.docs.filter((d) => (d.data().status || "").toUpperCase() === "PENDING").length;
  const pendingStudioClaims = studioClaimsSnap.docs.filter((d) => (d.data().status || "").toUpperCase() === "PENDING").length;
  const pendingProfessionalApps = professionalAppsSnap.docs.filter((d) => (d.data().status || "").toUpperCase() === "PENDING").length;
  console.log(`studioRequests pending: ${pendingStudioRequests}/${studioRequestsSnap.size}`);
  console.log(`studioClaims pending: ${pendingStudioClaims}/${studioClaimsSnap.size}`);
  console.log(`professionalApplications pending: ${pendingProfessionalApps}/${professionalAppsSnap.size}`);

  console.log("\n--- 6. Verification variety ---");
  const verifiedTeachers = users.filter((u) => u.verifiedTeacher === true);
  const verifiedChoreographers = users.filter((u) => u.verifiedChoreographer === true);
  console.log(`verifiedTeacher users: ${verifiedTeachers.length} -> ${verifiedTeachers.map((u) => u.id).join(", ")}`);
  console.log(`verifiedChoreographer users: ${verifiedChoreographers.length} -> ${verifiedChoreographers.map((u) => u.id).join(", ")}`);

  console.log("\n--- 7. Studios missing teacher rosters ---");
  const studiosWithTeachers = studios.filter((s) => Array.isArray(s.teacherUids) && s.teacherUids.length > 0).length;
  console.log(`studios with >=1 teacherUids entry: ${studiosWithTeachers}/${studios.length}`);

  console.log("\n--- 8. Engagement graph (likes/comments/views) ---");
  const [likesGroupSnap, commentsGroupSnap] = await Promise.all([
    db.collectionGroup("likes").get(),
    db.collectionGroup("comments").get()
  ]);
  console.log(`total post likes (collectionGroup): ${likesGroupSnap.size}`);
  console.log(`total post comments (collectionGroup): ${commentsGroupSnap.size}`);
  const likesPerPost = {};
  likesGroupSnap.docs.forEach((d) => {
    const postId = d.ref.parent.parent ? d.ref.parent.parent.id : "?";
    likesPerPost[postId] = (likesPerPost[postId] || 0) + 1;
  });
  const postsWithLikes = Object.keys(likesPerPost).length;
  console.log(`distinct posts with >=1 like: ${postsWithLikes}/${posts.length}`);

  // Style-preference clustering: for users with the most likes, check what style each liked
  // post declares (via a rough style/activityType/collaborationStyle match), to see whether a
  // user's likes concentrate on 1-2 styles or scatter across everything.
  const likesByUser = {};
  likesGroupSnap.docs.forEach((d) => {
    const data = d.data();
    const uid = data.userId || d.id;
    const postId = d.ref.parent.parent ? d.ref.parent.parent.id : null;
    if (!uid || !postId) return;
    likesByUser[uid] = likesByUser[uid] || [];
    likesByUser[uid].push(postId);
  });
  const postsById = Object.fromEntries(posts.map((p) => [p.id, p]));
  const sampleUsers = Object.entries(likesByUser).sort((a, b) => b[1].length - a[1].length).slice(0, 8);
  console.log("\nSample of most-active likers and the styles of what they liked:");
  sampleUsers.forEach(([uid, postIds]) => {
    const styles = postIds.map((pid) => {
      const post = postsById[pid];
      if (!post) return "?";
      return post.activityType || post.collaborationStyle || (post.text || "").slice(0, 40) || "?";
    });
    console.log(`  ${uid} (${postIds.length} likes): ${JSON.stringify(styles)}`);
  });

  console.log(`\nuserActivityEvents total: ${userActivityEventsSnap.size}`);
  const eventTypeCounts = {};
  userActivityEventsSnap.docs.forEach((d) => {
    const t = d.data().eventType || "?";
    eventTypeCounts[t] = (eventTypeCounts[t] || 0) + 1;
  });
  console.log("userActivityEvents by eventType:", JSON.stringify(eventTypeCounts, null, 2));

  console.log("\n=== Audit complete ===");
}

async function authUserExists(uid) {
  if (!uid) return false;
  try {
    await admin.auth().getUser(uid);
    return true;
  } catch (error) {
    if (error.code === "auth/user-not-found") return false;
    throw error;
  }
}
