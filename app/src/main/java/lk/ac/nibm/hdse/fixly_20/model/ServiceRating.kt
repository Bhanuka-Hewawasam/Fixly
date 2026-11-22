package lk.ac.nibm.hdse.fixly_20.model

import lk.ac.nibm.hdse.fixly_20.util.ServiceCategories

data class ServiceRating(
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0
)

data class ProviderRatingAggregate(
    val providerId: String = "",
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    val rating: Double = 0.0,
    val serviceRatings: Map<String, ServiceRating> = emptyMap(),
    val availableForEmergency: Boolean = false,
    val lastRatingJobId: String? = null
)

object ServiceRatingPolicy {
    fun next(current: ServiceRating, rating: Int): ServiceRating {
        require(rating in 1..5)
        val count = current.reviewCount.coerceAtLeast(0)
        return ServiceRating(
            averageRating = (current.averageRating * count + rating) / (count + 1),
            reviewCount = count + 1
        )
    }

    fun bookingLockId(customerId: String, providerId: String, category: String): String {
        require(customerId.isNotBlank() && providerId.isNotBlank())
        require(category.isNotBlank() && '/' !in category)
        val normalizedCategory = ServiceCategories.normalize(category)
        return "${customerId}__${providerId}__${normalizedCategory}"
    }

    fun nextByCategory(
        current: Map<String, ServiceRating>,
        category: String,
        rating: Int
    ): Map<String, ServiceRating> {
        val normalized = ServiceCategories.normalize(category)
        return current + (normalized to next(current[normalized] ?: ServiceRating(), rating))
    }
}
