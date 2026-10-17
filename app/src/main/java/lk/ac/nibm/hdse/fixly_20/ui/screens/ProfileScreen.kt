package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import lk.ac.nibm.hdse.fixly_20.ui.components.BottomNavItem
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyBottomNavBar
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderAvailabilityViewModel
import java.util.Locale

private val providerServiceCategories = listOf(
    "Vehicle Mechanic",
    "Electrician",
    "Plumber",
    "AC Technician",
    "Computer Technician",
    "Other"
)

private val sriLankaMapCenter = LatLng(7.8731, 80.7718)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    activeRole: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ProviderAvailabilityViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var selectedRadius by remember { mutableStateOf(20f) }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var availableForEmergency by remember { mutableStateOf(false) }
    var isGettingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.user?.uid) {
        uiState.user?.let { user ->
            address = user.serviceAddress
            city = user.serviceCity
            latitude = user.serviceLatitude?.toString().orEmpty()
            longitude = user.serviceLongitude?.toString().orEmpty()
            selectedRadius = user.serviceRadiusKm.coerceIn(1, 100).toFloat()
            selectedCategories = user.serviceCategories.toSet()
            availableForEmergency = user.availableForEmergency
        }
    }

    fun updateMapLocation(point: LatLng) {
        latitude = point.latitude.toString()
        longitude = point.longitude.toString()
        locationError = null
    }

    fun useCurrentLocationOnce() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            locationError = "Turn on Location services, then try again."
            return
        }
        val priority = if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        isGettingLocation = true
        fusedLocationClient.getCurrentLocation(priority, null)
            .addOnSuccessListener { location ->
                isGettingLocation = false
                if (location == null) {
                    locationError = "Couldn't get a location. Choose the service point on the map."
                } else {
                    updateMapLocation(LatLng(location.latitude, location.longitude))
                }
            }
            .addOnFailureListener {
                isGettingLocation = false
                locationError = "Couldn't get a location. Choose the service point on the map."
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) useCurrentLocationOnce()
        else locationError = "Allow location permission to use your location once, or select the point on the map."
    }

    val mapPoint = remember(latitude, longitude) {
        val lat = latitude.toDoubleOrNull()
        val lng = longitude.toDoubleOrNull()
        if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
            LatLng(lat, lng)
        } else sriLankaMapCenter
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapPoint, if (latitude.isBlank()) 7f else 14f)
    }
    LaunchedEffect(mapPoint) { cameraPositionState.position = CameraPosition.fromLatLngZoom(mapPoint, 14f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            FixlyBottomNavBar(
                currentRoute = BottomNavItem.Profile.route,
                activeRole = activeRole,
                onNavigate = onNavigate
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(uiState.user?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(uiState.user?.phoneNumber.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text(uiState.user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (activeRole == "provider") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Service Location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "This is the provider’s registered service area, not their live current location.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Service address (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City or area (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = !isGettingLocation,
                                    onClick = {
                                        val fineGranted = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        val coarseGranted = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (fineGranted || coarseGranted) useCurrentLocationOnce()
                                        else locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                ) {
                                    Text(if (isGettingLocation) "Getting location…" else "Use current location once")
                                }
                            }
                            Text("Or tap the map to select your registered service point.")
                            GoogleMap(
                                modifier = Modifier.fillMaxWidth().height(230.dp),
                                cameraPositionState = cameraPositionState,
                                onMapClick = ::updateMapLocation
                            ) {
                                Marker(
                                    state = MarkerState(position = mapPoint),
                                    title = "Service Location",
                                    snippet = "Registered provider service point"
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (latitude.isBlank() || longitude.isBlank()) "Select a service point on the map"
                                else "Latitude ${String.format(Locale.US, "%.6f", latitude.toDouble())}, " +
                                    "Longitude ${String.format(Locale.US, "%.6f", longitude.toDouble())}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            locationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                            Text("Service radius: ${selectedRadius.toInt()} km", fontWeight = FontWeight.SemiBold)
                            Slider(
                                value = selectedRadius,
                                onValueChange = { selectedRadius = it },
                                valueRange = 1f..100f,
                                steps = 98
                            )
                            Text("Service categories", fontWeight = FontWeight.SemiBold)
                            providerServiceCategories.chunked(2).forEach { rowCategories ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowCategories.forEach { category ->
                                        FilterChip(
                                            selected = category in selectedCategories,
                                            onClick = {
                                                selectedCategories = if (category in selectedCategories) {
                                                    selectedCategories - category
                                                } else selectedCategories + category
                                            },
                                            label = { Text(category) }
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Emergency Availability", fontWeight = FontWeight.Bold)
                                    Text(
                                        if (availableForEmergency) {
                                            "After saving, your registered service location stays visible after logout. Sign in to respond to customer requests."
                                        }
                                        else "After saving, your service location will be hidden from emergency search.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = availableForEmergency,
                                    onCheckedChange = { availableForEmergency = it }
                                )
                            }
                            uiState.errorMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                            if (uiState.saved) {
                                Text("Service profile saved.", color = MaterialTheme.colorScheme.primary)
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isUpdating && !isGettingLocation,
                                onClick = {
                                    viewModel.saveServiceProfile(
                                        serviceAddress = address,
                                        serviceCity = city,
                                        latitude = latitude,
                                        longitude = longitude,
                                        serviceRadiusKm = selectedRadius.toInt(),
                                        serviceCategories = selectedCategories.toList(),
                                        availableForEmergency = availableForEmergency
                                    )
                                }
                            ) {
                                if (uiState.isUpdating) CircularProgressIndicator()
                                else Text("Save Service Profile")
                            }
                        }
                    }
                }
            }
        }
    }
}
