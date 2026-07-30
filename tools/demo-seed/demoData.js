const CITIES = ["Tel Aviv", "Ramat Gan", "Givatayim", "Haifa", "Jerusalem", "Beer Sheva", "Herzliya", "Netanya"];
const STYLES = ["Hip Hop", "Heels", "Ballet", "Contemporary", "Salsa", "Bachata", "Jazz", "Afro", "House", "Breaking"];
const LEVELS = ["Beginner", "Intermediate", "Advanced", "Open level"];
const DEMO_IMAGES = [
  "https://commons.wikimedia.org/wiki/Special:FilePath/Ballet%20Dancer.jpg",
  "https://commons.wikimedia.org/wiki/Special:FilePath/The%20lone%20ballet%20dancer.jpg",
  "https://commons.wikimedia.org/wiki/Special:FilePath/Sf%20hiphop.jpg",
  "https://commons.wikimedia.org/wiki/Special:FilePath/Ballet-dancer%2001.jpg"
];

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
  const dancerCount = full ? 22 : 5;
  const professionalCount = full ? 8 : 2;
  const studioCount = full ? 9 : 2;
  const postCount = full ? 55 : 10;
  const eventCount = full ? 12 : 5;
  const jobCount = full ? 20 : 4;

  const demoMeta = { isDemo: true, seedBatchId };
  const users = [];
  const studios = [];
  const posts = [];
  const jobs = [];
  const jobApplications = [];
  const savedJobs = [];
  const notifications = [];
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
      role: "dancer",
      verifiedTeacher: professional && i % 2 === 0,
      verifiedChoreographer: professional && i % 2 === 1,
      professionalBadges: professional ? [i % 2 === 0 ? "Verified Teacher" : "Choreographer"] : [],
      managedStudioIds: [],
      onboardingCompleted: true,
      coverImageUrl: DEMO_IMAGES[(i + 1) % DEMO_IMAGES.length],
      danceStyles: persona.styles,
      danceLevel: persona.level,
      location: persona.city,
      profileImageUrl: DEMO_IMAGES[i % DEMO_IMAGES.length],
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
      profileImageUrl: DEMO_IMAGES[i % DEMO_IMAGES.length],
      coverImageUrl: DEMO_IMAGES[(i + 2) % DEMO_IMAGES.length],
      socialLinks: {},
      status: "APPROVED",
      claimStatus: "",
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    });
  }

  studios.forEach((studio, index) => {
    const manager = users[dancerCount + (index % professionalCount)];
    if (!manager) return;
    manager.role = "studio_manager";
    manager.managedStudioIds = Array.from(new Set([...(manager.managedStudioIds || []), studio.id]));
    manager.professionalBadges = Array.from(new Set([...(manager.professionalBadges || []), "Studio Manager"]));
    studio.managerUids = [manager.uid];
    studio.ownerUid = manager.uid;
  });

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
      mediaUrls: i % 3 === 0 ? [DEMO_IMAGES[i % DEMO_IMAGES.length]] : [],
      mediaItems: i % 3 === 0 ? [{
        id: `${id}_media_01`,
        url: DEMO_IMAGES[i % DEMO_IMAGES.length],
        mediaType: "photo",
        visibleInMedia: true,
        pinned: false,
        uploadedAt: Date.now()
      }] : [],
      mediaType: i % 3 === 0 ? "photo" : "none",
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
    if (index < 6 || index % 17 !== 0 || post.postType === "dance_activity") return;
    const original = posts[(index - 5) % index];
    post.postType = "repost";
    post.text = "";
    post.mediaUrls = [];
    post.mediaItems = [];
    post.mediaType = "none";
    post.originalPostId = original.postId;
    post.originalAuthorId = original.authorId;
    post.originalAuthorName = original.authorName;
  });

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

  for (let i = 0; i < jobCount; i += 1) {
    const studio = studios[i % studios.length];
    const publishedByStudio = i % 2 === 0;
    const professional = publishedByStudio
      ? users.find((user) => (user.managedStudioIds || []).includes(studio.id))
      : users.find((user, index) => index >= dancerCount && user.verifiedChoreographer)
        || users[dancerCount];
    const persona = PERSONAS[i % PERSONAS.length];
    const id = `${seedBatchId}_job_${String(i + 1).padStart(2, "0")}`;
    const status = i % 9 === 0 ? "filled" : i % 7 === 0 ? "closed" : "active";
    jobs.push({
      jobId: id,
      title: [
        "Dance Teacher",
        "Choreographer",
        "Studio Receptionist",
        "Workshop Instructor",
        "Backup Dancer",
        "Dance Content Creator",
        "Dance Photographer"
      ][i % 7],
      employerName: publishedByStudio ? studio.displayName : `${professional.firstName} ${professional.lastName}`,
      employerImageUrl: publishedByStudio ? studio.profileImageUrl : professional.profileImageUrl,
      city: persona.city,
      location: persona.city,
      workType: ["on_site", "hybrid", "remote"][i % 3],
      jobType: ["freelance", "part_time", "one_time", "temporary", "full_time"][i % 5],
      danceStyles: persona.styles,
      experienceLevel: LEVELS[i % LEVELS.length],
      description: `Demo ${persona.styles[0]} opportunity in ${persona.city}. Looking for reliable dance community talent with strong communication and presence.`,
      requirements: [`${persona.styles[0]} experience`, "Available for rehearsals", "Portfolio or social link preferred"],
      paymentText: i % 4 === 0 ? "Paid, rate discussed with selected candidates" : "",
      deadlineAt: "__SERVER_TIMESTAMP__",
      contactMethod: "Apply in THE FLOW",
      externalApplyUrl: i % 6 === 0 ? "https://example.com/the-flow-demo-job" : "",
      status,
      creatorId: professional.uid,
      studioId: publishedByStudio ? studio.id : "",
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    });
  }

  jobs.filter((job) => job.status === "active").slice(0, full ? 12 : 3).forEach((job, index) => {
    const applicant = users[index % dancerCount];
    const applicationId = `${job.jobId}_${applicant.uid}`;
    jobApplications.push({
      applicationId,
      jobId: job.jobId,
      applicantId: applicant.uid,
      applicantName: `${applicant.firstName} ${applicant.lastName}`,
      introduction: `I am interested in this ${job.danceStyles[0]} opportunity and available for a demo interview.`,
      experience: `${applicant.danceLevel} dancer in ${applicant.location}.`,
      portfolioUrl: "",
      status: ["submitted", "viewed", "contacted", "accepted", "rejected"][index % 5],
      createdAt: "__SERVER_TIMESTAMP__",
      updatedAt: "__SERVER_TIMESTAMP__",
      ...demoMeta
    });
    savedJobs.push({
      uid: applicant.uid,
      jobId: job.jobId,
      data: {
        jobId: job.jobId,
        title: job.title,
        employerName: job.employerName,
        city: job.city,
        workType: job.workType,
        jobType: job.jobType,
        danceStyles: job.danceStyles,
        status: job.status,
        savedAt: "__SERVER_TIMESTAMP__",
        ...demoMeta
      }
    });
    notifications.push({
      uid: job.creatorId,
      notificationId: `${seedBatchId}_notification_${applicationId}`,
      data: {
        notificationId: `${seedBatchId}_notification_${applicationId}`,
        type: "job_application_received",
        actorId: applicant.uid,
        actorName: `${applicant.firstName} ${applicant.lastName}`,
        actorProfileImageUrl: applicant.profileImageUrl,
        applicationId,
        title: "New job application",
        message: `New application for ${job.title}.`,
        createdAt: "__SERVER_TIMESTAMP__",
        isRead: false,
        ...demoMeta
      }
    });
  });

  posts.forEach((post) => {
    post.likesCount = likes.filter((like) => like.postId === post.postId).length;
    post.commentsCount = comments.filter((comment) => comment.postId === post.postId).length;
  });

  return { seedBatchId, size, users, studios, posts, jobs, jobApplications, savedJobs, notifications, comments, likes, savedItems, follows, activityEvents, recommendationProfiles };
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
