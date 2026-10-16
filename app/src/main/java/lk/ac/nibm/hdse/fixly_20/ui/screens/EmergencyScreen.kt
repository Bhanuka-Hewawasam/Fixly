package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import lk.ac.nibm.hdse.fixly_20.viewmodel.EmergencyViewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderWithDistance

private val categoryFilters = listOf("Any", "Plumbing", "Electrical", "Cleaning", "Other")
private val radiusOptionsKm = listOf(5, 10, 25, 50)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // permission is checked in requestLocation() before any location call
@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    onPostUrgentJob: () -> Unit,
    viewModel: EmergencyViewModel = viewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val uiState by viewModel.uiState.collectAsState()
    var isLocating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<ProviderWithDistance?>(null) }
    var debugMsg by remember { mutableStateOf<String?>(null) }

    val customerLatLng = uiState.customerLocation?.let { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        customerLatLng?.let { position = CameraPosition.fromLatLngZoom(it, 13f) }
    }

    // Update camera when customer location changes for the first time
    LaunchedEffect(customerLatLng) {
        customerLatLng?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 13f)
        }
    }

    // Fresh GPS fix: this is "where the emergency is happening"
    fun fetchLocation() {
        isLocating = true
        locationFailed = false
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                isLocating = false
                if (location != null) {
                    viewModel.setCustomerLocation(GeoPoint(location.latitude, location.longitude))
                } else {
                    locationFailed = true
                }
            }
            .addOnFailureListener {
                isLocating = false
                locationFailed = true
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchLocation() else locationFailed = true
    }

    fun requestLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) fetchLocation()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    LaunchedEffect(Unit) { requestLocation() }

    // Opens the phone's dialer with the number filled in (no CALL_PHONE permission needed)
    fun callProvider(phoneNumber: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phoneNumber))))
        } catch (e: ActivityNotFoundException) {
            // Device has no dialer app
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚨 Emergency Help") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        when {
            // --- Step A: we need the customer's position first ---
            uiState.customerLocation == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isLocating) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Finding your location...")
                        } else {
                            Text(
                                text = if (locationFailed)
                                    "Couldn't get your location. Turn on GPS and allow location permission."
                                else
                                    "We need your location to find help near you.",
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { requestLocation() }) { Text("Enable Location") }
                        }
                    }
                }
            }

            // --- Step B: filters + map + live results ---
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            "Showing providers who are available right now within ${uiState.radiusKm} km of you, nearest first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    // --- DEBUG ONLY ACTIONS FOR TESTING ---
                    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    if (isDebug) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🛠️ Debug Actions (DEV ONLY)", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.seedDemoProviders { debugMsg = it } },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Seed Demo Providers", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.deleteDemoProviders { debugMsg = it } },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Delete Demo Providers", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    }
                                }
                                debugMsg?.let { msg ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }

                    Text(
                        "What do you need?",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryFilters.forEach { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.setCategory(category) },
                                label = { Text(category) }
                            )
                        }
                    }

                    Text(
                        "Search radius",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        radiusOptionsKm.forEach { km ->
                            FilterChip(
                                selected = uiState.radiusKm == km,
                                onClick = { viewModel.setRadius(km) },
                                label = { Text("$km km") }
                            )
                        }
                    }

                    // --- Google Map ---
                    if (customerLatLng != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState
                            ) {
                                // Customer location marker
                                Marker(
                                    state = MarkerState(position = customerLatLng),
                                    title = "You are here"
                                )

                                // Provider markers
                                uiState.results.forEach { item ->
                                    val pLoc = item.provider.lastLocation
                                    if (pLoc != null) {
                                        val pLatLng = LatLng(pLoc.latitude, pLoc.longitude)
                                        Marker(
                                            state = MarkerState(position = pLatLng),
                                            title = item.provider.name,
                                            snippet = "${String.format(java.util.Locale.getDefault(), "%.1f", item.distanceKm)} km away",
                                            onClick = {
                                                selectedProvider = item
                                                false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        uiState.results.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No providers available within ${uiState.radiusKm} km right now. " +
                                            "Try a bigger radius, or post an urgent job so providers can respond to you.",
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = onPostUrgentJob) { Text("Post an Urgent Job") }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.results) { item ->
                                    ProviderCard(
                                        item = item,
                                        isSelected = selectedProvider?.provider?.uid == item.provider.uid,
                                        onClick = {
                                            selectedProvider = item
                                            item.provider.lastLocation?.let { loc ->
                                                val latLng = LatLng(loc.latitude, loc.longitude)
                                                cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                                            }
                                        },
                                        onCall = { callProvider(item.provider.phoneNumber) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    item: ProviderWithDistance,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    val provider = item.provider
    val distanceText = String.format(java.util.Locale.getDefault(), "%.1f", item.distanceKm)
    val updatedText = when {
        item.minutesAgo < 1 -> "just now"
        item.minutesAgo < 60 -> "${item.minutesAgo} min ago"
        else -> "${item.minutesAgo / 60} h ago"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(provider.name, style = MaterialTheme.typography.titleMedium)
                if (provider.certificateUrl != null) {
                    AssistChip(onClick = {}, label = { Text("📄 Certificate") })
                }
            }

            Text(
                formatRating(provider.averageRating, provider.ratingCount),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Services: ${provider.serviceCategories.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "📍 $distanceText km away • location updated $updatedText",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onCall,
                enabled = provider.phoneNumber.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (provider.phoneNumber.isBlank()) "No phone number"
                    else "📞 Call ${provider.name}"
                )
            }
        }
    }
}
