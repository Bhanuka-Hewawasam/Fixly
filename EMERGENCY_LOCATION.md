# Emergency provider service locations

Fixly Emergency uses a provider's registered service location. It does not track a provider's live location.

## Provider profile data

Providers save `serviceAddress`, `serviceCity`, `serviceLatitude`, `serviceLongitude`, `serviceRadiusKm`, `serviceCategories`, and `availableForEmergency` in their own `users/{uid}` profile. Address and city/area are optional. Valid coordinates, a 1-100 km radius, and at least one category are required before saving.

The same transaction maintains the restricted `emergencyServiceAreas/{uid}` public projection. It contains only provider display details, registered service-area fields, rating, verification status, suspension status, and availability. Customers can read only available, non-suspended provider projections; private user documents remain owner-only. Providers can update only their own profile and projection. Provider contact details remain in a separate restricted document and are disclosed only for that customer's selected active request.

`availableForEmergency` controls emergency search only. Logging out does not change it. Turning it off updates the projection immediately, so the provider disappears from customer searches. This is a registered service area and does not promise the provider is currently online or available to respond.

## Location permissions and billing

- The provider can select the service point by tapping the map. **Use current location once** is optional and requests foreground fine/coarse location permission only when tapped; the location is saved as a static service point.
- No background location permission, location foreground service, emergency device token, session endpoint, or recurring provider location update is used.
- Customer location is requested while the Emergency search screen is in use, not continuously.
- Provider map/search projection and emergency requests use Firebase Authentication and Firestore client operations. They do not depend on an emergency-location Cloud Function or Firebase Blaze.
- Firebase Cloud Messaging delivery is optional; the in-app provider alert and request status use Firestore listeners. A logged-out provider will not receive in-app alerts until signing in again.
- Android 13+ notification permission may be needed for optional app push notifications. There is no persistent location-sharing notification because no background tracking runs.

## Manual provider checks

1. Sign in with a provider account and open **Profile**.
2. Optionally enter a service address and city/area. Tap the map to choose the registered point, or grant location permission and use **Use current location once**. Confirm the coordinate preview updates; address and city/area can remain blank.
3. Choose one or more categories, set a positive service radius, turn **Emergency Availability** ON, and save. Confirm the profile indicates it was saved.
4. Save with address and city/area blank to confirm they are optional. Try saving without coordinates or categories, or with an invalid radius; confirm the invalid profile is rejected with an error.
5. Log out normally. Sign in as a customer: provider availability and registered service location remain saved after logout.
6. Sign in as the provider again, turn availability OFF, save, and confirm that the provider no longer appears in customer emergency search.

## Manual customer checks

1. Ensure a provider has saved a valid service location, matching service category, positive radius, and Emergency Availability ON.
2. Sign in as another customer and open Emergency search. Grant location permission only for the search flow and confirm the customer location is not tracked after leaving it.
3. Search by the provider's category and choose a radius that includes the provider's service point and is within the provider's service radius.
4. Confirm the marker and provider card say **Service Location**, show distance, rating and verification, and explain: “This is the provider’s registered service area, not their live current location.”
5. Try a non-matching category, a smaller radius, a provider service radius that excludes the customer, a suspended provider, and the customer's own provider profile; each must be excluded.
6. Select an eligible provider, send a request with a note, and verify the provider sees it after signing in and can accept or decline. Confirm the customer sees request status changes in real time.
7. Turn the provider's Emergency Availability OFF and save; verify the provider disappears from the map/search.

## Limitations

The map pin is the provider-entered registered service point, not a live or guaranteed current position. Providers logged out of Fixly remain discoverable when enabled, but cannot receive in-app requests or respond until they log in again. Selecting a saved point does not require GPS; using the optional one-time location shortcut does.
