package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onPostJob: () -> Unit,
    onViewBookings: () -> Unit,
    onViewAvailableJobs: () -> Unit,
    onViewAcceptedJobs: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fixly") },
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${uiState.errorMessage}")
                }
            }

            else -> {
                val user = uiState.user
                val hasBothRoles = user != null &&
                    user.roles.contains("customer") && user.roles.contains("provider")

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Welcome, ${user?.name ?: ""}",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    if (user?.roles?.contains("provider") == true && !user.isVerified) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("Certificate pending verification") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Only show the role switcher when the user registered for BOTH roles
                    if (hasBothRoles) {
                        TabRow(
                            selectedTabIndex = if (uiState.activeRole == "customer") 0 else 1
                        ) {
                            Tab(
                                selected = uiState.activeRole == "customer",
                                onClick = { viewModel.switchRole("customer") },
                                text = { Text("Customer") }
                            )
                            Tab(
                                selected = uiState.activeRole == "provider",
                                onClick = { viewModel.switchRole("provider") },
                                text = { Text("Provider") }
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // --- Content swaps based on the active role ---
                    if (uiState.activeRole == "customer") {
                        CustomerDashboard(onPostJob = onPostJob, onViewBookings = onViewBookings)
                    } else {
                        ProviderDashboard(
                            onViewAvailableJobs = onViewAvailableJobs,
                            onViewAcceptedJobs = onViewAcceptedJobs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDashboard(
    onPostJob: () -> Unit,
    onViewBookings: () -> Unit
) {
    Column {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Need a repair done?", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Post a job and get bids from nearby providers.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onPostJob, modifier = Modifier.fillMaxWidth()) {
                    Text("Post a Job")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onViewBookings, modifier = Modifier.fillMaxWidth()) {
            Text("My Bookings")
        }
    }
}

@Composable
private fun ProviderDashboard(
    onViewAvailableJobs: () -> Unit,
    onViewAcceptedJobs: () -> Unit
) {
    Column {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Looking for work?", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Browse nearby job posts and place your bid.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onViewAvailableJobs, modifier = Modifier.fillMaxWidth()) {
                    Text("Available Jobs")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onViewAcceptedJobs, modifier = Modifier.fillMaxWidth()) {
            Text("My Accepted Jobs")
        }
    }
}
