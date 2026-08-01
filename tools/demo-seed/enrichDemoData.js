#!/usr/bin/env node
// Enriches the existing live Firestore project instead of replacing it: links managers to
// studios that are missing one, fills only-blank image fields, adds a few new studios for
// geographic spread, tops up posts/activities/comments/likes/activity-events, and seeds a few
// genuinely pending admin-queue items. Dry-run by default; pass --write to commit.
//
// New documents get isDemo:true + seedBatchId so they stay identifiable/reversible, matching
// the convention already used by demoData.js/seedDemoData.js. Merges onto EXISTING documents
// (manager linkage, image fills) never touch that document's own isDemo/ownership identity -
// only the specific blank fields being filled.

const admin = require("firebase-admin");

const args = new Set(process.argv.slice(2));
const write = args.has("--write");
const dryRun = !write;
const BATCH = "enrich_v1";
const PASSWORD = "DemoFlow!2026";

if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  console.error("Set GOOGLE_APPLICATION_CREDENTIALS to a local Firebase service account JSON file before using --write.");
  process.exit(1);
}
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId: process.env.FIREBASE_PROJECT_ID
  });
}
const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;

const CITIES = [
  { name: "Tel Aviv", lat: 32.0853, lng: 34.7818 },
  { name: "Jerusalem", lat: 31.7683, lng: 35.2137 },
  { name: "Haifa", lat: 32.794, lng: 34.9896 },
  { name: "Beer Sheva", lat: 31.253, lng: 34.7915 },
  { name: "Ramat Gan", lat: 32.0684, lng: 34.8248 },
  { name: "Rishon LeZion", lat: 31.973, lng: 34.7925 },
  { name: "Eilat", lat: 29.5581, lng: 34.9482 },
  { name: "Netanya", lat: 32.3328, lng: 34.86 },
  { name: "Herzliya", lat: 32.1663, lng: 34.8437 },
  { name: "Givatayim", lat: 32.0722, lng: 34.8125 }
];
const STYLES = ["Hip Hop", "Salsa", "Contemporary", "Ballet", "Heels", "Latin", "Breaking", "Jazz", "Ballroom", "Bachata", "Afro", "House"];

// Existing dance-specific photos already live in the app today (from demoData.js) - reused for
// studio logos/covers and a slice of posts for genuine dance-content flavor. Everything else
// uses reliably-resolving generic placeholder services (Pravatar for face-shaped avatars,
// Picsum for general cover/media photography) - the user explicitly approved Picsum/Unsplash-
// style sources, and neither ever 404s, unlike hand-guessed Wikimedia filenames would risk.
const DANCE_PHOTOS = [
  "https://commons.wikimedia.org/wiki/Special:FilePath/Ballet%20Dancer.jpg",
  "https://commons.wikimedia.org/wiki/Special:FilePath/The%20lone%20ballet%20dancer.jpg",
  "https://commons.wikimedia.org/wiki/Special:FilePath/Sf%20hiphop.jpg",
  "https://commons.wikimedia.org/wiki/Special:FilePath/Ballet-dancer%2001.jpg"
];
function avatarUrl(seed) {
  return `https://i.pravatar.cc/300?u=${encodeURIComponent(seed)}`;
}
function stockUrl(seed, w = 800, h = 500) {
  return `https://picsum.photos/seed/${encodeURIComponent(seed)}/${w}/${h}`;
}
function coverImageFor(seed, index) {
  return index % 4 === 0 ? DANCE_PHOTOS[index % DANCE_PHOTOS.length] : stockUrl(seed);
}

main().catch((error) => {
  console.error(error.stack || error.message || error);
  process.exit(1);
});

async function main() {
  const [usersSnap, studiosSnap, postsSnap] = await Promise.all([
    db.collection("users").get(),
    db.collection("studios").get(),
    db.collection("posts").get()
  ]);
  const existingUsers = usersSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const existingStudios = studiosSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const existingPosts = postsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));

  const plan = buildPlan({ existingUsers, existingStudios, existingPosts });
  printSummary(plan);

  if (dryRun) {
    console.log("\nDry run only. Nothing was written to Firebase. Re-run with --write to commit.");
    return;
  }

  await upsertAuthUsers(plan.newManagerUsers);
  await commitWrites(plan);
  console.log(`\nEnrichment complete for batch ${BATCH}.`);
}

