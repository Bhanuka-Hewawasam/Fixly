const test = require("node:test");
const assert = require("node:assert/strict");
const {
    matchesEmergencyRequest,
    notificationStatusUpdate,
    shouldExpireAlert
} = require("./emergency-utils");

const customerId = "customer";
const customerLocation = { latitude: 6.9271, longitude: 79.8612 };

function provider(overrides = {}) {
    return {
        roles: ["provider"],
        availableForEmergency: true,
        suspended: false,
        serviceCategories: ["Electrical"],
        serviceLatitude: 6.93,
        serviceLongitude: 79.86,
        serviceRadiusKm: 20,
        ...overrides
    };
}

test("registered service area matching excludes self, unavailable, suspended, and wrong category", () => {
    const request = {
        customerId,
        customerLocation,
        category: "Electrician"
    };
    assert.equal(matchesEmergencyRequest({
        ...request,
        providerId: customerId,
        provider: provider()
    }), false);
    assert.equal(matchesEmergencyRequest({
        ...request,
        providerId: "provider",
        provider: provider({ availableForEmergency: false })
    }), false);
    assert.equal(matchesEmergencyRequest({
        ...request,
        providerId: "provider",
        provider: provider({ suspended: true })
    }), false);
    assert.equal(matchesEmergencyRequest({
        ...request,
        providerId: "provider",
        provider: provider({ serviceCategories: ["Plumbing"] })
    }), false);
});

test("service location and provider radius determine eligibility without freshness timestamps", () => {
    assert.equal(matchesEmergencyRequest({
        providerId: "provider",
        provider: provider(),
        customerId,
        customerLocation,
        category: "Electrician"
    }), true);
    assert.equal(matchesEmergencyRequest({
        providerId: "provider",
        provider: provider({ serviceLatitude: 7.7, serviceLongitude: 80.0 }),
        customerId,
        customerLocation,
        category: "Any"
    }), false);
    assert.equal(matchesEmergencyRequest({
        providerId: "provider",
        provider: provider({ serviceRadiusKm: 0 }),
        customerId,
        customerLocation,
        category: "Any"
    }), false);
});

test("direct requests only notify their selected provider", () => {
    assert.equal(matchesEmergencyRequest({
        providerId: "selected",
        provider: provider(),
        customerId,
        customerLocation,
        category: "Electrician",
        selectedProviderId: "selected"
    }), true);
    assert.equal(matchesEmergencyRequest({
        providerId: "another",
        provider: provider(),
        customerId,
        customerLocation,
        category: "Electrician",
        selectedProviderId: "selected"
    }), false);
});

test("enabled registered service locations remain eligible without a login-session field", () => {
    const savedArea = provider();
    delete savedArea.normalAppSessionActive;
    assert.equal(matchesEmergencyRequest({
        providerId: "provider",
        provider: savedArea,
        customerId,
        customerLocation,
        category: "Electrician"
    }), true);
    assert.equal(matchesEmergencyRequest({
        providerId: "provider",
        provider: provider({ availableForEmergency: false }),
        customerId,
        customerLocation,
        category: "Electrician"
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
    const now = 1_800_000_000_000;
    assert.equal(shouldExpireAlert("ACTIVE", now - 1, now), true);
    assert.equal(shouldExpireAlert("ACCEPTED", now - 1, now), true);
    assert.equal(shouldExpireAlert("ACTIVE", now + 1, now), false);
    assert.equal(shouldExpireAlert("CLOSED", now - 1, now), false);
});
