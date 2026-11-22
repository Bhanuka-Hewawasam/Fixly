package lk.ac.nibm.hdse.fixly_20.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the phone's dialer with the number filled in (no CALL_PHONE permission needed). */
fun Context.dialNumber(phoneNumber: String) {
    try {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phoneNumber))))
    } catch (e: ActivityNotFoundException) {
        // Device has no dialer app (e.g. some emulators) — nothing to open
    }
}

/** Opens a maps app on the given point so the user can start navigation. */
fun Context.openInMaps(latitude: Double, longitude: Double, label: String) {
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        // No maps app installed
    }
}
