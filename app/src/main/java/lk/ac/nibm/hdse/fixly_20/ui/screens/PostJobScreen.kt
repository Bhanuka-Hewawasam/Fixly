package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.viewmodel.PostJobViewModel

private val categories = listOf("Plumbing", "Electrical", "Cleaning", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // permission is checked before the location call below
@Composable
fun PostJobScreen(
    onJobPosted: () -> Unit,
    onBack: () -> Unit,
    viewModel: PostJobViewModel = viewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var category by remember { mutableStateOf(categories.first()) }
    var description by remember { mutableStateOf("") }
    var preferredDate by remember { mutableStateOf("") }
    var preferredTime by remember { mutableStateOf("") }
    var isEmergency by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isPostSuccessful) {
        if (uiState.isPostSuccessful) onJobPosted()
    }

    // Asks for fine-location permission; on grant, fetches the current GPS position
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setFetchingLocation(true)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.setLocation(GeoPoint(location.latitude, location.longitude))
                } else {
                    viewModel.setFetchingLocation(false)
                }
            }
        }
    }

    fun requestLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.setFetchingLocation(true)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.setLocation(GeoPoint(location.latitude, location.longitude))
                } else {
                    viewModel.setFetchingLocation(false)
                }
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post a Job") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Cancel") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Service Category", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Describe the problem") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = preferredDate,
                    onValueChange = { preferredDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = preferredTime,
                    onValueChange = { preferredTime = it },
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { requestLocation() },
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    uiState.isFetchingLocation -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Getting location...")
                    }
                    uiState.capturedLocation != null -> Text("Location Captured ✓")
                    else -> Text("Use Current Location (GPS)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isEmergency, onCheckedChange = { isEmergency = it })
                Text("This is an emergency — find nearby providers urgently")
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.postJob(category, description, preferredDate, preferredTime, isEmergency, address)
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Post Job")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
