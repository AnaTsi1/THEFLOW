# THE FLOW

THE FLOW is an Android app for dancers, studios, and dance professionals. The app combines user authentication, onboarding, discovery, profile media, posts, studio information, and professional verification flows.

## Features

- Firebase Authentication based login and registration
- User onboarding and preference editing
- Home, Discover, Search, Profile, and Settings screens
- Studio and professional discovery flows
- Post and profile media rendering with Glide
- Professional verification and studio claim data models
- Firebase Firestore and Storage repositories
- Google Maps API key support through local configuration

## Tech Stack

- Kotlin
- Android Gradle Plugin
- AndroidX AppCompat, Fragment, Lifecycle, Navigation, and ViewBinding
- Jetpack Compose dependencies are available in the project
- Firebase Auth, Firestore, and Storage
- Google Play Services Maps
- Glide

## Project Structure

```text
app/src/main/java/com/ana/theflow
+-- data
|   +-- model          # User, post, studio, discovery, and activity models
|   +-- repository     # Firebase and app data repositories
+-- ui
|   +-- auth           # Login and registration
|   +-- common         # Shared renderers
|   +-- detail         # Discovery detail screen
|   +-- discover       # Discovery feed
|   +-- home           # Home feed
|   +-- media          # Media viewer
|   +-- onboarding     # Onboarding and preferences
|   +-- profile        # Profile and profile media
|   +-- search         # Search
|   +-- settings       # Settings and professional verification
|   +-- studio         # Studio ViewModel
+-- utilities          # Constants, city options, and validation helpers
```

## Requirements

- Android Studio
- JDK 11 or newer
- Android SDK with compile SDK 36 support
- Firebase project configured for Android package `com.ana.theflow`
- Google Maps API key

## Configuration

1. Add or update `app/google-services.json` with the Firebase Android configuration for the app.
2. Create a `local.properties` file in the project root if it does not already exist.
3. Add the Google Maps API key:

```properties
MAPS_API_KEY=your_google_maps_api_key
```

The app also supports reading `MAPS_API_KEY` from the environment when it is not present in `local.properties`.

## Build

From the project root:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

## Tests

Run local unit tests:

```bash
./gradlew test
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
```

## Firestore

The app uses these main Firestore collections:

- `users`
- `posts`
- `studios`
- `studioClaims`
- `professionalApplications`
- `studioApplications`
- `userActivityEvents`

Firestore security rules are stored in `firestore.rules`.
