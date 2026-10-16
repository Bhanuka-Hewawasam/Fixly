const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

// Haversine formula to calculate distance in kilometers between two GPS coordinates
function calculateDistanceKm(lat1, lon1, lat2, lon2) {
    const R = 6371; // Earth radius in km
    const dLat = (lat2 - lat1) * (Math.PI / 180);
    const dLon = (lon2 - lon1) * (Math.PI / 180);
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * (Math.PI / 180)) * Math.cos(lat2 * (Math.PI / 180)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

exports.onEmergencyAlertCreated = onDocumentCreated(
    {
        document: "emergencyAlerts/{alertId}",
        region: "us-central1"
    },
    async (event) => {
        const snap = event.data;
        if (!snap) return;

        const alertId = event.params.alertId;
        const alertData = snap.data();
        const customerId = alertData.customerId;
        const customerLocation = alertData.location; // GeoPoint { latitude, longitude }
        const category = alertData.category || "Any";
        const note = alertData.note || "";
        const status = alertData.status || "ACTIVE";

        if (status !== "ACTIVE" || !customerLocation) {
            console.log(`Alert ${alertId} is not ACTIVE or missing location.`);
            return;
        }

        console.log(`Processing ACTIVE emergency alert ${alertId} from customer ${customerId} (${category})`);

        // Query all providers who are available for emergencies
        const providersSnapshot = await db.collection("users")
            .where("emergencyAvailable", "==", true)
            .get();

        let matchedProviderCount = 0;
        let tokenCount = 0;
        let notificationCount = 0;
        let successCount = 0;
        let failureCount = 0;

        const now = Date.now();
        const MAX_AGE_MS = 3 * 60 * 60 * 1000; // 3 hours freshness limit
        const messaging = getMessaging();
        const batch = db.batch();

        const targets = [];

        providersSnapshot.forEach((doc) => {
            const provider = doc.data();
            const providerId = provider.uid || doc.id;

            // 1. Exclude customer themselves
            if (providerId === customerId) return;

            // 2. Check location and freshness
            const pLoc = provider.lastLocation;
            const pLastAt = provider.lastLocationAt;
            if (!pLoc || !pLastAt) return;

            const ageMs = now - pLastAt.toMillis();
            if (ageMs > MAX_AGE_MS) return;

            // 3. Check category matching (unless request is "Any")
            const categories = provider.serviceCategories || [];
            if (category !== "Any" && !categories.includes(category)) return;

            // 4. Check 25 km radius
            const distance = calculateDistanceKm(
                customerLocation.latitude, customerLocation.longitude,
                pLoc.latitude, pLoc.longitude
            );

            if (distance <= 25.0) {
                matchedProviderCount++;
                const token = provider.fcmToken;
                if (token && typeof token === 'string' && token.trim() !== "") {
                    tokenCount++;
                }
                targets.push({ providerId, token, distance: Number(distance.toFixed(1)) });
            }
        });

        console.log(`Matched ${matchedProviderCount} providers within 25 km. Tokens found: ${tokenCount}`);

        for (const target of targets) {
            const notifId = `${alertId}_${target.providerId}`;
            const notifRef = db.collection("providerNotifications").doc(notifId);

            const title = `🚨 Emergency ${target.distance} km away`;
            const body = note ? `Note: ${note}` : `A customer needs urgent help near you (${category}).`;

            // Idempotent creation of providerNotifications document
            batch.set(notifRef, {
                notificationId: notifId,
                providerId: target.providerId,
                alertId: alertId,
                type: "EMERGENCY_ALERT",
                title: title,
                body: body,
                serviceType: category,
                distanceKm: target.distance,
                isRead: false,
                status: "ACTIVE",
                createdAt: FieldValue.serverTimestamp()
            }, { merge: true });

            notificationCount++;

            if (target.token) {
                const message = {
                    token: target.token,
                    notification: {
                        title: title,
                        body: body
                    },
                    data: {
                        alertId: alertId,
                        title: title,
                        body: body,
                        serviceType: category,
                        distanceKm: String(target.distance),
                        type: "EMERGENCY_ALERT"
                    }
                };

                try {
                    await messaging.send(message);
                    successCount++;
                } catch (error) {
                    failureCount++;
                    console.error(`Failed to send FCM token to provider ${target.providerId}:`, error);
                    // Remove invalid tokens
                    if (
                        error.code === 'messaging/invalid-registration-token' ||
                        error.code === 'messaging/registration-token-not-registered'
                    ) {
                        await db.collection("users").doc(target.providerId).update({ fcmToken: null });
                        console.log(`Removed invalid FCM token for provider ${target.providerId}`);
                    }
                }
            }
        }

        await batch.commit();

        console.log(`Alert ID: ${alertId}`);
        console.log(`Matched provider count: ${matchedProviderCount}`);
        console.log(`Token count: ${tokenCount}`);
        console.log(`Notification count: ${notificationCount}`);
        console.log(`FCM success count: ${successCount}`);
        console.log(`FCM failure count: ${failureCount}`);
    }
);

exports.onEmergencyAlertUpdated = onDocumentUpdated(
    {
        document: "emergencyAlerts/{alertId}",
        region: "us-central1"
    },
    async (event) => {
        const change = event.data;
        if (!change) return;

        const alertId = event.params.alertId;
        const beforeData = change.before.data();
        const afterData = change.after.data();

        const oldStatus = beforeData.status;
        const newStatus = afterData.status;

        if (oldStatus === newStatus) return;

        console.log(`Alert ${alertId} status changed from ${oldStatus} to ${newStatus}`);

        const notifsSnap = await db.collection("providerNotifications")
            .where("alertId", "==", alertId)
            .get();

        if (notifsSnap.empty) return;

        const batch = db.batch();
        const acceptedProviderId = afterData.acceptedProviderId;

        notifsSnap.forEach((doc) => {
            const notif = doc.data();
            if (newStatus === "ACCEPTED") {
                if (notif.providerId === acceptedProviderId) {
                    batch.update(doc.ref, { isRead: true, status: "ACCEPTED" });
                } else {
                    batch.update(doc.ref, { status: "CLOSED" });
                }
            } else if (newStatus === "CANCELLED" || newStatus === "EXPIRED" || newStatus === "CLOSED") {
                batch.update(doc.ref, { status: newStatus });
            }
        });

        await batch.commit();
        console.log(`Updated provider notifications for alert ${alertId} under status ${newStatus}`);
    }
);
