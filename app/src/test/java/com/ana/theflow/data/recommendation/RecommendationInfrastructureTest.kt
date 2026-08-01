package com.ana.theflow.data.recommendation

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.utilities.CityOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationInfrastructureTest {

    @Test
    fun normalizationTreatsStyleVariantsAsSameId() {
        val expected = "hip_hop"

        assertEquals(expected, RecommendationNormalizer.styleId("Hip Hop"))
        assertEquals(expected, RecommendationNormalizer.styleId("hip_hop"))
        assertEquals(expected, RecommendationNormalizer.styleId("Hip-Hop"))
    }

    @Test
    fun normalizationTreatsBeerShevaVariantsAsSameCity() {
        val expected = "beer_sheva"

        assertEquals(expected, CityOptions.normalizeCityId("Beer Sheva"))
        assertEquals(expected, CityOptions.normalizeCityId("Be'er Sheva"))
        assertEquals(expected, CityOptions.normalizeCityId("Beersheba"))
        assertEquals(expected, CityOptions.normalizeCityId("באר שבע"))
    }

    @Test
    fun normalizationTreatsLevelVariantsAsSameId() {
        val expected = "intermediate"

        assertEquals(expected, RecommendationNormalizer.levelId("Intermediate"))
        assertEquals(expected, RecommendationNormalizer.levelId(" intermediate "))
        assertEquals(expected, RecommendationNormalizer.levelId("INTERMEDIATE"))
    }

    @Test
    fun discoverUsesPreferredAreaBeforeDeviceLocation() {
        val context = context(
            surface = RecommendationSurface.DISCOVER,
            device = telAviv,
            preferred = "Beer Sheva"
        )

        val resolved = LocationSourceResolver.resolve(context)

        assertEquals(ResolvedLocationSource.PREFERRED_RECOMMENDATION_AREA, resolved.source)
        assertEquals("beer_sheva", resolved.cityId)
    }

    @Test
    fun searchManualCityOverridesDeviceLocation() {
        val context = context(
            surface = RecommendationSurface.SEARCH,
            device = telAviv,
            manual = "Be'er Sheva"
        )

        val resolved = LocationSourceResolver.resolve(context)

        assertEquals(ResolvedLocationSource.MANUAL_SELECTION, resolved.source)
        assertEquals("beer_sheva", resolved.cityId)
        assertEquals(31.2529, resolved.point?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun mapManualCityOverridesDeviceLocation() {
        val context = context(
            surface = RecommendationSurface.MAP,
            device = telAviv,
            manual = "באר שבע"
        )

        val resolved = LocationSourceResolver.resolve(context)

        assertEquals(ResolvedLocationSource.MANUAL_SELECTION, resolved.source)
        assertEquals("beer_sheva", resolved.cityId)
    }

    @Test
    fun mapSearchThisAreaOverridesManualCity() {
        val context = context(
            surface = RecommendationSurface.MAP,
            device = telAviv,
            manual = "Beer Sheva",
            mapCamera = haifa
        )

        val resolved = LocationSourceResolver.resolve(context)

        assertEquals(ResolvedLocationSource.MAP_CAMERA, resolved.source)
        assertEquals(haifa, resolved.point)
    }

    @Test
    fun searchWithoutDeviceFallsBackToPreferredThenProfile() {
        val preferred = LocationSourceResolver.resolve(
            context(surface = RecommendationSurface.SEARCH, preferred = "Beer Sheva", profileLocation = "Tel Aviv")
        )
        val profile = LocationSourceResolver.resolve(
            context(surface = RecommendationSurface.SEARCH, profileLocation = "Tel Aviv")
        )

        assertEquals(ResolvedLocationSource.PREFERRED_RECOMMENDATION_AREA, preferred.source)
        assertEquals("beer_sheva", preferred.cityId)
        assertEquals(ResolvedLocationSource.PROFILE_LOCATION, profile.source)
        assertEquals("tel_aviv", profile.cityId)
    }

    @Test
    fun followingDoesNotResolveLocation() {
        val resolved = LocationSourceResolver.resolve(
            context(surface = RecommendationSurface.FOLLOWING, device = telAviv, preferred = "Beer Sheva")
        )

        assertEquals(ResolvedLocationSource.NONE, resolved.source)
    }

    @Test
    fun searchHardFilterIsNotOverriddenByRecommendationScore() {
        val items = listOf(
            discoveryItem(id = "beer", location = "Beer Sheva", style = "Salsa"),
            discoveryItem(id = "tel", location = "Tel Aviv", style = "Hip Hop")
        )
        val profile = RecommendationProfile(
            userId = "u1",
            danceStyles = listOf("Hip Hop"),
            styleScores = mapOf("hip_hop" to 100.0)
        )
        val ranked = SearchRankingStrategy.rank(
            items,
            context(surface = RecommendationSurface.SEARCH, manual = "Beer Sheva", profile = profile)
        )

        assertEquals(listOf("beer"), ranked.map { it.id })
    }

    @Test
    fun followingStrategyReturnsOnlyFollowedAuthors() {
        val posts = listOf(Post(postId = "a", authorId = "followed"), Post(postId = "b", authorId = "other"))

        val ranked = FollowingRankingStrategy.rank(
            posts,
            context(surface = RecommendationSurface.FOLLOWING, followingIds = setOf("followed"))
        )

        assertEquals(listOf("a"), ranked.map { it.postId })
    }

    @Test
    fun mapStrategyDoesNotReturnItemsWithoutCoordinates() {
        val items = listOf(
            discoveryItem(id = "mapped", location = "Beer Sheva", latitude = 31.2529, longitude = 34.7915),
            discoveryItem(id = "missing", location = "Beer Sheva")
        )

        val ranked = MapRankingStrategy.rank(
            items,
            context(surface = RecommendationSurface.MAP, manual = "Beer Sheva")
        )

        assertEquals(listOf("mapped"), ranked.map { it.id })
    }

    @Test
    fun discoverPreferredAreaBoostRanksMatchingCityHigher() {
        val items = listOf(
            discoveryItem(id = "tel", location = "Tel Aviv"),
            discoveryItem(id = "beer", location = "Beer Sheva")
        )

        val ranked = DiscoverRankingStrategy.rank(
            items,
            context(surface = RecommendationSurface.DISCOVER, preferred = "Beer Sheva")
        )

        assertTrue(ranked.first().id == "beer")
    }

    @Test
    fun likeUpdatesStyleCreatorAndContentType() {
        val plan = RecommendationProfileUpdatePlanner.plan(
            signal = RecommendationSignalType.LIKE,
            features = RecommendationFeatures(
                itemId = "p1",
                styleIds = setOf("hip_hop"),
                creatorId = "teacher_1",
                contentTypeId = "post",
                mediaTypeId = "video"
            )
        )

        assertEquals(3.0, plan.increments["styleScores.hip_hop"] ?: 0.0, 0.001)
        assertEquals(2.4, plan.increments["creatorScores.teacher_1"] ?: 0.0, 0.001)
        assertEquals(1.5, plan.increments["contentTypeScores.post"] ?: 0.0, 0.001)
        assertEquals(1.2, plan.increments["mediaTypeScores.video"] ?: 0.0, 0.001)
    }

    @Test
    fun openProfileMainlyAffectsCreator() {
        val plan = RecommendationProfileUpdatePlanner.plan(
            signal = RecommendationSignalType.VIEW_PROFILE,
            features = RecommendationFeatures(
                itemId = "u2",
                styleIds = setOf("salsa"),
                locationId = "tel_aviv",
                creatorId = "u2",
                creatorTypeId = "teacher"
            )
        )

        assertEquals(3.0, plan.increments["creatorScores.u2"] ?: 0.0, 0.001)
        assertEquals(2.4, plan.increments["creatorTypeScores.teacher"] ?: 0.0, 0.001)
        assertTrue((plan.increments["styleScores.salsa"] ?: 0.0) < 1.0)
        assertTrue((plan.increments["locationScores.tel_aviv"] ?: 0.0) < 0.5)
    }

    @Test
    fun registrationGetsStrongSignalAcrossEventFeatures() {
        val plan = RecommendationProfileUpdatePlanner.plan(
            signal = RecommendationSignalType.REGISTER_EVENT,
            features = RecommendationFeatures(
                itemId = "event1",
                styleIds = setOf("heels"),
                locationId = "beer_sheva",
                teacherId = "teacher_a",
                studioId = "studio_a",
                levelId = "intermediate",
                contentTypeId = "event"
            )
        )

        assertEquals(8.0, plan.increments["styleScores.heels"] ?: 0.0, 0.001)
        assertEquals(7.2, plan.increments["locationScores.beer_sheva"] ?: 0.0, 0.001)
        assertEquals(listOf("event1"), plan.arrayUnions["registeredEventIds"])
    }

    @Test
    fun fastSkipIsWeakAndHideBlocksItem() {
        val skip = RecommendationProfileUpdatePlanner.plan(
            signal = RecommendationSignalType.FAST_SKIP,
            features = RecommendationFeatures(itemId = "p1", styleIds = setOf("salsa"), creatorId = "u1")
        )
        val hide = RecommendationProfileUpdatePlanner.plan(
            signal = RecommendationSignalType.HIDE,
            features = RecommendationFeatures(itemId = "p1", styleIds = setOf("salsa"), creatorId = "u1")
        )

        assertTrue((skip.increments["styleScores.salsa"] ?: 0.0) > -1.0)
        assertEquals(listOf("p1"), hide.arrayUnions["hiddenItemIds"])
        assertEquals(-8.0, hide.increments["creatorScores.u1"] ?: 0.0, 0.001)
    }

    @Test
    fun multipleInterestsCanStayStrongTogether() {
        val profile = RecommendationProfile(
            userId = "u1",
            styleScores = mapOf("hip_hop" to 30.0, "heels" to 28.0, "salsa" to 27.0)
        )

        assertTrue((profile.styleScores["hip_hop"] ?: 0.0) > 25.0)
        assertTrue((profile.styleScores["heels"] ?: 0.0) > 25.0)
        assertTrue((profile.styleScores["salsa"] ?: 0.0) > 25.0)
    }

    @Test
    fun decayReducesButDoesNotEraseOldScore() {
        val now = 200L * 24L * 60L * 60L * 1000L
        val decayed = RecommendationScoreMath.decayed(40.0, 0L, now)
        val recent = RecommendationScoreMath.decayed(40.0, now - 2L * 24L * 60L * 60L * 1000L, now)
        val old = RecommendationScoreMath.decayed(40.0, now - 180L * 24L * 60L * 60L * 1000L, now)

        assertEquals(40.0, decayed, 0.001)
        assertTrue(recent > old)
        assertTrue(old > 0.0)
    }

    @Test
    fun forYouStyleCanBeatLocation() {
        val items = listOf(
            Post(postId = "style", authorId = "a", text = "Hip Hop combo", activityLocation = "Haifa"),
            Post(postId = "location", authorId = "b", text = "Ballet", activityLocation = "Beer Sheva")
        )
        val profile = RecommendationProfile(userId = "u1", danceStyles = listOf("Hip Hop"))

        val ranked = ForYouRankingStrategy.rank(
            items,
            context(surface = RecommendationSurface.FOR_YOU, preferred = "Beer Sheva", profile = profile)
        )

        assertEquals("style", ranked.first().postId)
    }

    @Test
    fun forYouDiversityLimitsLongCreatorRuns() {
        val items = (1..5).map { Post(postId = "same$it", authorId = "same", text = "Hip Hop") } +
            listOf(Post(postId = "other", authorId = "other", text = "Hip Hop"))
        val profile = RecommendationProfile(userId = "u1", danceStyles = listOf("Hip Hop"))

        val ranked = ForYouRankingStrategy.rank(items, context(surface = RecommendationSurface.FOR_YOU, profile = profile))

        assertTrue(ranked.take(3).any { it.authorId == "other" })
    }

    @Test
    fun hiddenContentDoesNotAppearInForYou() {
        val items = listOf(Post(postId = "hidden", text = "Hip Hop"), Post(postId = "visible", text = "Hip Hop"))
        val profile = RecommendationProfile(userId = "u1", hiddenItemIds = setOf("hidden"))

        val ranked = ForYouRankingStrategy.rank(items, context(surface = RecommendationSurface.FOR_YOU, profile = profile))

        assertEquals(listOf("visible"), ranked.map { it.postId })
    }

    @Test
    fun discoverManualHardFilterOverridesLearnedPreference() {
        val items = listOf(
            discoveryItem(id = "tel", location = "Tel Aviv", style = "Hip Hop"),
            discoveryItem(id = "beer", location = "Beer Sheva", style = "Salsa")
        )
        val profile = RecommendationProfile(userId = "u1", styleScores = mapOf("hip_hop" to 80.0), preferredRecommendationArea = "Tel Aviv")

        val ranked = DiscoverRankingStrategy.rank(
            items,
            context(surface = RecommendationSurface.DISCOVER, manual = "Beer Sheva", profile = profile)
        )

        assertEquals(listOf("beer"), ranked.map { it.id })
    }

    @Test
    fun firestoreRecommendationPathsAreUserScoped() {
        assertEquals("users/u1/recommendationProfile/main", RecommendationFirestorePaths.profile("u1"))
        assertEquals(
            "users/u1/recommendationProfile/main/signalDedupe/like_p1",
            RecommendationFirestorePaths.signalDedupe("u1", "like_p1")
        )
        assertEquals("users/u1/recommendationImpressions", RecommendationFirestorePaths.impressions("u1"))
    }

    @Test
    fun dedupeKeysAreStableForSameActionAndItem() {
        val features = RecommendationFeatures(itemId = "post 1", styleIds = setOf("hip_hop"))
        val first = RecommendationProfileUpdatePlanner.plan(RecommendationSignalType.LIKE, features)
        val second = RecommendationProfileUpdatePlanner.plan(RecommendationSignalType.LIKE, features)

        assertEquals(first.dedupeKey, second.dedupeKey)
        assertTrue(RecommendationProfileUpdatePlanner.shouldDedupe(RecommendationSignalType.LIKE))
    }

    @Test
    fun reversibleRegistrationAddsAndRemovesRegisteredEventId() {
        val features = RecommendationFeatures(itemId = "event1", contentTypeId = "event", styleIds = setOf("salsa"))
        val register = RecommendationProfileUpdatePlanner.plan(RecommendationSignalType.REGISTER_EVENT, features)
        val cancel = RecommendationProfileUpdatePlanner.plan(RecommendationSignalType.CANCEL_REGISTRATION, features)

        assertEquals(listOf("event1"), register.arrayUnions["registeredEventIds"])
        assertEquals(listOf("event1"), cancel.arrayRemoves["registeredEventIds"])
    }

    @Test
    fun hiddenItemPersistenceFiltersForYouAndDiscover() {
        val profile = RecommendationProfile(userId = "u1", hiddenItemIds = setOf("hidden"))
        val posts = listOf(Post(postId = "hidden", text = "Hip Hop"), Post(postId = "visible", text = "Hip Hop"))
        val discover = listOf(discoveryItem(id = "hidden", location = "Beer Sheva"), discoveryItem(id = "visible", location = "Beer Sheva"))

        assertEquals(listOf("visible"), ForYouRankingStrategy.rank(posts, context(RecommendationSurface.FOR_YOU, profile = profile)).map { it.postId })
        assertEquals(listOf("visible"), DiscoverRankingStrategy.rank(discover, context(RecommendationSurface.DISCOVER, profile = profile)).map { it.id })
    }

    @Test
    fun registeredEventIsNotRecommendedAgainInDiscover() {
        val profile = RecommendationProfile(userId = "u1", registeredEventIds = setOf("event1"))
        val events = listOf(
            discoveryItem(id = "event1", location = "Beer Sheva").copy(type = "Event", displayType = "event"),
            discoveryItem(id = "event2", location = "Beer Sheva").copy(type = "Event", displayType = "event")
        )

        val ranked = DiscoverRankingStrategy.rank(events, context(RecommendationSurface.DISCOVER, profile = profile))

        assertEquals(listOf("event2"), ranked.map { it.id })
    }

    @Test
    fun manualLocationStillOverridesGpsForRuntimeContext() {
        val resolved = LocationSourceResolver.resolve(
            context(
                surface = RecommendationSurface.SEARCH,
                device = telAviv,
                manual = "Beer Sheva",
                preferred = "Tel Aviv"
            )
        )

        assertEquals(ResolvedLocationSource.MANUAL_SELECTION, resolved.source)
        assertEquals("beer_sheva", resolved.cityId)
    }

    private fun context(
        surface: RecommendationSurface,
        device: GeoPoint? = null,
        manual: String = "",
        mapCamera: GeoPoint? = null,
        preferred: String = "",
        profileLocation: String = "",
        followingIds: Set<String> = emptySet(),
        profile: RecommendationProfile = RecommendationProfile.empty("u1")
    ): RecommendationContext {
        return RecommendationContext(
            userId = "u1",
            surface = surface,
            profileLocation = profileLocation,
            preferredRecommendationArea = preferred,
            currentDeviceLocation = device,
            manualSelectedLocation = manual,
            mapCameraLocation = mapCamera,
            followingIds = followingIds,
            recommendationProfile = profile.copy(
                preferredRecommendationArea = preferred.ifBlank { profile.preferredRecommendationArea },
                profileLocation = profileLocation.ifBlank { profile.profileLocation }
            )
        )
    }

    private fun discoveryItem(
        id: String,
        location: String,
        style: String = "Hip Hop",
        latitude: Double? = null,
        longitude: Double? = null
    ): DiscoveryItem {
        return DiscoveryItem(
            id = id,
            title = id,
            studio = "Studio $id",
            teacher = "Teacher",
            style = style,
            level = "All levels",
            location = location,
            time = "Today",
            type = "Studio",
            latitude = latitude,
            longitude = longitude
        )
    }

    private companion object {
        val telAviv = GeoPoint(32.0853, 34.7818)
        val haifa = GeoPoint(32.7940, 34.9896)
    }
}