function buildPlan({ existingUsers, existingStudios, existingPosts }) {
  const demoMeta = { isDemo: true, seedBatchId: BATCH };
  const usersById = Object.fromEntries(existingUsers.map((u) => [u.id, u]));

  // --- 1. New studio-manager accounts for every studio currently missing one ---
  const managerGaps = [
    { studioId: "SBUgSpmu75FbedWhLHl8", label: "Studio Naim (Tel Aviv)", count: 2 },
    { studioId: "demo_small_v1_studio_02", label: "Studio Luna Demo (Jerusalem)", count: 1 },
    { studioId: "eRTCgft2PhJ722leWXvC", label: "Be Street (Haifa)", count: 1 },
    { studioId: "maPQn7PIJbkIfUWYEwQR", label: "Be Street (Ramat Gan)", count: 1 },
    { studioId: "qWFL7hx64iT3G2ngb7Zq", label: "Be Street (Rishon LeZion)", count: 1 }
  ].filter((gap) => existingStudios.some((s) => s.id === gap.studioId));

  const managerFirstNames = ["Roni", "Idan", "Meital", "Guy", "Noga", "Eitan", "Shahar"];
  const managerLastNames = ["Ben-David", "Azulay", "Katz", "Nahmani", "Sadeh", "Tzur", "Golan"];
  const newManagerUsers = [];
  let managerIndex = 0;
  managerGaps.forEach((gap) => {
    for (let i = 0; i < gap.count; i += 1) {
      managerIndex += 1;
      const uid = `${BATCH}_manager_${String(managerIndex).padStart(2, "0")}`;
      newManagerUsers.push({
        uid,
        email: `manager${String(managerIndex).padStart(2, "0")}@theflow.demo`,
        password: PASSWORD,
        firstName: managerFirstNames[(managerIndex - 1) % managerFirstNames.length],
        lastName: managerLastNames[(managerIndex - 1) % managerLastNames.length],
        role: "studio_manager",
        managedStudioIds: [gap.studioId],
        professionalBadges: ["Studio Manager"],
        onboardingCompleted: true,
        profileImageUrl: avatarUrl(uid),
        coverImageUrl: stockUrl(`${uid}_cover`),
        headline: `Manager at ${gap.label}`,
        bio: `Helping run day-to-day operations and class scheduling at ${gap.label}.`,
        location: gap.label.match(/\(([^)]+)\)/)?.[1] || "Tel Aviv",
        danceStyles: [],
        danceLevel: "",
        assignedStudioId: gap.studioId,
        assignedStudioLabel: gap.label,
        ...demoMeta
      });
    }
  });

  // --- 2. New studios for geographic spread. Eilat gets its own manager; Netanya/Herzliya
  // stay deliberately unclaimed with a pending studioClaim each, so the claim-approval flow
  // has real pending items even after every currently-gapped studio gets linked above. ---
  managerIndex += 1;
  const eilatManagerUid = `${BATCH}_manager_${String(managerIndex).padStart(2, "0")}`;
  newManagerUsers.push({
    uid: eilatManagerUid,
    email: `manager${String(managerIndex).padStart(2, "0")}@theflow.demo`,
    password: PASSWORD,
    firstName: "Liad",
    lastName: "Peretz",
    role: "studio_manager",
    managedStudioIds: [`${BATCH}_studio_eilat`],
    professionalBadges: ["Studio Manager"],
    onboardingCompleted: true,
    profileImageUrl: avatarUrl(eilatManagerUid),
    coverImageUrl: stockUrl(`${eilatManagerUid}_cover`),
    headline: "Manager at Red Sea Dance Co. (Eilat)",
    bio: "Running Eilat's newest dance studio, open to travelers and locals alike.",
    location: "Eilat",
    danceStyles: [],
    danceLevel: "",
    assignedStudioId: `${BATCH}_studio_eilat`,
    assignedStudioLabel: "Red Sea Dance Co. (Eilat)",
    ...demoMeta
  });

  const newStudios = [
    {
      id: `${BATCH}_studio_eilat`,
      displayName: "Red Sea Dance Co.",
      address: "14 HaTmarim Blvd, Eilat",
      city: "Eilat",
      location: "Eilat",
      latitude: 29.5581,
      longitude: 34.9482,
      ownerUid: eilatManagerUid,
      managerUids: [eilatManagerUid],
      verified: true,
      bio: "A resort-town studio blending Latin, Afro, and House for a community that dances year-round.",
      danceStyles: ["Latin", "Afro", "House"],
      profileImageUrl: DANCE_PHOTOS[0],
      coverImageUrl: stockUrl(`${BATCH}_studio_eilat_cover`),
      openingHours: "Sun-Thu 09:00-21:00, Fri 09:00-14:00",
      socialLinks: { instagram: "reddancesea_demo" },
      status: "APPROVED",
      claimStatus: "CLAIMED",
      teacherUids: [],
      teacherProfiles: [],
      followersCount: 0,
      postsCount: 0,
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    },
    {
      id: `${BATCH}_studio_netanya`,
      displayName: "Coastline Movement Studio",
      address: "22 Herzl St, Netanya",
      city: "Netanya",
      location: "Netanya",
      latitude: 32.3328,
      longitude: 34.86,
      ownerUid: "",
      managerUids: [],
      verified: false,
      bio: "A community-first studio for Jazz, Contemporary, and Breaking, right by the Netanya coastline.",
      danceStyles: ["Jazz", "Contemporary", "Breaking"],
      profileImageUrl: DANCE_PHOTOS[2],
      coverImageUrl: stockUrl(`${BATCH}_studio_netanya_cover`),
      openingHours: "",
      socialLinks: {},
      status: "APPROVED",
      claimStatus: "UNCLAIMED",
      teacherUids: [],
      teacherProfiles: [],
      followersCount: 0,
      postsCount: 0,
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    },
    {
      id: `${BATCH}_studio_herzliya`,
      displayName: "Marina Dance House",
      address: "5 Marina Square, Herzliya",
      city: "Herzliya",
      location: "Herzliya",
      latitude: 32.1663,
      longitude: 34.8437,
      ownerUid: "",
      managerUids: [],
      verified: false,
      bio: "Ballroom and Salsa classes overlooking the Herzliya marina.",
      danceStyles: ["Ballroom", "Salsa", "Bachata"],
      profileImageUrl: DANCE_PHOTOS[3],
      coverImageUrl: stockUrl(`${BATCH}_studio_herzliya_cover`),
      openingHours: "",
      socialLinks: {},
      status: "APPROVED",
      claimStatus: "UNCLAIMED",
      teacherUids: [],
      teacherProfiles: [],
      followersCount: 0,
      postsCount: 0,
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    }
  ];

  // --- 3. Manager linkage merges onto EXISTING studios (field-level merge only) ---
  const studioManagerMerges = [];
  let cursor = 0;
  managerGaps.forEach((gap) => {
    const assigned = newManagerUsers.slice(cursor, cursor + gap.count).map((u) => u.uid);
    cursor += gap.count;
    studioManagerMerges.push({
      studioId: gap.studioId,
      data: {
        managerUids: assigned,
        ownerUid: assigned[0],
        claimStatus: "CLAIMED",
        updatedAt: "__SERVER_TIMESTAMP__"
      }
    });
  });

  // --- 4. Fill only-blank image fields on existing users/studios ---
  const userImageMerges = existingUsers
    .map((u) => {
      const patch = {};
      if (!(u.profileImageUrl || "").trim()) patch.profileImageUrl = avatarUrl(u.id);
      if (!(u.coverImageUrl || "").trim()) patch.coverImageUrl = coverImageFor(`${u.id}_cover`, hashIndex(u.id));
      return Object.keys(patch).length ? { uid: u.id, data: patch } : null;
    })
    .filter(Boolean);

  const studioImageMerges = existingStudios
    .map((s) => {
      const patch = {};
      if (!(s.profileImageUrl || "").trim()) patch.profileImageUrl = DANCE_PHOTOS[hashIndex(s.id) % DANCE_PHOTOS.length];
      if (!(s.coverImageUrl || "").trim()) patch.coverImageUrl = coverImageFor(`${s.id}_cover`, hashIndex(s.id));
      return Object.keys(patch).length ? { studioId: s.id, data: patch } : null;
    })
    .filter(Boolean);

  // --- Author pool for new posts/activities/comments: every existing user + every new manager ---
  const allAuthors = existingUsers.concat(newManagerUsers.map((m) => ({
    id: m.uid,
    firstName: m.firstName,
    lastName: m.lastName,
    profileImageUrl: m.profileImageUrl,
    verifiedTeacher: false,
    verifiedChoreographer: false
  })));
  const allStudiosForAuthorship = existingStudios.concat(newStudios);

  const posts = buildPosts({ demoMeta, authors: allAuthors, studios: allStudiosForAuthorship, existingPostCount: existingPosts.length });
  const activities = buildActivities({ demoMeta, studios: allStudiosForAuthorship, authors: allAuthors });
  const engagementPool = existingPosts.concat(posts.map((p) => ({ id: p.postId, ...p })));
  const { comments, replies } = buildComments({ demoMeta, posts: engagementPool, authors: allAuthors });
  const { likes, activityEvents } = buildEngagement({ demoMeta, posts: engagementPool, authors: allAuthors, newPostStyleMeta: posts });
  const commentCountByPost = countBy(comments.concat(replies), (c) => c.postId);
  const likeCountByPost = countBy(likes, (l) => l.postId);
  const newPostIds = new Set(posts.map((p) => p.postId));
  // New posts start at likesCount:0/commentsCount:0 in the same write, so it's safe to set the
  // exact final count inline. Existing posts already have real counts from before this run - an
  // increment (not a set) is required so this never clobbers/lowers what's already there.
  posts.forEach((post) => {
    post.likesCount = likeCountByPost[post.postId] || 0;
    post.commentsCount = commentCountByPost[post.postId] || 0;
  });
  const existingPostCountIncrements = existingPosts
    .map((p) => {
      const likeDelta = likeCountByPost[p.id] || 0;
      const commentDelta = commentCountByPost[p.id] || 0;
      if (!likeDelta && !commentDelta) return null;
      return { postId: p.id, likeDelta, commentDelta };
    })
    .filter(Boolean);

  const { studioRequests, studioClaims } = buildAdminQueueItems({ demoMeta, newStudios });

  return {
    newManagerUsers,
    newStudios,
    studioManagerMerges,
    userImageMerges,
    studioImageMerges,
    posts,
    activities,
    comments,
    replies,
    likes,
    activityEvents,
    existingPostCountIncrements,
    studioRequests,
    studioClaims
  };
}

