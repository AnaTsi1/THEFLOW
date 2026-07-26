# THE FLOW Demo Data Seeder

These tools generate demo-only Firebase Authentication users and Firestore documents for testing THE FLOW recommendations.

Nothing is written by default. The seed script runs in dry-run mode unless you pass `--write`.

## Safety Rules

- Every generated Firestore document includes `isDemo: true` and a shared `seedBatchId`.
- Document IDs are deterministic for each batch, so re-running the same batch updates the same demo docs instead of creating duplicates.
- The delete script only targets documents with both `isDemo == true` and the selected `seedBatchId`.
- Auth users are deleted only when their matching Firestore `users/{uid}` document is also marked as demo for the selected batch.
- Demo users do not create notifications, private messages, push notifications, or external calls.
- No real people, real photos, Instagram content, or scraped content are used.

## Setup

From `tools/demo-seed`:

```bash
npm install
```

For real writes only, set a local service account path outside Git:

```bash
set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\local-service-account.json
set FIREBASE_PROJECT_ID=the-flow-54106
```

Do not commit the service account file. The repository ignores common local credential paths.

## Dry Run

Small test batch:

```bash
npm run dry-run:small
```

Full batch:

```bash
npm run dry-run:full
```

Custom batch ID:

```bash
node seedDemoData.js --size small --batch demo_small_v1 --dry-run
```

## Write Demo Data

Run only after reviewing dry-run output:

```bash
node seedDemoData.js --size small --batch demo_small_v1 --write
```

Later full demo:

```bash
node seedDemoData.js --size full --batch demo_full_v1 --write
```

All demo login passwords are:

```text
DemoFlow!2026
```

## Delete Demo Data

Preview deletion:

```bash
node deleteDemoData.js --batch demo_small_v1 --dry-run
```

Delete requires both `--write --confirm <batch>` and an interactive typed confirmation:

```bash
node deleteDemoData.js --batch demo_small_v1 --write --confirm demo_small_v1
```

When prompted, type:

```text
DELETE demo_small_v1
```

## Generated Data Shape

- `users/{uid}`: demo dancers and professionals, onboarding preferences, message/notification defaults.
- `users/{uid}/recommendationProfile/main`: preferred styles, level, location, style/location/target scores.
- `users/{uid}/savedItems/{itemId}`: saved demo studios/classes.
- `users/{uid}/followingDancers/{targetUid}` and `followingTeachers/{targetUid}`: follow edges.
- `users/{uid}/followers/{followerUid}`: reverse follow edges.
- `studios/{studioId}`: approved demo studios.
- `posts/{postId}`: regular posts, collaborations, and `dance_activity` class/event posts.
- `posts/{postId}/likes/{uid}` and `comments/{commentId}`: small engagement graph.
- `userActivityEvents/{eventId}`: recommendation activity signals.

The current app does not have a dedicated events collection. Demo classes/events are represented as posts with `postType: "dance_activity"` and the existing `activity*` fields.
