package lk.ac.nibm.hdse.fixly_20

import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlertPolicy
import lk.ac.nibm.hdse.fixly_20.model.EmergencyLocationPolicy
import lk.ac.nibm.hdse.fixly_20.model.User
import lk.ac.nibm.hdse.fixly_20.util.EmergencyProviderMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class EmergencyProviderMatcherTest {
    private val now = 1_800_000_000_000L
    private val customer = GeoPoint(6.9271, 79.8612)

    @Test
    fun filtersSelfWrongCategoryStaleAndOutOfRadiusProviders() {
        val providers = listOf(
            provider("self", now, customer, categories = listOf("Plumber")),
            provider("wrong-category", now, near(0.01), categories = listOf("Electrician")),
            provider("stale", now - EmergencyProviderMatcher.MAX_LOCATION_AGE_MS - 1, near(0.01)),
            provider("too-far", now, near(1.0)),
            provider("eligible", now, near(0.01), categories = listOf("Plumber"))
        )

        val matches = EmergencyProviderMatcher.findMatches(
            providers = providers,
            customerLocation = customer,
            category = "Plumber",
            radiusKm = 10,
            currentUid = "self",
            currentEmail = null,
            nowMs = now
        )

        assertEquals(listOf("eligible"), matches.map { it.provider.uid })
    }

    @Test
    fun mapsNewEmergencyCategoryNamesToExistingProviderCategories() {
        val electrician = provider("electrician", now, near(0.01), categories = listOf("Electrical"))
        val anyProvider = provider("other", now, near(0.02), categories = listOf("Other"))

        val electricianMatches = EmergencyProviderMatcher.findMatches(
            listOf(electrician, anyProvider), customer, "Electrician", 25, null, null, now
        )
        val anyMatches = EmergencyProviderMatcher.findMatches(
            listOf(electrician, anyProvider), customer, "Any Available Provider", 25, null, null, now
        )

        assertEquals(listOf("electrician"), electricianMatches.map { it.provider.uid })
        assertEquals(2, anyMatches.size)
    }

    @Test
    fun sortsByDistanceThenProviderRating() {
        val lowerRated = provider("lower", now, near(0.02), ratingSum = 3.0, ratingCount = 1)
        val higherRated = provider("higher", now, near(0.02), ratingSum = 5.0, ratingCount = 1)
        val farther = provider("farther", now, near(0.04), ratingSum = 5.0, ratingCount = 1)

        val matches = EmergencyProviderMatcher.findMatches(
            listOf(farther, lowerRated, higherRated), customer, "Any", 25, null, null, now
        )

        assertEquals(listOf("higher", "lower", "farther"), matches.map { it.provider.uid })
    }

    @Test
    fun rejectsFutureLocationTimestampsAndUnauthenticatedProfiles() {
        val future = provider("future", now + 1, near(0.01))
        val customerRole = provider("customer", now, near(0.02)).copy(roles = listOf("customer"))

        val matches = EmergencyProviderMatcher.findMatches(
            listOf(future, customerRole), customer, "Any", 25, null, null, now
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun onlyOneProviderCanAcceptAndOnlyActiveRequestsCanBeCancelled() {
        assertTrue(EmergencyAlertPolicy.canProviderAccept("ACTIVE", "ACTIVE", null, "p1", now + 1, now))
        assertFalse(EmergencyAlertPolicy.canProviderAccept("ACCEPTED", "ACTIVE", null, "p2", now + 1, now))
        assertFalse(EmergencyAlertPolicy.canProviderAccept("ACTIVE", "CLOSED", null, "p2", now + 1, now))
        assertFalse(EmergencyAlertPolicy.canProviderAccept("ACTIVE", "ACTIVE", "p1", "p2", now + 1, now))
        assertFalse(EmergencyAlertPolicy.canProviderAccept("ACTIVE", "ACTIVE", null, "p1", now - 1, now))
        assertTrue(EmergencyAlertPolicy.canCustomerCancel("ACTIVE", now + 1, now))
        assertFalse(EmergencyAlertPolicy.canCustomerCancel("ACTIVE", now - 1, now))
        assertFalse(EmergencyAlertPolicy.canCustomerCancel("ACCEPTED", now + 1, now))
        assertFalse(EmergencyAlertPolicy.canCustomerCancel("CLOSED", now + 1, now))
    }

    @Test
    fun expiryAppliesOnlyToLiveRequestsWhoseDeadlinePassed() {
        assertTrue(EmergencyAlertPolicy.shouldExpire("ACTIVE", now - 1, now))
        assertTrue(EmergencyAlertPolicy.shouldExpire("ACCEPTED", now - 1, now))
        assertFalse(EmergencyAlertPolicy.shouldExpire("ACTIVE", now + 1, now))
        assertFalse(EmergencyAlertPolicy.shouldExpire("CLOSED", now - 1, now))
    }

    @Test
    fun refusesLocationWhenPermissionIsDeniedOrLocationServicesAreDisabled() {
        assertFalse(EmergencyLocationPolicy.canReadCurrentLocation(false, true))
        assertFalse(EmergencyLocationPolicy.canReadCurrentLocation(true, false))
        assertTrue(EmergencyLocationPolicy.canReadCurrentLocation(true, true))
    }

    @Test
    fun rejectsDuplicateUnexpiredActiveOrAcceptedAlerts() {
        assertTrue(EmergencyAlertPolicy.hasUnexpiredLiveAlert("ACTIVE", now + 1, now))
        assertTrue(EmergencyAlertPolicy.hasUnexpiredLiveAlert("ACCEPTED", now + 1, now))
        assertFalse(EmergencyAlertPolicy.hasUnexpiredLiveAlert("CANCELLED", now + 1, now))
        assertFalse(EmergencyAlertPolicy.hasUnexpiredLiveAlert("ACTIVE", now - 1, now))
    }

    private fun near(longitudeOffset: Double) =
        GeoPoint(customer.latitude, customer.longitude + longitudeOffset)

    private fun provider(
        uid: String,
        timestamp: Long,
        location: GeoPoint,
        categories: List<String> = listOf("Plumber"),
        ratingSum: Double = 0.0,
        ratingCount: Int = 0
    ) = User(
        uid = uid,
        name = uid,
        email = "$uid@example.com",
        roles = listOf("provider"),
        serviceCategories = categories,
        ratingSum = ratingSum,
        ratingCount = ratingCount,
        emergencyAvailable = true,
        lastLocation = location,
        lastLocationAt = Date(timestamp)
    )
}