// ---------------------------------------------------------------------------
// Post generation - varied tone/length caption pools, wide style/city coverage.
// ---------------------------------------------------------------------------
const CAPTION_TEMPLATES = [
  (s) => `Just left ${s} class and I'm still buzzing. My legs are toast but worth every second.`,
  (s) => `That ${s} combo we drilled tonight is living in my head rent free. Teach it again next week please!!`,
  (s) => `First time trying ${s} and I already want to come back tomorrow. Why did nobody tell me sooner.`,
  (s) => `Sweat, laughs, and a killer ${s} combo. Tonight's class hit different.`,
  (s) => `Ok ${s} class tonight actually broke me a little (in the best way). See you all Thursday.`,
  (s) => `Three hours of ${s} and I'd do it again right now if my body let me.`,
  (s) => `We performed tonight and I still can't believe it's over. Months of rehearsal for four minutes on stage, and I'd relive every second.`,
  (s) => `Stepping off that stage after our ${s} piece... there's nothing like it. Thank you to everyone who came out to support us.`,
  (s) => `Performing ${s} tonight taught me more about myself than a year of just training ever did. Grateful doesn't even cover it.`,
  (s) => `The nerves before, the blur during, the joy after. That's what ${s} performance nights are made of.`,
  (s) => `Watching the footage back from tonight and honestly proud of how far this piece came. We started as strangers and finished as a team.`,
  (s) => `Anyone have tips for keeping balance during ${s} turns? Mine fall apart every single time and I'm losing my mind a little.`,
  (s) => `How long did it take you all to feel comfortable improvising in ${s}? I freeze up the second the counts stop.`,
  (s) => `Looking for advice - my hips just will not cooperate with ${s} isolations. What finally clicked for you?`,
  (s) => `Does anyone have a good stretching routine before ${s} class? My hamstrings are staging a protest.`,
  (s) => `Genuine question for the ${s} dancers here - how do you deal with performance nerves? Asking for a very anxious me.`,
  (s) => `One year ago I couldn't do a single ${s} combo without stopping halfway through. Tonight I ran the whole routine clean. Growth is real.`,
  (s) => `Today marks 6 months since I walked into my first ${s} class scared out of my mind. Look at me now.`,
  (s) => `Booked my first paid ${s} gig today. Eighteen-year-old me who used to dance alone in her room would not believe this.`,
  (s) => `Finally landed the move I've been drilling for weeks in ${s}. Small win but it's MY win.`,
  (s) => `Two years of ${s} and I finally feel like a dancer instead of someone just copying steps. What a feeling.`,
  (s) => `Our next ${s} showcase is coming up - grab your spot before it fills, last one sold out in two days!`,
  (s) => `New ${s} workshop just got added to the schedule. All levels welcome, bring your energy.`,
  (s) => `Save the date - our ${s} intensive weekend is happening soon and spots are limited.`,
  (s) => `Big one coming up: a full ${s} evening with guest instructors. Tag someone you want to bring.`,
  (s) => `We're hosting a ${s} jam this month, open floor, all styles welcome to come watch or join in.`,
  (s) => `Behind the scenes at the studio today - new flooring finally went in and it feels amazing under our feet.`,
  (s) => `Late night at the studio prepping the new ${s} schedule for next month. Excited for what's coming.`,
  (s) => `Our instructors got together tonight to plan the new season and honestly the energy in that room was everything.`,
  (s) => `Studio life: coffee, choreography notes everywhere, and a whiteboard we can barely read anymore.`,
  (s) => `Cleaning out the studio closet and found props from three years ago. The memories are real.`,
  (s) => `Reminder that you don't have to be the best dancer in the room, you just have to be the one who keeps showing up.`,
  (s) => `Bad ${s} class today. Going back tomorrow anyway. That's the whole secret.`,
  (s) => `You are allowed to be a beginner. Everyone in that advanced ${s} class was exactly where you are once.`,
  (s) => `Some days the choreography clicks, some days it doesn't. Both days count.`,
  (s) => `Rainy day, ${s} class, and a good playlist. Could be worse.`,
  (s) => `Anyone else's week feel impossible without ${s} class to look forward to?`,
  (s) => `Quick check in - who's coming to open floor tonight? Need my dance people.`,
  (s) => `Realized I haven't posted in a while, life's been busy but ${s} class is still the highlight of my week.`,
  (s) => `Found this old ${s} rehearsal video from way back. We looked so serious, we were so not ready.`,
  (s) => `Throwback to the first ${s} class I ever taught. I was more nervous than the students.`,
  (s) => `Old footage resurfaced from our very first showcase. We've come a long way since then.`
];
const COLLAB_TEMPLATES = [
  (s) => `Looking for a ${s} partner for an upcoming showcase, must be reliable and up for weekend rehearsals.`,
  (s) => `Need a ${s} collab partner for a video project, styles can mix, just bring good energy.`,
  (s) => `Anyone want to trade lessons? I'll teach ${s} basics if you teach me something completely different.`,
  (s) => `Searching for a duet partner, ${s} focus, no pressure just want to create something fun.`
];

