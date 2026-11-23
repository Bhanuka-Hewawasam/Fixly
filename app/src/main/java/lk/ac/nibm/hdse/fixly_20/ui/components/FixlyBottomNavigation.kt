package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    object History : BottomNavItem("my_bookings", "History", Icons.AutoMirrored.Filled.ListAlt)
    object AvailableJobs : BottomNavItem("available_jobs", "Jobs", Icons.Default.Work)
    object MyWork : BottomNavItem("accepted_jobs", "My Work", Icons.Outlined.Handyman)
    object Emergency : BottomNavItem("emergency_sos", "Emergency", Icons.Default.Emergency)
    object Profile : BottomNavItem("profile", "Profile", Icons.Outlined.Person)
}

@Composable
fun FixlyBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    activeRole: String = "customer"
) {
    val items = if (activeRole == "customer") {
        listOf(BottomNavItem.Home, BottomNavItem.History, BottomNavItem.Emergency, BottomNavItem.Profile)
    } else {
        listOf(BottomNavItem.Home, BottomNavItem.AvailableJobs, BottomNavItem.MyWork, BottomNavItem.Profile)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = WindowInsets.navigationBars,
            tonalElevation = 0.dp
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val iconTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "bottomNavIconTint"
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (item == BottomNavItem.Emergency && selected) {
                                MaterialTheme.colorScheme.error
                            } else if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
