const { onDocumentCreated, onDocumentUpdated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
    MAX_PROVIDER_DISTANCE_KM,
    calculateDistanceKm,
    matchesEmergencyRequest,
    notificationStatusUpdate,
    shouldExpireAlert,
    emergencyProviderProfile
} = require("./emergency-utils");

initializeApp();
const db = getFirestore();

exports.syncEmergencyProviderProfile = onDocumentWritten(
    {
        document: "users/{providerId}",
        region: "us-central1"
    },
    async (event) => {
        const providerId = event.params.providerId;
        const userSnapshot = await db.collection("users").doc(providerId).get();
        const profileRef = db.collection("emergencyProviderProfiles").doc(providerId);
        const profile = userSnapshot.exists
            ? emergencyProviderProfile(providerId, userSnapshot.data(), Date.now())
            : null;

        if (profile) {
            await profileRef.set(profile);
        } else {
            await profileRef.delete();
        }
    }
);

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
        const messaging = getMessaging();
        const targets = [];

        providersSnapshot.forEach((doc) => {
            const provider = doc.data();
            const providerId = provider.uid || doc.id;
            if (!matchesEmergencyRequest({
                providerId,
                provider,
                customerId,
                customerLocation,
                category,
                selectedProviderId: alertData.selectedProviderId,
                nowMs: now
            })) return;

            matchedProviderCount++;
            const token = provider.fcmToken;
            if (token && typeof token === 'string' && token.trim() !== "") {
                tokenCount++;
            }
            const distance = calculateDistanceKm(
                customerLocation.latitude,
                customerLocation.longitude,
                provider.lastLocation.latitude,
                provider.lastLocation.longitude
            );
            targets.push({ providerId, userDocId: doc.id, token, distance: Number(distance.toFixed(1)) });
        });

        console.log(`Matched ${matchedProviderCount} providers within ${MAX_PROVIDER_DISTANCE_KM} km. Tokens found: ${tokenCount}`);

        for (const target of targets) {
            const notifId = `${alertId}_${target.providerId}`;
            const notifRef = db.collection("providerNotifications").doc(notifId);

            const title = `🚨 Emergency ${target.distance} km away`;
            const body = note ? `Note: ${note}` : `A customer needs urgent help near you (${category}).`;

            try {
                const created = await db.runTransaction(async (transaction) => {
                    const latestAlert = await transaction.get(snap.ref);
                    const latestAlertData = latestAlert.data();
                    const latestExpiry = latestAlertData?.expiresAt?.toMillis?.() ?? 0;
                    if (
                        !latestAlert.exists ||
                        latestAlertData.status !== "ACTIVE" ||
                        latestExpiry <= Date.now()
                    ) return false;
                    const existing = await transaction.get(notifRef);
                    if (existing.exists) return false;
                    transaction.create(notifRef, {
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
                    });
                    return true;
                });
                if (!created) continue;
            } catch (error) {
                console.error(`Could not create provider notification ${notifId}:`, error);
                throw error;
            }
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
                        await db.collection("users").doc(target.userDocId)
                            .update({ fcmToken: FieldValue.delete() })
                            .catch((updateError) => console.error(`Could not clear invalid token for ${target.providerId}:`, updateError));
                    }
                }
            }
        }

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

        // An empty query result means there's nothing to update.
        if (notifsSnap.empty) return;

        const acceptedProviderId = afterData.acceptedProviderId;
        const notificationUpdates = notifsSnap.docs
            .map((doc) => ({
                ref: doc.ref,
                update: notificationStatusUpdate(doc.data(), newStatus, acceptedProviderId)
            }))
            .filter((item) => item.update !== null);

        for (let offset = 0; offset < notificationUpdates.length; offset += 450) {
            const batch = db.batch();
            notificationUpdates.slice(offset, offset + 450).forEach((item) => {
                batch.update(item.ref, item.update);
            });
            await batch.commit();
        }
        console.log(`Updated provider notifications for alert ${alertId} under status ${newStatus}`);

        if (newStatus === "ACCEPTED" && oldStatus !== "ACCEPTED") {
            const customerId = afterData.customerId;
            const providerName = afterData.acceptedProviderName || "A provider";
            const alertRef = change.after.ref;
            const shouldNotifyCustomer = await db.runTransaction(async (transaction) => {
                const latestAlert = await transaction.get(alertRef);
                if (latestAlert.get("customerNotificationClaimed") === true) return false;
                transaction.update(alertRef, { customerNotificationClaimed: true });
                return true;
            });
            const customer = customerId ? await db.collection("users").doc(customerId).get() : null;
            const token = customer && customer.get("fcmToken");
            if (shouldNotifyCustomer && typeof token === "string" && token.trim()) {
                try {
                    await getMessaging().send({
                        token,
                        notification: {
                            title: "Emergency request accepted",
                            body: `${providerName} accepted your request and is on the way.`
                        },
                        data: { alertId, type: "EMERGENCY_ACCEPTED" }
                    });
                } catch (error) {
                    console.error(`Failed to notify customer ${customerId}:`, error);
                    if (
                        error.code === "messaging/invalid-registration-token" ||
                        error.code === "messaging/registration-token-not-registered"
                    ) {
                        await customer.ref.update({ fcmToken: FieldValue.delete() })
                            .catch((updateError) => console.error(`Could not clear invalid token for ${customerId}:`, updateError));
                    }
                }
            }
        }
    }
);

exports.expireEmergencyAlerts = onSchedule(
    { schedule: "every 5 minutes", region: "us-central1" },
    async () => {
        let expiredCount = 0;
        let cursor = null;
        while (true) {
            let query = db.collection("emergencyAlerts")
                .where("expiresAt", "<=", new Date())
                .orderBy("expiresAt")
                .limit(450);
            if (cursor) query = query.startAfter(cursor);
            const snapshot = await query.get();
            if (snapshot.empty) break;

            const batch = db.batch();
            let batchCount = 0;
            snapshot.docs.forEach((doc) => {
                const expiresAtMs = doc.get("expiresAt")?.toMillis?.() ?? 0;
                if (shouldExpireAlert(doc.get("status"), expiresAtMs, Date.now())) {
                    batch.update(doc.ref, { status: "EXPIRED" });
                    expiredCount++;
                    batchCount++;
                }
            });
            if (batchCount > 0) await batch.commit();
            cursor = snapshot.docs[snapshot.docs.length - 1];
            if (snapshot.size < 450) break;
        }
        console.log(`Expired ${expiredCount} emergency requests`);
    }
);

exports.pruneStaleEmergencyProviderProfiles = onSchedule(
    { schedule: "every 5 minutes", region: "us-central1" },
    async () => {
        const cutoff = new Date(Date.now() - 15 * 60 * 1000);
        let removedCount = 0;
        while (true) {
            const staleProfiles = await db.collection("emergencyProviderProfiles")
                .where("lastLocationAt", "<=", cutoff)
                .limit(450)
                .get();
            if (staleProfiles.empty) break;
            const batch = db.batch();
            staleProfiles.docs.forEach((doc) => batch.delete(doc.ref));
            await batch.commit();
            removedCount += staleProfiles.size;
        }
        console.log(`Removed ${removedCount} stale emergency provider profiles`);
    }
);