function buildPosts({ demoMeta, authors, studios, existingPostCount }) {
  const posts = [];
  const targetNewPosts = 70;
  for (let i = 0; i < targetNewPosts; i += 1) {
    const author = authors[(i * 7 + 3) % authors.length];
    const city = CITIES[i % CITIES.length];
    const style = STYLES[(i * 3 + 1) % STYLES.length];
    const isCollab = i % 9 === 0;
    const isStudioAuthored = i % 6 === 0;
    const studio = isStudioAuthored ? studios[i % studios.length] : null;
    const id = `${BATCH}_post_${String(existingPostCount + i + 1).padStart(3, "0")}`;
    const templatePool = isCollab ? COLLAB_TEMPLATES : CAPTION_TEMPLATES;
    const template = templatePool[i % templatePool.length];
    const hasImage = i % 3 !== 2;
    posts.push({
      postId: id,
      authorId: author.id,
      authorName: `${author.firstName} ${author.lastName}`.trim(),
      authorProfileImageUrl: author.profileImageUrl || "",
      authorType: author.verifiedTeacher ? "teacher" : author.verifiedChoreographer ? "choreographer" : "dancer",
      authorEntityType: isStudioAuthored ? "studio" : "",
      authorEntityId: isStudioAuthored ? studio.id : "",
      authorEntityName: isStudioAuthored ? (studio.displayName || studio.name || "") : "",
      authorEntityImageUrl: isStudioAuthored ? (studio.profileImageUrl || "") : "",
      text: template(style),
      mediaUrls: hasImage ? [coverImageFor(`${id}_media`, i)] : [],
      mediaItems: hasImage ? [{
        id: `${id}_media_01`,
        url: coverImageFor(`${id}_media`, i),
        mediaType: "photo",
        visibleInMedia: true,
        pinned: false,
        uploadedAt: Date.now() - i * 3600000
      }] : [],
      mediaType: hasImage ? "photo" : "none",
      postType: isCollab ? "collaboration" : "regular",
      collaborationLookingFor: isCollab ? "Practice partner" : "",
      collaborationStyle: isCollab ? style : "",
      collaborationLocation: isCollab ? city.name : "",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta,
      _style: style,
      _city: city.name
    });
  }
  return posts;
}

