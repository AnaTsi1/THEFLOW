const CITIES = ["Tel Aviv", "Ramat Gan", "Givatayim", "Haifa", "Jerusalem", "Beer Sheva", "Herzliya", "Netanya"];
const STYLES = ["Hip Hop", "Heels", "Ballet", "Contemporary", "Salsa", "Bachata", "Jazz", "Afro", "House", "Breaking"];
const LEVELS = ["Beginner", "Intermediate", "Advanced", "Open level"];

const PERSONAS = [
  { key: "hipheels_tlv", styles: ["Hip Hop", "Heels"], level: "Intermediate", city: "Tel Aviv" },
  { key: "ballet_modern", styles: ["Ballet", "Contemporary"], level: "Advanced", city: "Jerusalem" },
  { key: "latin_rg", styles: ["Salsa", "Bachata"], level: "Intermediate", city: "Ramat Gan" },
  { key: "beginner_mix", styles: ["Jazz", "Hip Hop"], level: "Beginner", city: "Givatayim" },
  { key: "advanced_stage", styles: ["Contemporary", "Afro"], level: "Advanced", city: "Haifa" }
];

const FIRST_NAMES = ["Noa", "Maya", "Dana", "Yael", "Shira", "Tamar", "Lior", "Amit", "Roni", "Adi", "Neta", "Gal", "Ella", "Hila", "Or", "Ari", "Yarden", "Michal", "Alma", "Niv", "Tal", "Rotem", "Yuval", "Omer", "Bar", "Eden", "Aviv", "Shani", "Maayan", "Inbar", "Hadar", "Keren", "Zohar", "Ofir", "Daria", "Linoy", "Sivan", "Gaya", "Daniel", "Talia"];
const LAST_NAMES = ["Demo-Levi", "Demo-Cohen", "Demo-Mizrahi", "Demo-Peretz", "Demo-Shalev", "Demo-Barak", "Demo-Dahan", "Demo-Harel", "Demo-Amir", "Demo-Rosen"];
const STUDIO_NAMES = ["Beat Room", "Studio Luna", "Latin House", "Move Hub", "North Stage", "Ballet Yard", "Flow Lab", "Urban Steps", "Tempo Studio", "The Floor"];

