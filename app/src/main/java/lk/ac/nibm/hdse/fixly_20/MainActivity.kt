package lk.ac.nibm.hdse.fixly_20

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import lk.ac.nibm.hdse.fixly_20.navigation.AppNavigation
import lk.ac.nibm.hdse.fixly_20.ui.theme.Fixly_20Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Cloudinary for free image/certificate hosting
        initCloudinary()

        setContent {
            Fixly_20Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun initCloudinary() {
        try {
            val config = mapOf(
                "cloud_name" to "rrsnpui6",
                "secure" to true
            )
            com.cloudinary.android.MediaManager.init(this, config)
        } catch (e: Exception) {
            // Already initialized
        }
    }
}