function buildActivities({ demoMeta, studios, authors }) {
  const activities = [];
  const targetCount = 22;
  const now = Date.now();
  for (let i = 0; i < targetCount; i += 1) {
    const studio = studios[i % studios.length];
    const instructor = authors[(i * 5 + 2) % authors.length];
    const city = { name: studio.city || studio.location || CITIES[i % CITIES.length].name };
    const style = STYLES[(i * 2 + 1) % STYLES.length];
    const activityType = i % 4 === 0 ? "workshop" : i % 7 === 0 ? "event" : "class";
    // Spread across the next ~18 days so "This Week" and "later" both have real hits.
    const startAtMillis = now + (i % 18) * 24 * 3600 * 1000 + ((i * 37) % 6 + 17) * 3600 * 1000;
    const id = `${BATCH}_activity_${String(i + 1).padStart(2, "0")}`;
    activities.push({
      id,
      creatorUid: instructor.id,
      instructorUid: instructor.id,
      instructorName: `${instructor.firstName} ${instructor.lastName}`.trim(),
      hostStudioId: studio.id,
      hostStudioName: studio.displayName || studio.name || "",
      title: `${style} ${activityType === "workshop" ? "Workshop" : activityType === "event" ? "Showcase" : "Class"}`,
      description: `A ${style.toLowerCase()} ${activityType} hosted at ${studio.displayName || studio.name || "our studio"} in ${city.name}, open to ${i % 3 === 0 ? "all levels" : "intermediate and up"}.`,
      activityType,
      styles: [style],
      levels: [i % 3 === 0 ? "All levels" : i % 3 === 1 ? "Intermediate" : "Advanced"],
      startAtMillis,
      timezone: "Asia/Jerusalem",
      price: {
        isFree: i % 8 === 0,
        amount: i % 8 === 0 ? null : 60 + (i % 5) * 15,
        currency: "ILS",
        displayText: i % 8 === 0 ? "Free" : ""
      },
      registration: {
        isOpen: true,
        externalUrl: "",
        whatsapp: "",
        phone: "",
        email: "",
        capacity: 20 + (i % 3) * 10,
        spotsRemaining: 5 + (i % 10),
        showSpotsRemaining: i % 2 === 0
      },
      location: {
        type: "studio",
        name: studio.displayName || studio.name || "",
        address: studio.address || "",
        city: city.name,
        latitude: studio.latitude || (CITIES.find((c) => c.name === city.name) || {}).lat || null,
        longitude: studio.longitude || (CITIES.find((c) => c.name === city.name) || {}).lng || null,
        visibility: "public"
      },
      mediaUrls: [coverImageFor(`${id}_cover`, i)],
      ratingSummary: { average: null, count: 0 },
      status: "published",
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    });
  }
  return activities;
}

// ---------------------------------------------------------------------------
// Comments/replies - real threaded conversations via posts/{id}/comments/{id}/replies/{id},
// unevenly distributed: ~40% get a multi-turn conversation, ~30% get one comment, ~30% none.
// ---------------------------------------------------------------------------
const CONVERSATIONS = [
  (name) => [
    { who: "other", text: "How long did this combo take you to learn?? It looks so clean" },
    { who: "poster", text: "Honestly like 3 classes to get it half right, still cleaning it up" },
    { who: "other", text: "That's so encouraging, thank you!" }
  ],
  () => [
    { who: "other", text: "You're glowing, this made my day" },
    { who: "poster", text: "stop it you're going to make me cry, thank you" }
  ],
  (name) => [
    { who: "other", text: `@${name} we need to try this combo together` },
    { who: "poster", text: "say less, studio Thursday?" },
    { who: "other", text: "deal, bringing snacks" }
  ],
  () => [
    { who: "other", text: "remember when we couldn't even get through the warm up without dying" },
    { who: "poster", text: "do NOT remind me, we've come so far lol" },
    { who: "other", text: "we really have" }
  ],
  () => [
    { who: "other", text: "be honest, is my timing off in the second half?" },
    { who: "poster", text: "a little in the turns but the energy carries it, don't stress" },
    { who: "other", text: "ok phew, appreciate the honesty" }
  ],
  () => [
    { who: "other", text: "WAIT this is huge, congratulations!!" },
    { who: "poster", text: "thank you so much, still can't believe it" },
    { who: "other", text: "so proud of you, seriously" }
  ],
  () => [
    { who: "other", text: "what time does this class run? thinking of joining" },
    { who: "poster", text: "usually 7pm but check the studio page, it shifted last month" },
    { who: "other", text: "perfect, see you there maybe!" }
  ],
  () => [
    { who: "other", text: "the way you disappeared into that spin and reappeared somehow on beat" },
    { who: "poster", text: "pure luck honestly, don't tell anyone" },
    { who: "other", text: "your secret is safe with me" }
  ],
  () => [
    { who: "other", text: "sending you so much love, you've got this" },
    { who: "poster", text: "needed to hear that today, thank you truly" }
  ],
  () => [
    { who: "other", text: "try dropping your weight lower before the turn, helped me so much" },
    { who: "other2", text: "seconding this, also slow it wayyy down at first" },
    { who: "poster", text: "trying this tomorrow, thank you both!" }
  ],
  () => [
    { who: "other", text: "I am SO there, already told everyone" },
    { who: "poster", text: "yesss can't wait to see you all" }
  ],
  () => [
    { who: "other", text: "this brought back so many memories" },
    { who: "poster", text: "right?? feels like yesterday" },
    { who: "other", text: "time flies when you're spinning I guess" }
  ]
];
const SINGLE_COMMENTS = [
  "This is beautiful",
  "Need this energy in my life",
  "Obsessed with this",
  "Yesss!!",
  "Save me a spot next time",
  "This studio is so lucky to have you",
  "The transitions though, incredible",
  "Okay but the outfit too",
  "Screaming this is so good",
  "Wish I could've been there"
];

