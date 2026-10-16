# Firebase Cloud Functions Setup Guide for SOS Push Notifications (FCM)

This guide outlines the backend setup required to dispatch background push notifications via Firebase Cloud Messaging (FCM) when a customer triggers an emergency SOS alert.

## Prerequisites & Billing
- **Firebase Blaze Plan**: Cloud Functions that make outbound HTTP/API calls (such as sending FCM messages to Google FCM servers) require the **Blaze (pay-as-you-go)** billing plan on Google Cloud / Firebase. The free Spark plan does not allow outbound network requests from functions.
- **Node.js Environment**: Node.js 18 or 20 installed locally with Firebase CLI (`npm install -g firebase-tools`).

---

## 1. Initialize Cloud Functions in your Firebase Project
In your project terminal:
```bash
firebase init functions
```
- Select JavaScript or TypeScript.
- Install dependencies: `cd functions && npm install firebase-admin firebase-functions`

---

## 2. Cloud Function Implementation (`index.js`)
Replace `functions/index.js` with the following code:

```javascript
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
db = getFirestore();

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

exports.onEmergencyAlertCreated = onDocumentCreated("emergencyAlerts/{alertId}", async (event) => {
    const snap = event.data;
    if (!snap) return;

    const alertData = snap.data();
    const customerId = alertData.customerId;
    const customerLocation = alertData.location; // GeoPoint { latitude, longitude }
    const category = alertData.category || "Any";
    const note = alertData.note || "";

    if (!customerLocation) return;

    // 1. Query all providers who are available for emergencies
    const providersSnapshot = await db.collection("users")
        .where("emergencyAvailable", "==", true)
        .get();

    const tokensToNotify = [];
    const now = Date.now();
    const MAX_AGE_MS = 3 * 60 * 60 * 1000; // 3 hours freshness limit

    providersSnapshot.forEach((doc) => {
        const provider = doc.data();
        const providerId = provider.uid || doc.id;

        // Exclude customer themselves
        if (providerId === customerId) return;

        // Check location and freshness
        const pLoc = provider.lastLocation;
        const pLastAt = provider.lastLocationAt;
        if (!pLoc || !pLastAt) return;

        const ageMs = now - pLastAt.toMillis();
        if (ageMs > MAX_AGE_MS) return;

        // Check category matching
        const categories = provider.serviceCategories || [];
        if (category !== "Any" && !categories.includes(category)) return;

        // Check 25 km radius
        const distance = calculateDistanceKm(
            customerLocation.latitude, customerLocation.longitude,
            pLoc.latitude, pLoc.longitude
        );

        if (distance <= 25.0) {
            if (provider.fcmToken) {
                tokensToNotify.push({ token: provider.fcmToken, distance: distance.toFixed(1) });
            }
        }
    });

    if (tokensToNotify.length === 0) {
        console.log("No eligible providers found within 25 km.");
        return;
    }

    // 2. Send FCM Push Notifications
    const messaging = getMessaging();
    for (const item of tokensToNotify) {
        const message = {
            token: item.token,
            notification: {
                title: `🚨 Emergency ${item.distance} km away`,
                body: note ? `Note: ${note}` : `A customer needs urgent help near you.`
            },
            data: {
                alertId: event.params.alertId,
                category: category
            }
        };

        try {
            await messaging.send(message);
        } catch (error) {
            console.error("Failed to send FCM token:", item.token, error);
            // If token is invalid/expired, remove it from user document
            if (error.code === 'messaging/invalid-registration-token' || error.code === 'messaging/registration-token-not-registered') {
                // Cleanup invalid token logic
            }
        }
    }

    console.log(`Successfully notified ${tokensToNotify.length} providers.`);
});
```

---

## 3. Deploy Cloud Functions
```bash
firebase deploy --only functions
```
