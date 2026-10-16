const MAX_PROVIDER_DISTANCE_KM = 50;
const MAX_PROVIDER_LOCATION_AGE_MS = 15 * 60 * 1000;

function calculateDistanceKm(lat1, lon1, lat2, lon2) {
    const earthRadiusKm = 6371;
    const dLat = (lat2 - lat1) * (Math.PI / 180);
    const dLon = (lon2 - lon1) * (Math.PI / 180);
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * (Math.PI / 180)) * Math.cos(lat2 * (Math.PI / 180)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function matchesEmergencyRequest({
    providerId,
    provider,
    customerId,
    customerLocation,
    category,
    selectedProviderId,
    nowMs
}) {
    if (
        providerId === customerId ||
        !Array.isArray(provider.roles) ||
        !provider.roles.includes("provider") ||
        provider.emergencyAvailable !== true
    ) {
        return false;
    }
    if (selectedProviderId && selectedProviderId !== providerId) return false;

    const location = provider.lastLocation;
    const locationAt = provider.lastLocationAt;
    if (
        !location ||
        !Number.isFinite(location.latitude) ||
        !Number.isFinite(location.longitude) ||
        location.latitude < -90 ||
        location.latitude > 90 ||
        location.longitude < -180 ||
        location.longitude > 180 ||
        !locationAt ||
        typeof locationAt.toMillis !== "function"
    ) return false;

    const ageMs = nowMs - locationAt.toMillis();
    if (ageMs < 0 || ageMs > MAX_PROVIDER_LOCATION_AGE_MS) return false;

    const categoryAliases = {
        "Vehicle Mechanic": ["Vehicle Mechanic", "Mechanic"],
        Electrician: ["Electrician", "Electrical"],
        Plumber: ["Plumber", "Plumbing"],
        "AC Technician": ["AC Technician", "AC Repair"],
        "Computer Technician": ["Computer Technician", "Computer Repair"],
        Other: ["Other"]
    };
    const categories = Array.isArray(provider.serviceCategories) ? provider.serviceCategories : [];
    const matchingCategories = categoryAliases[category] || [category];
    if (category !== "Any" && !matchingCategories.some((item) => categories.includes(item))) return false;

    return calculateDistanceKm(
        customerLocation.latitude,
        customerLocation.longitude,
        location.latitude,
        location.longitude
    ) <= MAX_PROVIDER_DISTANCE_KM;
}

function notificationStatusUpdate(notification, newStatus, acceptedProviderId) {
    if (newStatus === "ACCEPTED") {
        return notification.providerId === acceptedProviderId
            ? { isRead: true, status: "ACCEPTED" }
            : { status: "CLOSED" };
    }
    if (["CANCELLED", "EXPIRED", "CLOSED"].includes(newStatus)) {
        return { status: newStatus };
    }
    return null;
}

function shouldExpireAlert(status, expiresAtMs, nowMs) {
    return ["ACTIVE", "ACCEPTED"].includes(status) && expiresAtMs <= nowMs;
}

function emergencyProviderProfile(providerId, user, nowMs) {
    const location = user.lastLocation;
    const locationAt = user.lastLocationAt;
    if (
        !providerId ||
        !Array.isArray(user.roles) ||
        !user.roles.includes("provider") ||
        user.emergencyAvailable !== true ||
        !location ||
        !Number.isFinite(location.latitude) ||
        !Number.isFinite(location.longitude) ||
        location.latitude < -90 ||
        location.latitude > 90 ||
        location.longitude < -180 ||
        location.longitude > 180 ||
        !locationAt ||
        typeof locationAt.toMillis !== "function"
    ) return null;

    const locationAgeMs = nowMs - locationAt.toMillis();
    if (locationAgeMs < 0 || locationAgeMs > MAX_PROVIDER_LOCATION_AGE_MS) return null;

    const ratingCount = Number.isInteger(user.ratingCount) && user.ratingCount > 0
        ? user.ratingCount
        : 0;
    const ratingSum = Number.isFinite(user.ratingSum) && user.ratingSum >= 0
        ? user.ratingSum
        : 0;

    return {
        providerId,
        displayName: typeof user.name === "string" ? user.name : "",
        profilePhotoUrl: typeof user.profilePictureUrl === "string" ? user.profilePictureUrl : null,
        serviceCategories: Array.isArray(user.serviceCategories) ? user.serviceCategories : [],
        rating: ratingCount > 0 ? ratingSum / ratingCount : 0,
        ratingCount,
        isVerified: user.isVerified === true,
        emergencyAvailable: true,
        latitude: location.latitude,
        longitude: location.longitude,
        lastLocationAt: locationAt
    };
}

module.exports = {
    MAX_PROVIDER_DISTANCE_KM,
    MAX_PROVIDER_LOCATION_AGE_MS,
    calculateDistanceKm,
    matchesEmergencyRequest,
    notificationStatusUpdate,
    shouldExpireAlert,
    emergencyProviderProfile
};
