package lk.ac.nibm.hdse.fixly_20

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import lk.ac.nibm.hdse.fixly_20.navigation.AppNavigation
import lk.ac.nibm.hdse.fixly_20.ui.theme.Fixly_20Theme
import lk.ac.nibm.hdse.fixly_20.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.init(applicationContext)
        setContent {
            val isDarkMode by ThemeState.isDarkMode.collectAsState()
            Fixly_20Theme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
