package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import lk.ac.nibm.hdse.fixly_20.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptedJobsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Accepted Jobs",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        EmptyStateView(
            icon = Icons.Default.TaskAlt,
            title = "No Accepted Jobs Yet",
            message = "When a customer selects your price bid on an open job post, it will appear here in your accepted jobs list.",
            modifier = Modifier.padding(padding)
        )
    }
}
