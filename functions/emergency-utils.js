const MAX_PROVIDER_DISTANCE_KM = 50;

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
    selectedProviderId
}) {
    if (
        providerId === customerId ||
        !Array.isArray(provider.roles) ||
        !provider.roles.includes("provider") ||
        provider.availableForEmergency !== true ||
        provider.suspended === true
    ) return false;
    if (selectedProviderId && selectedProviderId !== providerId) return false;

    const latitude = provider.serviceLatitude;
    const longitude = provider.serviceLongitude;
    const radiusKm = provider.serviceRadiusKm;
    if (
        !Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
        !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
        !Number.isFinite(radiusKm) || radiusKm <= 0
    ) return false;

    const aliases = {
        "Vehicle Mechanic": ["Vehicle Mechanic", "Mechanic"],
        Electrician: ["Electrician", "Electrical"],
        Electrical: ["Electrician", "Electrical"],
        Plumber: ["Plumber", "Plumbing"],
        Plumbing: ["Plumber", "Plumbing"],
        "AC Technician": ["AC Technician", "AC Repair"],
        "AC Repair": ["AC Technician", "AC Repair"],
        "Computer Technician": ["Computer Technician", "Computer Repair"],
        "Computer Repair": ["Computer Technician", "Computer Repair"],
        Other: ["Other"]
    };
    const categories = Array.isArray(provider.serviceCategories) ? provider.serviceCategories : [];
    const matchingCategories = aliases[category] || [category];
    if (category !== "Any" && category !== "Any Available Provider" &&
        !matchingCategories.some((item) => categories.includes(item))) return false;

    return calculateDistanceKm(
        customerLocation.latitude,
        customerLocation.longitude,
        latitude,
        longitude
    ) <= Math.min(radiusKm, MAX_PROVIDER_DISTANCE_KM);
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

module.exports = {
    MAX_PROVIDER_DISTANCE_KM,
    calculateDistanceKm,
    matchesEmergencyRequest,
    notificationStatusUpdate,
    shouldExpireAlert
};
