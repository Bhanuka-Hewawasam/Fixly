package lk.ac.nibm.hdse.fixly_20.data.local

import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.model.User
import java.util.Date

// ---------- JobPost <-> BookingEntity ----------

fun JobPost.toBookingEntity() = BookingEntity(
    jobId = jobId,
    customerId = customerId,
    customerName = customerName,
    category = category,
    description = description,
    preferredDate = preferredDate,
    preferredTime = preferredTime,
    isEmergency = isEmergency,
    address = address,
    status = status,
    selectedBidId = selectedBidId,
    assignedProviderId = assignedProviderId,
    assignedProviderName = assignedProviderName,
    providerPhone = providerPhone,
    providerCertificateUrl = providerCertificateUrl,
    acceptedBidPrice = acceptedBidPrice,
    acceptedProviderRating = acceptedProviderRating,
    acceptedProviderReviewCount = acceptedProviderReviewCount,
    beforePhotoUrl = beforePhotoUrl,
    afterPhotoUrl = afterPhotoUrl,
    customerConfirmed = customerConfirmed,
    rating = rating,
    reviewComment = reviewComment,
    latitude = location?.latitude,
    longitude = location?.longitude,
    createdAtMillis = createdAt?.time
)

fun BookingEntity.toJobPost() = JobPost(
    jobId = jobId,
    customerId = customerId,
    customerName = customerName,
    category = category,
    description = description,
    preferredDate = preferredDate,
    preferredTime = preferredTime,
    isEmergency = isEmergency,
    location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
    address = address,
    status = status,
    selectedBidId = selectedBidId,
    assignedProviderId = assignedProviderId,
    assignedProviderName = assignedProviderName,
    providerPhone = providerPhone,
    providerCertificateUrl = providerCertificateUrl,
    acceptedBidPrice = acceptedBidPrice,
    acceptedProviderRating = acceptedProviderRating,
    acceptedProviderReviewCount = acceptedProviderReviewCount,
    beforePhotoUrl = beforePhotoUrl,
    afterPhotoUrl = afterPhotoUrl,
    customerConfirmed = customerConfirmed,
    rating = rating,
    reviewComment = reviewComment,
    createdAt = createdAtMillis?.let { Date(it) }
)

// ---------- User <-> UserProfileEntity ----------

fun User.toProfileEntity() = UserProfileEntity(
    uid = uid,
    name = name,
    email = email,
    phoneNumber = phoneNumber,
    roles = roles,
    serviceCategories = serviceCategories,
    certificateUrl = certificateUrl,
    isVerified = isVerified,
    ratingSum = ratingSum,
    ratingCount = ratingCount,
    emergencyAvailable = false,
    lastLatitude = null,
    lastLongitude = null,
    lastLocationAtMillis = null
)

fun UserProfileEntity.toUser() = User(
    uid = uid,
    name = name,
    email = email,
    phoneNumber = phoneNumber,
    roles = roles,
    serviceCategories = serviceCategories,
    certificateUrl = certificateUrl,
    isVerified = isVerified,
    ratingSum = ratingSum,
    ratingCount = ratingCount,
    availableForEmergency = false
)
