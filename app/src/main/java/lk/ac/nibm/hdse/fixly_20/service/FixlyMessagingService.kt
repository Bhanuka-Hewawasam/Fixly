package lk.ac.nibm.hdse.fixly_20.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.BuildConfig
import lk.ac.nibm.hdse.fixly_20.MainActivity

class FixlyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: Generated FCM token: $token")
        }
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: Notification received from FCM. Data: ${message.data}")
        }

        val data = message.data
        val alertId = data["alertId"] ?: ""
        val title = data["title"] ?: message.notification?.title ?: "Nearby emergency request"
        val body = data["body"] ?: message.notification?.body ?: "A customer needs urgent help near you."
        val serviceType = data["serviceType"] ?: data["category"] ?: ""
        val distanceKm = data["distanceKm"] ?: ""

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: Notification received - alertId: $alertId, title: $title, body: $body, distance: $distanceKm km")
        }

        createNotificationChannelIfNeeded()
        showEmergencyNotification(alertId, title, body, serviceType, distanceKm)
    }

    private fun showEmergencyNotification(
        alertId: String,
        title: String,
        body: String,
        serviceType: String,
        distanceKm: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: POST_NOTIFICATIONS permission not granted, skipping notification post.")
            }
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_EMERGENCY_ALERT_ID, alertId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = buildString {
            if (serviceType.isNotBlank()) append("Service: $serviceType • ")
            if (distanceKm.isNotBlank()) append("$distanceKm km away\n")
            append(body)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "View Alert", pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(alertId.hashCode(), notification)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: Notification posted successfully for alertId: $alertId")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "DEBUG: Failed to post notification", e)
            }
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for nearby emergency requests"
                enableVibration(true)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "FixlyMessagingService"
        const val CHANNEL_ID = "emergency_alerts"
        const val EXTRA_EMERGENCY_ALERT_ID = "emergency_alert_id"

        fun saveTokenToFirestore(token: String) {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid
            if (uid == null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "DEBUG: Cannot save FCM token yet — user is not authenticated.")
                }
                return
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: Authenticated UID: $uid. Saving FCM token...")
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FirebaseFirestore.getInstance().collection("users")
                        .document(uid)
                        .update("fcmToken", token)
                        .await()

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "DEBUG: Token saved successfully for UID: $uid")
                    }
                } catch (e: Exception) {
                    try {
                        FirebaseFirestore.getInstance().collection("users")
                            .document(uid)
                            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                            .await()
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "DEBUG: Token saved successfully via merge for UID: $uid")
                        }
                    } catch (ex: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "DEBUG: Token save failed for UID: $uid", ex)
                        }
                    }
                }
            }
        }
    }
}
