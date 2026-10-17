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

class EmergencyProviderMatcherTest {
    private val customer = GeoPoint(6.9271, 79.8612)

    @Test
    fun filtersSelfWrongCategoryUnavailableSuspendedAndOutOfServiceAreaProviders() {
        val providers = listOf(
            provider("self", near(0.01), categories = listOf("Plumber")),
            provider("wrong-category", near(0.01), categories = listOf("Electrician")),
            provider("unavailable", near(0.01), available = false),
            provider("suspended", near(0.01), suspended = true),
            provider("outside-service-radius", near(0.05), serviceRadiusKm = 2),
            provider("too-far", near(1.0)),
            provider("eligible", near(0.01), categories = listOf("Plumber"))
        )

        val matches = EmergencyProviderMatcher.findMatches(
            providers = providers,
            customerLocation = customer,
            category = "Plumber",
            radiusKm = 10,
            currentUid = "self",
            currentEmail = null
        )

        assertEquals(listOf("eligible"), matches.map { it.provider.uid })
    }

    @Test
    fun mapsSearchCategoryNamesToProviderCategoryAliases() {
        val electrician = provider("electrician", near(0.01), categories = listOf("Electrical"))
        val other = provider("other", near(0.02), categories = listOf("Other"))

        val electricianMatches = EmergencyProviderMatcher.findMatches(
            listOf(electrician, other), customer, "Electrician", 25, null, null
        )
        val anyMatches = EmergencyProviderMatcher.findMatches(
            listOf(electrician, other), customer, "Any Available Provider", 25, null, null
        )

        assertEquals(listOf("electrician"), electricianMatches.map { it.provider.uid })
        assertEquals(2, anyMatches.size)
    }

    @Test
    fun registeredProviderRemainsSearchableWithoutAnActiveProviderAppSession() {
        val registeredProvider = provider("logged-out-provider", near(0.01))

        val visible = EmergencyProviderMatcher.findMatches(
            listOf(registeredProvider),
            customer,
            "Plumber",
            10,
            currentUid = null,
            currentEmail = null
        )
        val hiddenAfterTurningAvailabilityOff = EmergencyProviderMatcher.findMatches(
            listOf(registeredProvider.copy(availableForEmergency = false)),
            customer,
            "Plumber",
            10,
            currentUid = null,
            currentEmail = null
        )

        assertEquals(listOf("logged-out-provider"), visible.map { it.provider.uid })
        assertTrue(hiddenAfterTurningAvailabilityOff.isEmpty())
    }

    @Test
    fun sortsByDistanceThenRatingThenVerification() {
        val lowerRated = provider("lower", near(0.02), ratingSum = 3.0, ratingCount = 1)
        val higherRated = provider("higher", near(0.02), ratingSum = 5.0, ratingCount = 1)
        val verified = provider("verified", near(0.02), ratingSum = 5.0, ratingCount = 1, verified = true)
        val farther = provider("farther", near(0.04), ratingSum = 5.0, ratingCount = 1)

        val matches = EmergencyProviderMatcher.findMatches(
            listOf(farther, lowerRated, higherRated, verified), customer, "Any", 25, null, null
        )

        assertEquals(listOf("verified", "higher", "lower", "farther"), matches.map { it.provider.uid })
    }

    @Test
    fun rejectsInvalidCoordinatesAndNonProviderProfiles() {
        val invalidCoordinates = provider("invalid", near(0.01)).copy(serviceLatitude = 91.0)
        val customerRole = provider("customer", near(0.02)).copy(roles = listOf("customer"))
        val invalidRadius = provider("radius", near(0.03)).copy(serviceRadiusKm = 0)

        val matches = EmergencyProviderMatcher.findMatches(
            listOf(invalidCoordinates, customerRole, invalidRadius),
            customer,
            "Any",
            25,
            null,
            null
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun onlyOneProviderCanAcceptAndOnlyActiveRequestsCanBeCancelled() {
        val now = 1_800_000_000_000L
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
    fun onlyActiveOrAcceptedRequestsAreKeptOpenOnEmergencyScreens() {
        assertTrue(EmergencyAlertPolicy.isLiveForCustomer("ACTIVE"))
        assertTrue(EmergencyAlertPolicy.isLiveForCustomer("ACCEPTED"))
        assertFalse(EmergencyAlertPolicy.isLiveForCustomer("CLOSED"))
        assertFalse(EmergencyAlertPolicy.isLiveForCustomer("CANCELLED"))
        assertFalse(EmergencyAlertPolicy.isLiveForCustomer("EXPIRED"))
    }

    @Test
    fun expiryAppliesOnlyToLiveRequestsWhoseDeadlinePassed() {
        val now = 1_800_000_000_000L
        assertTrue(EmergencyAlertPolicy.shouldExpire("ACTIVE", now - 1, now))
        assertTrue(EmergencyAlertPolicy.shouldExpire("ACCEPTED", now - 1, now))
        assertFalse(EmergencyAlertPolicy.shouldExpire("ACTIVE", now + 1, now))
        assertFalse(EmergencyAlertPolicy.shouldExpire("CLOSED", now - 1, now))
    }

    @Test
    fun customerLocationIsReadOnlyWithPermissionAndLocationServices() {
        assertFalse(EmergencyLocationPolicy.canReadCurrentLocation(false, true))
        assertFalse(EmergencyLocationPolicy.canReadCurrentLocation(true, false))
        assertTrue(EmergencyLocationPolicy.canReadCurrentLocation(true, true))
    }

    @Test
    fun rejectsDuplicateUnexpiredActiveOrAcceptedAlerts() {
        val now = 1_800_000_000_000L
        assertTrue(EmergencyAlertPolicy.hasUnexpiredLiveAlert("ACTIVE", now + 1, now))
        assertTrue(EmergencyAlertPolicy.hasUnexpiredLiveAlert("ACCEPTED", now + 1, now))
        assertFalse(EmergencyAlertPolicy.hasUnexpiredLiveAlert("CANCELLED", now + 1, now))
        assertFalse(EmergencyAlertPolicy.hasUnexpiredLiveAlert("ACTIVE", now - 1, now))
    }

    private fun near(longitudeOffset: Double) =
        GeoPoint(customer.latitude, customer.longitude + longitudeOffset)

    private fun provider(
        uid: String,
        location: GeoPoint,
        categories: List<String> = listOf("Plumber"),
        ratingSum: Double = 0.0,
        ratingCount: Int = 0,
        serviceRadiusKm: Int = 50,
        available: Boolean = true,
        verified: Boolean = false,
        suspended: Boolean = false
    ) = User(
        uid = uid,
        name = uid,
        email = "$uid@example.com",
        roles = listOf("provider"),
        serviceCategories = categories,
        ratingSum = ratingSum,
        ratingCount = ratingCount,
        isVerified = verified,
        availableForEmergency = available,
        serviceLatitude = location.latitude,
        serviceLongitude = location.longitude,
        serviceRadiusKm = serviceRadiusKm,
        suspended = suspended
    )
}