function buildDemoData({ size = "small", seedBatchId = "demo-local-preview" } = {}) {
  const full = size === "full";
  const dancerCount = full ? 32 : 5;
  const professionalCount = full ? 10 : 2;
  const studioCount = full ? 9 : 2;
  const postCount = full ? 80 : 10;
  const eventCount = full ? 24 : 5;

  const demoMeta = { isDemo: true, seedBatchId };
  const users = [];
  const studios = [];
  const posts = [];
  const comments = [];
  const likes = [];
  const savedItems = [];
  const follows = [];
  const activityEvents = [];
  const recommendationProfiles = [];

  for (let i = 0; i < dancerCount + professionalCount; i += 1) {
    const persona = PERSONAS[i % PERSONAS.length];
    const professional = i >= dancerCount;
    const uid = `${seedBatchId}_user_${String(i + 1).padStart(2, "0")}`;
    users.push({
      uid,
      email: `demo.${String(i + 1).padStart(2, "0")}@theflow.demo`,
      password: "DemoFlow!2026",
      firstName: FIRST_NAMES[i % FIRST_NAMES.length],
      lastName: LAST_NAMES[i % LAST_NAMES.length],
      birthDate: "1998-01-01",
      age: 20 + (i % 18),
      role: professional ? "dancer" : "dancer",
      verifiedTeacher: professional && i % 2 === 0,
      verifiedChoreographer: professional && i % 2 === 1,
      professionalBadges: professional ? [i % 2 === 0 ? "Verified Teacher" : "Choreographer"] : [],
      managedStudioIds: [],
      onboardingCompleted: true,
      coverImageUrl: "",
      danceStyles: persona.styles,
      danceLevel: persona.level,
      location: persona.city,
      profileImageUrl: "",
      headline: professional ? `${persona.styles[0]} teacher in ${persona.city}` : `${persona.level} dancer exploring ${persona.styles.join(" and ")}`,
      bio: `Demo profile for recommendation testing. Interests: ${persona.styles.join(", ")}.`,
      professionalBackground: professional ? `Teaches ${persona.styles.join(" and ")} around ${persona.city}.` : "",
      skills: persona.styles,
      yearsOfExperience: professional ? `${5 + (i % 8)} years` : "",
      studiosTrainedAt: [],
      teachersLearnedFrom: [],
      performancesCompetitions: [],
      availability: professional ? "Evenings and weekends" : "",
      instagramUrl: "",
      tiktokUrl: "",
      youtubeUrl: "",
      portfolioMediaUrls: [],
      notificationSettings: defaultNotificationSettings(),
      messageSettings: defaultMessageSettings(),
      ...demoMeta
    });

    recommendationProfiles.push({
      path: `users/${uid}/recommendationProfile/main`,
      data: {
        preferredStyles: persona.styles,
        preferredLevel: persona.level,
        preferredLocation: persona.city,
        preferredStudios: [],
        preferredTeachers: [],
        preferredDancers: [],
        styleScores: Object.fromEntries(persona.styles.map((style, index) => [scoreKey(style), 8 - index])),
        locationScores: { [scoreKey(persona.city)]: 8 },
        targetTypeScores: professional ? { teacher: 5, class: 4 } : { post: 3, class: 4 },
        updatedAt: "__SERVER_TIMESTAMP__",
        ...demoMeta
      }
    });
  }

  for (let i = 0; i < studioCount; i += 1) {
    const persona = PERSONAS[i % PERSONAS.length];
    const id = `${seedBatchId}_studio_${String(i + 1).padStart(2, "0")}`;
    studios.push({
      id,
      displayName: `${STUDIO_NAMES[i % STUDIO_NAMES.length]} Demo`,
      address: `${12 + i} Demo Street, ${persona.city}`,
      city: persona.city,
      ownerUid: "",
      managerUids: [],
      verified: i % 3 !== 0,
      bio: `Demo studio focused on ${persona.styles.join(", ")}.`,
      location: persona.city,
      danceStyles: persona.styles,
      profileImageUrl: "",
      coverImageUrl: "",
      socialLinks: {},
      status: "APPROVED",
      claimStatus: "",
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    });
  }

  for (let i = 0; i < postCount + eventCount; i += 1) {
    const author = users[i % users.length];
    const persona = PERSONAS[i % PERSONAS.length];
    const isEvent = i >= postCount;
    const id = `${seedBatchId}_post_${String(i + 1).padStart(3, "0")}`;
    const studio = studios[i % studios.length];
    const style = persona.styles[i % persona.styles.length];
    posts.push({
      postId: id,
      authorId: author.uid,
      authorName: `${author.firstName} ${author.lastName}`,
      authorProfileImageUrl: "",
      authorType: author.verifiedTeacher ? "teacher" : author.verifiedChoreographer ? "choreographer" : "dancer",
      text: isEvent
        ? `${style} ${persona.level} session at ${studio.displayName}. Demo event for recommendations.`
        : `Working on ${style} flow in ${persona.city}. Demo post for THE FLOW.`,
      mediaUrls: [],
      mediaItems: [],
      mediaType: "none",
      postType: isEvent ? "dance_activity" : i % 5 === 0 ? "collaboration" : "regular",
      activityType: isEvent ? `${style} class` : "",
      activityLocation: isEvent ? persona.city : "",
      activityDate: isEvent ? `2026-08-${String((i % 20) + 1).padStart(2, "0")}` : "",
      activityTime: isEvent ? `${18 + (i % 4)}:00` : "",
      activityPrice: isEvent ? `${60 + (i % 5) * 15} NIS` : "",
      activityLevel: isEvent ? persona.level : "",
      activityDescription: isEvent ? `A demo ${style} class designed for ${persona.level.toLowerCase()} dancers.` : "",
      collaborationLookingFor: !isEvent && i % 5 === 0 ? "Practice partner" : "",
      collaborationStyle: !isEvent && i % 5 === 0 ? style : "",
      collaborationLocation: !isEvent && i % 5 === 0 ? persona.city : "",
      collaborationDate: "",
      collaborationPaid: "",
      collaborationDescription: "",
      visibility: "public",
      likesCount: 0,
      commentsCount: 0,
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    });
  }

  posts.forEach((post, index) => {
    const interested = users.filter((user) => user.uid !== post.authorId && overlaps(user.danceStyles, [post.activityType.split(" ")[0], post.collaborationStyle, post.text]));
    interested.slice(0, full ? 6 : 2).forEach((user, likeIndex) => {
      likes.push({ postId: post.postId, uid: user.uid, data: { userId: user.uid, createdAt: "__SERVER_TIMESTAMP__", ...demoMeta } });
      activityEvents.push(activity(user, "like_post", "post", post.postId, post.authorName, user.danceStyles, user.location, demoMeta, 0.8));
      if ((index + likeIndex) % 3 === 0) {
        const commentId = `${seedBatchId}_comment_${post.postId}_${user.uid}`;
        comments.push({
          postId: post.postId,
          commentId,
          data: {
            commentId,
            postId: post.postId,
            authorId: user.uid,
            authorName: `${user.firstName} ${user.lastName}`,
            authorProfileImageUrl: "",
            text: "Demo comment: this looks like a great session.",
            createdAt: "__SERVER_TIMESTAMP__",
            ...demoMeta
          }
        });
      }
    });
  });

  users.forEach((user, index) => {
    const persona = PERSONAS[index % PERSONAS.length];
    const targetStudio = studios.find((studio) => studio.city === persona.city) || studios[index % studios.length];
    savedItems.push({
      uid: user.uid,
      itemId: targetStudio.id,
      data: {
        itemId: targetStudio.id,
        itemType: "Studio",
        title: targetStudio.displayName,
        studio: targetStudio.displayName,
        teacher: "Studio",
        style: targetStudio.danceStyles[0],
        level: "All levels",
        location: targetStudio.city,
        time: "Contact studio",
        savedAt: "__SERVER_TIMESTAMP__",
        updatedAt: "__SERVER_TIMESTAMP__",
        ...demoMeta
      }
    });

    const followTarget = users[(index + 3) % users.length];
    if (followTarget.uid !== user.uid) {
      follows.push({
        followerUid: user.uid,
        targetUid: followTarget.uid,
        type: followTarget.verifiedTeacher ? "followingTeachers" : "followingDancers",
        data: {
          targetId: followTarget.uid,
          userId: followTarget.uid,
          followedAt: "__SERVER_TIMESTAMP__",
          ...demoMeta
        }
      });
      activityEvents.push(activity(user, "follow_user", "user", followTarget.uid, `${followTarget.firstName} ${followTarget.lastName}`, followTarget.danceStyles, followTarget.location, demoMeta, 1));
    }
  });

  posts.forEach((post) => {
    post.likesCount = likes.filter((like) => like.postId === post.postId).length;
    post.commentsCount = comments.filter((comment) => comment.postId === post.postId).length;
  });

  return { seedBatchId, size, users, studios, posts, comments, likes, savedItems, follows, activityEvents, recommendationProfiles };
}

