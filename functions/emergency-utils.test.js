const test = require("node:test");
const assert = require("node:assert/strict");
const {
    MAX_PROVIDER_LOCATION_AGE_MS,
    matchesEmergencyRequest,
    notificationStatusUpdate,
    shouldExpireAlert,
    emergencyProviderProfile
} = require("./emergency-utils");

const nowMs = 1_800_000_000_000;
const customerLocation = { latitude: 6.9271, longitude: 79.8612 };

function provider(overrides = {}) {
    return {
        roles: ["provider"],
        emergencyAvailable: true,
        serviceCategories: ["Electrical"],
        lastLocation: { latitude: 6.93, longitude: 79.86 },
        lastLocationAt: { toMillis: () => nowMs - 1_000 },
        ...overrides
    };
}

test("provider matching rejects self, missing location, stale location, and unsupported category", () => {
    const request = {
        customerId: "customer",
        customerLocation,
        category: "Electrician",
        nowMs
    };
    assert.equal(matchesEmergencyRequest({ ...request, providerId: "customer", provider: provider() }), false);
    assert.equal(matchesEmergencyRequest({
        ...request,
        providerId: "provider",
        provider: provider({ lastLocationAt: { toMillis: () => nowMs - MAX_PROVIDER_LOCATION_AGE_MS - 1 } })
    }), false);
    assert.equal(matchesEmergencyRequest({
        ...request,
        providerId: "provider",
        provider: provider({ serviceCategories: ["Plumbing"] })
    }), false);
    assert.equal(matchesEmergencyRequest({
        ...request,
        providerId: "provider",
        provider: provider({ lastLocation: null })
    }), false);
});

test("new emergency categories match existing service category names within configured radius", () => {
    assert.equal(matchesEmergencyRequest({
        providerId: "provider",
        provider: provider(),
        customerId: "customer",
        customerLocation,
        category: "Electrician",
        nowMs
    }), true);
    assert.equal(matchesEmergencyRequest({
        providerId: "provider",
        provider: provider({ lastLocation: { latitude: 7.7, longitude: 80.0 } }),
        customerId: "customer",
        customerLocation,
        category: "Any",
        nowMs
    }), false);
});

test("acceptance closes other provider notifications and terminal statuses propagate", () => {
    assert.deepEqual(
        notificationStatusUpdate({ providerId: "winner" }, "ACCEPTED", "winner"),
        { isRead: true, status: "ACCEPTED" }
    );
    assert.deepEqual(
        notificationStatusUpdate({ providerId: "other" }, "ACCEPTED", "winner"),
        { status: "CLOSED" }
    );
    assert.deepEqual(
        notificationStatusUpdate({ providerId: "other" }, "CANCELLED", null),
        { status: "CANCELLED" }
    );
    assert.equal(notificationStatusUpdate({ providerId: "p" }, "ACTIVE", null), null);
});

test("only live requests past their deadline expire", () => {
    assert.equal(shouldExpireAlert("ACTIVE", nowMs - 1, nowMs), true);
    assert.equal(shouldExpireAlert("ACCEPTED", nowMs - 1, nowMs), true);
    assert.equal(shouldExpireAlert("ACTIVE", nowMs + 1, nowMs), false);
    assert.equal(shouldExpireAlert("CLOSED", nowMs - 1, nowMs), false);
});

test("emergency search projection contains only allowlisted fields and omits private data", () => {
    const profile = emergencyProviderProfile("provider", {
        roles: ["provider"],
        emergencyAvailable: true,
        name: "Provider",
        email: "private@example.com",
        phoneNumber: "+10000000000",
        fcmToken: "private-token",
        passwordHash: "private-hash",
        profilePictureUrl: "https://example.com/profile.jpg",
        serviceCategories: ["Electrical"],
        ratingSum: 18,
        ratingCount: 4,
        isVerified: true,
        lastLocation: { latitude: 6.93, longitude: 79.86 },
        lastLocationAt: { toMillis: () => nowMs - 1_000 }
    }, nowMs);

    assert.deepEqual(Object.keys(profile).sort(), [
        "displayName", "emergencyAvailable", "isVerified",
        "lastLocationAt", "latitude", "longitude", "profilePhotoUrl", "providerId", "rating", "ratingCount",
        "serviceCategories"
    ].sort());
    assert.equal(profile.rating, 4.5);
    assert.equal(profile.latitude, 6.93);
    assert.equal(profile.longitude, 79.86);
    assert.equal(profile.lastLocation, undefined);
    assert.equal(profile.phoneNumber, undefined);
    assert.equal(profile.fcmToken, undefined);
    assert.equal(profile.passwordHash, undefined);
    assert.equal(emergencyProviderProfile("provider", provider({
        lastLocationAt: { toMillis: () => nowMs - MAX_PROVIDER_LOCATION_AGE_MS - 1 }
    }), nowMs), null);
});
