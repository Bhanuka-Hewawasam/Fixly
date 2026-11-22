const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
    calculateDistanceKm,
    matchesEmergencyRequest,
    notificationStatusUpdate,
    shouldExpireAlert
} = require("./emergency-utils");

initializeApp();
const db = getFirestore();

exports.onEmergencyAlertCreated = onDocumentCreated(
    { document: "emergencyAlerts/{alertId}", region: "us-central1" },
    async (event) => {
        const snap = event.data;
        if (!snap) return;

        const alertId = event.params.alertId;
        const alert = snap.data();
        const customerId = alert.customerId;
        const customerLocation = alert.location;
        if (alert.status !== "ACTIVE" || !customerLocation) return;

        const areas = await db.collection("emergencyServiceAreas")
            .where("availableForEmergency", "==", true)
            .get();
        const targets = [];
        for (const areaDoc of areas.docs) {
            const area = areaDoc.data();
            const providerId = area.providerId || areaDoc.id;
            const latitude = area.serviceLatitude;
            const longitude = area.serviceLongitude;
            const serviceArea = { ...area, roles: ["provider"] };
            if (!matchesEmergencyRequest({
                providerId,
                provider: serviceArea,
                customerId,
                customerLocation,
                category: alert.category,
                selectedProviderId: alert.selectedProviderId
            })) continue;

            const distance = calculateDistanceKm(
                customerLocation.latitude, customerLocation.longitude, latitude, longitude
            );
            const providerSnapshot = await db.collection("users").doc(providerId).get();
            targets.push({
                providerId,
                token: providerSnapshot.get("fcmToken"),
                distance: Number(distance.toFixed(1))
            });
        }

        for (const target of targets) {
            const notificationId = `${alertId}_${target.providerId}`;
            const notificationRef = db.collection("providerNotifications").doc(notificationId);
            const title = `🚨 Emergency ${target.distance} km away`;
            const body = alert.note
                ? `Note: ${alert.note}`
                : `A customer needs urgent help near you (${alert.category || "Any"}).`;

            const created = await db.runTransaction(async (transaction) => {
                const latestAlert = await transaction.get(snap.ref);
                const latest = latestAlert.data();
                if (!latestAlert.exists ||
                    latest.status !== "ACTIVE" ||
                    latest.expiresAt?.toMillis?.() <= Date.now()) return false;
                const existing = await transaction.get(notificationRef);
                if (existing.exists) return false;
                transaction.create(notificationRef, {
                    notificationId,
                    providerId: target.providerId,
                    alertId,
                    type: "EMERGENCY_ALERT",
                    title,
                    body,
                    serviceType: alert.category || "Any",
                    distanceKm: target.distance,
                    isRead: false,
                    isCleared: false,
                    status: "ACTIVE",
                    createdAt: FieldValue.serverTimestamp(),
                    expiresAt: alert.expiresAt
                });
                return true;
            });
            if (!created || typeof target.token !== "string" || !target.token.trim()) continue;

            try {
                await getMessaging().send({
                    token: target.token,
                    notification: { title, body },
                    data: {
                        alertId,
                        title,
                        body,
                        serviceType: alert.category || "Any",
                        distanceKm: String(target.distance),
                        type: "EMERGENCY_ALERT"
                    }
                });
            } catch (error) {
                console.error(`Failed to send emergency notification to ${target.providerId}:`, error);
                if ([
                    "messaging/invalid-registration-token",
                    "messaging/registration-token-not-registered"
                ].includes(error.code)) {
                    await db.collection("users").doc(target.providerId)
                        .update({ fcmToken: FieldValue.delete() });
                }
            }
        }
    }
);

exports.onEmergencyAlertUpdated = onDocumentUpdated(
    { document: "emergencyAlerts/{alertId}", region: "us-central1" },
    async (event) => {
        const change = event.data;
        if (!change || change.before.get("status") === change.after.get("status")) return;

        const alertId = event.params.alertId;
        const after = change.after.data();
        const notifications = await db.collection("providerNotifications")
            .where("alertId", "==", alertId)
            .get();
        if (!notifications.empty) {
            const updates = notifications.docs
                .map((doc) => ({
                    ref: doc.ref,
                    update: notificationStatusUpdate(
                        doc.data(),
                        after.status,
                        after.acceptedProviderId
                    )
                }))
                .filter((item) => item.update !== null);
            for (let offset = 0; offset < updates.length; offset += 450) {
                const batch = db.batch();
                updates.slice(offset, offset + 450).forEach((item) => {
                    batch.update(item.ref, item.update);
                });
                await batch.commit();
            }
        }

        if (after.status === "ACCEPTED" && !change.before.get("customerNotificationClaimed")) {
            const customerSnapshot = await db.collection("users").doc(after.customerId).get();
            const token = customerSnapshot.get("fcmToken");
            if (typeof token !== "string" || !token.trim()) return;
            try {
                await getMessaging().send({
                    token,
                    notification: {
                        title: "Emergency request accepted",
                        body: `${after.acceptedProviderName || "A provider"} accepted your request.`
                    },
                    data: { alertId, type: "EMERGENCY_ACCEPTED" }
                });
            } catch (error) {
                console.error(`Failed to notify customer ${after.customerId}:`, error);
            }
        }
    }
);

exports.expireEmergencyAlerts = onSchedule(
    { schedule: "every 5 minutes", region: "us-central1" },
    async () => {
        const expiredCandidates = await db.collection("emergencyAlerts")
            .where("expiresAt", "<=", new Date())
            .limit(450)
            .get();
        const batch = db.batch();
        let count = 0;
        expiredCandidates.docs.forEach((doc) => {
            const expiry = doc.get("expiresAt")?.toMillis?.() || 0;
            if (shouldExpireAlert(doc.get("status"), expiry, Date.now())) {
                batch.update(doc.ref, { status: "EXPIRED" });
                count++;
            }
        });
        if (count > 0) await batch.commit();
        console.log(`Expired ${count} emergency requests`);
    }
);