function buildComments({ demoMeta, posts, authors }) {
  const comments = [];
  const replies = [];
  posts.forEach((post, index) => {
    const postId = post.postId || post.id;
    const posterAuthorId = post.authorId;
    const poster = authors.find((a) => a.id === posterAuthorId) || authors[0];
    const bucket = index % 10;
    if (bucket < 4) {
      // Multi-turn conversation.
      const script = CONVERSATIONS[index % CONVERSATIONS.length](poster.firstName);
      const commenterA = authors[(index * 11 + 2) % authors.length];
      const commenterB = authors[(index * 13 + 5) % authors.length];
      let rootCommentId = null;
      let turnOffset = 0;
      script.forEach((turn) => {
        const speaker = turn.who === "poster" ? poster : turn.who === "other2" ? commenterB : commenterA;
        turnOffset += 1;
        if (rootCommentId === null) {
          rootCommentId = `${BATCH}_comment_${postId}_${turnOffset}`;
          comments.push({
            postId,
            commentId: rootCommentId,
            data: {
              commentId: rootCommentId,
              postId,
              authorId: speaker.id,
              authorName: `${speaker.firstName} ${speaker.lastName}`.trim(),
              authorProfileImageUrl: speaker.profileImageUrl || "",
              text: turn.text,
              createdAt: "__SERVER_TIMESTAMP__",
              ...demoMeta
            }
          });
        } else {
          const replyId = `${BATCH}_reply_${postId}_${turnOffset}`;
          replies.push({
            postId,
            commentId: rootCommentId,
            replyId,
            data: {
              replyId,
              postId,
              commentId: rootCommentId,
              authorId: speaker.id,
              authorName: `${speaker.firstName} ${speaker.lastName}`.trim(),
              authorProfileImageUrl: speaker.profileImageUrl || "",
              text: turn.text,
              createdAt: "__SERVER_TIMESTAMP__",
              ...demoMeta
            }
          });
        }
      });
    } else if (bucket < 7) {
      // Single isolated comment.
      const commenter = authors[(index * 17 + 4) % authors.length];
      const commentId = `${BATCH}_comment_${postId}_solo`;
      comments.push({
        postId,
        commentId,
        data: {
          commentId,
          postId,
          authorId: commenter.id,
          authorName: `${commenter.firstName} ${commenter.lastName}`.trim(),
          authorProfileImageUrl: commenter.profileImageUrl || "",
          text: SINGLE_COMMENTS[index % SINGLE_COMMENTS.length],
          createdAt: "__SERVER_TIMESTAMP__",
          ...demoMeta
        }
      });
    }
    // Remaining ~30% (bucket 7-9) get nothing - realistic uneven engagement.
  });
  return { comments, replies };
}

// ---------------------------------------------------------------------------
// Likes + activity events, with a handful of personas biased hard toward 1-2 styles so the
// recommendation engine has something obvious to demonstrate.
// ---------------------------------------------------------------------------
const BIAS_PERSONAS = [
  { uid: "demo_small_v1_user_01", styles: ["Hip Hop", "Heels"] },
  { uid: "demo_small_v1_user_02", styles: ["Ballet", "Contemporary"] },
  { uid: "demo_small_v1_user_03", styles: ["Salsa", "Bachata"] },
  { uid: "demo_small_v1_user_04", styles: ["Jazz", "Hip Hop"] },
  { uid: "demo_small_v1_user_05", styles: ["Contemporary", "Afro"] }
];

