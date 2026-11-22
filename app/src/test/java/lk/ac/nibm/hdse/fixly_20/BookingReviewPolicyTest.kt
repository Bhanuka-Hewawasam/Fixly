package lk.ac.nibm.hdse.fixly_20

import lk.ac.nibm.hdse.fixly_20.model.JobStatusPolicy
import lk.ac.nibm.hdse.fixly_20.model.BookingPolicy
import lk.ac.nibm.hdse.fixly_20.model.ServiceRating
import lk.ac.nibm.hdse.fixly_20.model.ServiceRatingPolicy
import lk.ac.nibm.hdse.fixly_20.model.ServiceReviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingReviewPolicyTest {
    @Test
    fun repeatBookingLockIsScopedToCustomerProviderAndService() {
        val firstBooking = ServiceRatingPolicy.bookingLockId("customer-1", "provider-1", "Cleaning")

        assertEquals(
            firstBooking,
            ServiceRatingPolicy.bookingLockId("customer-1", "provider-1", "Cleaning")
        )
        assertNotEquals(
            firstBooking,
            ServiceRatingPolicy.bookingLockId("customer-1", "provider-1", "Electrical")
        )
        assertNotEquals(
            firstBooking,
            ServiceRatingPolicy.bookingLockId("customer-2", "provider-1", "Cleaning")
        )
    }

    @Test
    fun activeBookingBlocksDuplicateButFinalConfirmationReleasesLock() {
        assertFalse(BookingPolicy.canAssign("Open", activeLockExists = true))
        assertFalse(BookingPolicy.shouldReleaseLock("Completed", customerConfirmed = false))
        assertTrue(BookingPolicy.shouldReleaseLock("Completed", customerConfirmed = true))
        assertTrue(BookingPolicy.canAssign("Open", activeLockExists = false))
    }

    @Test
    fun cleaningAndElectricalReviewsAreAggregatedIndependently() {
        val initial = mapOf("Electrical" to ServiceRating(4.0, 2))

        val updated = ServiceRatingPolicy.nextByCategory(initial, "Cleaning", 5)

        assertEquals(ServiceRating(4.0, 2), updated["Electrical"])
        assertEquals(ServiceRating(5.0, 1), updated["Cleaning"])
    }

    @Test
    fun categoryAverageIncludesOnlyReviewsForThatService() {
        val ratings = ServiceRatingPolicy.nextByCategory(
            mapOf("Cleaning" to ServiceRating(4.0, 1)),
            "Cleaning",
            5
        )

        assertEquals(4.5, ratings.getValue("Cleaning").averageRating, 0.0)
        assertEquals(2, ratings.getValue("Cleaning").reviewCount)
        assertFalse("Electrical" in ratings)
    }

    @Test
    fun reviewRequiresOwningCustomerCompletedJobAndUniqueValidRating() {
        val validReview = reviewAllowed(
            reviewAlreadyExists = false,
            rating = 5
        )
        assertTrue(validReview)

        assertFalse(reviewAllowed(customerId = "another-customer"))
        assertFalse(reviewAllowed(reviewAlreadyExists = true))
        assertFalse(reviewAllowed(rating = 6))
        assertFalse(reviewAllowed(jobStatus = "In Progress"))
        assertFalse(reviewAllowed(customerConfirmed = true))
    }

    @Test
    fun providerCannotReviewTheirOwnServiceOrExceedCommentLimit() {
        assertFalse(reviewAllowed(customerId = "provider-1"))
        assertFalse(reviewAllowed(commentLength = 1001))
    }

    @Test
    fun providerCanOnlyMoveAnAcceptedJobThroughAllowedStatuses() {
        assertTrue(JobStatusPolicy.canProviderTransition("provider-1", "provider-1", "Accepted", "In Progress", false))
        assertTrue(JobStatusPolicy.canProviderTransition("provider-1", "provider-1", "Assigned", "In Progress", false))
        assertTrue(JobStatusPolicy.canProviderTransition("provider-1", "provider-1", "In Progress", "Completed", true))
        assertFalse(JobStatusPolicy.canProviderTransition("provider-2", "provider-1", "In Progress", "Completed", true))
        assertFalse(JobStatusPolicy.canProviderTransition("provider-1", null, "Open", "Completed", true))
        assertFalse(JobStatusPolicy.canProviderTransition("provider-1", "provider-1", "Completed", "In Progress", true))
        assertFalse(JobStatusPolicy.canProviderTransition("provider-1", "provider-1", "In Progress", "Completed", false))
    }

    private fun reviewAllowed(
        customerId: String = "customer-1",
        reviewAlreadyExists: Boolean = false,
        rating: Int = 5,
        jobStatus: String = "Completed",
        customerConfirmed: Boolean = false,
        commentLength: Int = 0
    ): Boolean = ServiceReviewPolicy.canCreate(
        customerId = customerId,
        bookingCustomerId = "customer-1",
        providerId = "provider-1",
        bookingProviderId = "provider-1",
        jobStatus = jobStatus,
        customerConfirmed = customerConfirmed,
        reviewAlreadyExists = reviewAlreadyExists,
        rating = rating,
        commentLength = commentLength
    )
}