function defaultNotificationSettings() {
  return {
    allNotificationsEnabled: true,
    likes: true,
    comments: true,
    newFollowers: true,
    privateMessages: true,
    eventRecommendations: true,
    registeredEventUpdates: true,
    professionalApplicationUpdates: true
  };
}

function defaultMessageSettings() {
  return {
    messageNotificationsEnabled: true,
    showMessagePreviews: true,
    receiveMessagesFrom: "everyone",
    readReceipts: true,
    emojiSuggestions: true
  };
}

function activity(user, eventType, targetType, targetId, targetName, danceStyles, location, demoMeta, strength) {
  return {
    id: `${demoMeta.seedBatchId}_activity_${user.uid}_${eventType}_${targetId}`.replace(/[^A-Za-z0-9_-]/g, "_"),
    data: {
      userId: user.uid,
      eventType,
      targetType,
      targetId,
      targetName,
      danceStyles,
      location,
      metadata: { interactionStrength: String(strength) },
      weight: strength,
      createdAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    }
  };
}

function overlaps(values, textValues) {
  const haystack = textValues.join(" ").toLowerCase();
  return values.some((value) => haystack.includes(value.toLowerCase().split(" ")[0]));
}

function scoreKey(value) {
  return value.trim().replace(/[^A-Za-z0-9_-]/g, "_") || "unknown";
}

module.exports = { buildDemoData };