function buildEngagement({ demoMeta, posts, authors }) {
  const likes = [];
  const activityEvents = [];
  const authorsById = Object.fromEntries(authors.map((a) => [a.id, a]));

  BIAS_PERSONAS.forEach((persona) => {
    if (!authorsById[persona.uid]) return;
    const matchingPosts = posts.filter((p) => postStyleOf(p) && persona.styles.includes(postStyleOf(p)));
    const opposingPosts = posts.filter((p) => postStyleOf(p) && !persona.styles.includes(postStyleOf(p)) && STYLES.includes(postStyleOf(p)));
    // Heavy, consistent engagement with the persona's own styles.
    matchingPosts.slice(0, 14).forEach((p, i) => {
      const postId = p.postId || p.id;
      likes.push({ postId, uid: persona.uid, data: { userId: persona.uid, createdAt: "__SERVER_TIMESTAMP__", ...demoMeta } });
      activityEvents.push(activityEvent(persona.uid, "like_post", "post", postId, postStyleOf(p), demoMeta, 0.9));
      if (i % 4 === 0) activityEvents.push(activityEvent(persona.uid, "save_item", "post", postId, postStyleOf(p), demoMeta, 1.0));
    });
    // Deliberately skip the opposing styles entirely (no likes) - only log a rare view so it
    // isn't a suspicious total absence of any signal, but nowhere near the same weight.
    opposingPosts.slice(0, 2).forEach((p) => {
      const postId = p.postId || p.id;
      activityEvents.push(activityEvent(persona.uid, "view_post", "post", postId, postStyleOf(p), demoMeta, 0.1));
    });
  });

  // Lighter, broader engagement from everyone else so posts don't look untouched.
  posts.forEach((post, index) => {
    const postId = post.postId || post.id;
    const style = postStyleOf(post);
    const engagers = [authors[(index * 9 + 1) % authors.length], authors[(index * 19 + 6) % authors.length]];
    engagers.forEach((user, i) => {
      if (!user || BIAS_PERSONAS.some((b) => b.uid === user.id)) return;
      if ((index + i) % 3 === 0) return; // uneven - not every post gets extra likes
      likes.push({ postId, uid: user.id, data: { userId: user.id, createdAt: "__SERVER_TIMESTAMP__", ...demoMeta } });
      activityEvents.push(activityEvent(user.id, "like_post", "post", postId, style, demoMeta, 0.6));
    });
  });

  return { likes: dedupeLikes(likes), activityEvents };
}

function dedupeLikes(likes) {
  const seen = new Set();
  return likes.filter((like) => {
    const key = `${like.postId}__${like.uid}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function postStyleOf(post) {
  if (post._style) return post._style;
  return post.activityType ? post.activityType.split(" ")[0] : post.collaborationStyle || null;
}

function activityEvent(uid, eventType, targetType, targetId, style, demoMeta, strength) {
  return {
    id: `${demoMeta.seedBatchId}_ae_${uid}_${eventType}_${targetId}`.replace(/[^A-Za-z0-9_-]/g, "_"),
    data: {
      userId: uid,
      eventType,
      targetType,
      targetId,
      danceStyles: style ? [style] : [],
      metadata: { interactionStrength: String(strength) },
      weight: strength,
      createdAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    }
  };
}

// ---------------------------------------------------------------------------
// Admin queue: genuinely pending studioRequests + studioClaims (against the new unclaimed
// studios), so the approval flows are live-demoable.
// ---------------------------------------------------------------------------
function buildAdminQueueItems({ demoMeta, newStudios }) {
  const studioRequests = [
    {
      id: `${BATCH}_request_01`,
      data: {
        requestId: `${BATCH}_request_01`,
        studioName: "Sands Studio",
        city: "Ashdod",
        contactName: "Michal Aviram",
        contactEmail: "michal.demo@theflow.demo",
        contactPhone: "050-1234567",
        danceStyles: ["Hip Hop", "Jazz"],
        message: "Requesting to add our studio, we've been running classes here for 3 years.",
        status: "PENDING",
        createdAt: "__SERVER_TIMESTAMP__",
        ...demoMeta
      }
    },
    {
      id: `${BATCH}_request_02`,
      data: {
        requestId: `${BATCH}_request_02`,
        studioName: "Northline Dance",
        city: "Nahariya",
        contactName: "Oren Ben-Zvi",
        contactEmail: "oren.demo@theflow.demo",
        contactPhone: "052-2345678",
        danceStyles: ["Contemporary", "Ballet"],
        message: "New studio opening next month, would love to be listed ahead of our launch.",
        status: "PENDING",
        createdAt: "__SERVER_TIMESTAMP__",
        ...demoMeta
      }
    },
    {
      id: `${BATCH}_request_03`,
      data: {
        requestId: `${BATCH}_request_03`,
        studioName: "Desert Beat Studio",
        city: "Beer Sheva",
        contactName: "Tamar Ilan",
        contactEmail: "tamar.demo@theflow.demo",
        contactPhone: "054-3456789",
        danceStyles: ["Breaking", "House"],
        message: "We run weekend workshops and want a permanent home on the app.",
        status: "PENDING",
        createdAt: "__SERVER_TIMESTAMP__",
        ...demoMeta
      }
    },
    {
      id: `${BATCH}_request_04`,
      data: {
        requestId: `${BATCH}_request_04`,
        studioName: "Golan Heights Dance Collective",
        city: "Katzrin",
        contactName: "Yotam Peled",
        contactEmail: "yotam.demo@theflow.demo",
        contactPhone: "053-4567890",
        danceStyles: ["Folk", "Contemporary"],
        message: "Small but growing studio in the north, hoping to reach more students through the app.",
        status: "PENDING",
        createdAt: "__SERVER_TIMESTAMP__",
        ...demoMeta
      }
    }
  ];

  const claimTargets = newStudios.filter((s) => s.claimStatus === "UNCLAIMED");
  const claimContacts = [
    { name: "Dana Weiss", email: "dana.claim@theflow.demo" },
    { name: "Amit Sror", email: "amit.claim@theflow.demo" }
  ];
  const studioClaims = claimTargets.map((studio, index) => ({
    id: `${BATCH}_claim_${String(index + 1).padStart(2, "0")}`,
    data: {
      claimId: `${BATCH}_claim_${String(index + 1).padStart(2, "0")}`,
      studioId: studio.id,
      studioName: studio.displayName,
      requesterName: claimContacts[index % claimContacts.length].name,
      requesterEmail: claimContacts[index % claimContacts.length].email,
      message: `I run ${studio.displayName} and would like to claim and manage this listing.`,
      status: "PENDING",
      createdAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    }
  }));

  return { studioRequests, studioClaims };
}

function hashIndex(value) {
  let hash = 0;
  for (let i = 0; i < value.length; i += 1) hash = (hash * 31 + value.charCodeAt(i)) >>> 0;
  return hash;
}
function countBy(items, keyFn) {
  const map = {};
  items.forEach((item) => {
    const key = keyFn(item);
    map[key] = (map[key] || 0) + 1;
  });
  return map;
}

// ---------------------------------------------------------------------------
// Writes
// ---------------------------------------------------------------------------
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

async function commitWrites(plan) {
  const writes = [];

  plan.newManagerUsers.forEach((user) => {
    const { password, assignedStudioId, assignedStudioLabel, ...profile } = user;
    writes.push({ ref: db.collection("users").doc(user.uid), data: profile, merge: true });
  });
  plan.newStudios.forEach((studio) => {
    writes.push({ ref: db.collection("studios").doc(studio.id), data: studio, merge: true });
  });
  plan.studioManagerMerges.forEach((m) => {
    writes.push({ ref: db.collection("studios").doc(m.studioId), data: m.data, merge: true });
  });
  plan.userImageMerges.forEach((m) => {
    writes.push({ ref: db.collection("users").doc(m.uid), data: m.data, merge: true });
  });
  plan.studioImageMerges.forEach((m) => {
    writes.push({ ref: db.collection("studios").doc(m.studioId), data: m.data, merge: true });
  });
  plan.posts.forEach((post) => {
    const { _style, _city, ...data } = post;
    writes.push({ ref: db.collection("posts").doc(post.postId), data, merge: true });
  });
  plan.activities.forEach((activity) => {
    const { startAtMillis, ...rest } = activity;
    const data = { ...rest, startAt: admin.firestore.Timestamp.fromMillis(startAtMillis) };
    writes.push({ ref: db.collection("activities").doc(activity.id), data, merge: true });
  });
  plan.comments.forEach((c) => {
    writes.push({ ref: db.collection("posts").doc(c.postId).collection("comments").doc(c.commentId), data: c.data, merge: true });
  });
  plan.replies.forEach((r) => {
    writes.push({
      ref: db.collection("posts").doc(r.postId).collection("comments").doc(r.commentId).collection("replies").doc(r.replyId),
      data: r.data,
      merge: true
    });
  });
  plan.likes.forEach((like) => {
    writes.push({ ref: db.collection("posts").doc(like.postId).collection("likes").doc(like.uid), data: like.data, merge: true });
  });
  plan.activityEvents.forEach((event) => {
    writes.push({ ref: db.collection("userActivityEvents").doc(event.id), data: event.data, merge: true });
  });
  plan.existingPostCountIncrements.forEach((update) => {
    writes.push({
      ref: db.collection("posts").doc(update.postId),
      data: {
        likesCount: FieldValue.increment(update.likeDelta),
        commentsCount: FieldValue.increment(update.commentDelta)
      },
      merge: true
    });
  });
  plan.studioRequests.forEach((r) => {
    writes.push({ ref: db.collection("studioRequests").doc(r.id), data: r.data, merge: true });
  });
  plan.studioClaims.forEach((c) => {
    writes.push({ ref: db.collection("studioClaims").doc(c.id), data: c.data, merge: true });
  });

  for (const chunk of chunks(writes, 450)) {
    const batch = db.batch();
    chunk.forEach((w) => batch.set(w.ref, withServerTimestamps(w.data), { merge: w.merge !== false }));
    await batch.commit();
  }
}

function withServerTimestamps(value) {
  if (value === "__SERVER_TIMESTAMP__") return FieldValue.serverTimestamp();
  if (value instanceof admin.firestore.Timestamp) return value;
  if (value instanceof FieldValue) return value;
  if (Array.isArray(value)) return value.map(withServerTimestamps);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, withServerTimestamps(v)]));
  }
  return value;
}

function chunks(items, size) {
  const result = [];
  for (let i = 0; i < items.length; i += size) result.push(items.slice(i, i + size));
  return result;
}

function printSummary(plan) {
  console.log(`THE FLOW enrichment ${dryRun ? "dry run" : "WRITE MODE"}`);
  console.log(`Batch: ${BATCH}`);
  console.log(`New manager accounts: ${plan.newManagerUsers.length}`);
  plan.newManagerUsers.forEach((u) => console.log(`  - ${u.email} -> ${u.assignedStudioLabel}`));
  console.log(`New studios: ${plan.newStudios.length} (${plan.newStudios.map((s) => `${s.displayName} [${s.city}]`).join(", ")})`);
  console.log(`Manager-linkage merges onto existing studios: ${plan.studioManagerMerges.length}`);
  console.log(`Image fills - users: ${plan.userImageMerges.length}, studios: ${plan.studioImageMerges.length}`);
  console.log(`New posts: ${plan.posts.length}`);
  console.log(`New activities: ${plan.activities.length}`);
  console.log(`New comments: ${plan.comments.length}, replies: ${plan.replies.length}`);
  console.log(`New likes: ${plan.likes.length}`);
  console.log(`New userActivityEvents: ${plan.activityEvents.length}`);
  console.log(`Existing-post like/comment count increments: ${plan.existingPostCountIncrements.length}`);
  console.log(`New studioRequests (pending): ${plan.studioRequests.length}`);
  console.log(`New studioClaims (pending): ${plan.studioClaims.length}`);
}
