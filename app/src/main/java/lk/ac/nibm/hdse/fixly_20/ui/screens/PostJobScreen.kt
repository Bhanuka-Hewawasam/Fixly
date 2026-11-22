package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.GeoPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lk.ac.nibm.hdse.fixly_20.ui.components.CategoryCard
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyPrimaryButton
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlySecondaryButton
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyTextField
import lk.ac.nibm.hdse.fixly_20.ui.theme.FixlyEmergency
import lk.ac.nibm.hdse.fixly_20.ui.theme.FixlyEmergencyContainer
import lk.ac.nibm.hdse.fixly_20.viewmodel.PostJobViewModel
import java.io.IOException
import java.util.Calendar
import java.util.Locale

import lk.ac.nibm.hdse.fixly_20.util.ServiceCategories

private val categories = ServiceCategories.canonicalList
private val sriLankaMapCenter = LatLng(7.8731, 80.7718)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun PostJobScreen(
    onJobPosted: () -> Unit,
    onBack: () -> Unit,
    viewModel: PostJobViewModel = viewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("Plumbing") }
    var description by remember { mutableStateOf("") }
    var preferredDate by remember { mutableStateOf("") }
    var preferredTime by remember { mutableStateOf("") }
    var isEmergency by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showLocationPicker by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isPostSuccessful) {
        if (uiState.isPostSuccessful) onJobPosted()
    }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImages = uris
    }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            preferredDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val amPm = if (hourOfDay < 12) "AM" else "PM"
            val hour12 = if (hourOfDay == 0) 12 else if (hourOfDay > 12) hourOfDay - 12 else hourOfDay
            preferredTime = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false
    )

    fun captureLocation(location: Location?) {
        if (location == null) {
            viewModel.setFetchingLocation(false)
            return
        }
        val point = GeoPoint(location.latitude, location.longitude)
        val fallbackAddress = formatLocationCoordinates(point)
        address = fallbackAddress
        viewModel.setLocation(point, fallbackAddress)
        locationScope.launch {
            val resolvedAddress = reverseGeocode(context, point)
            if (viewModel.uiState.value.capturedLocation == point) {
                address = resolvedAddress
                viewModel.setLocation(point, resolvedAddress)
            }
        }
    }

    fun fetchLastLocation() {
        viewModel.setFetchingLocation(true)
        fusedLocationClient.lastLocation
            .addOnSuccessListener(::captureLocation)
            .addOnFailureListener { viewModel.setFetchingLocation(false) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLastLocation()
        } else {
            viewModel.setFetchingLocation(false)
        }
    }

    fun requestLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            fetchLastLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Post a Repair Job",
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
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    FixlyPrimaryButton(
                        text = "Submit Job Request",
                        icon = Icons.AutoMirrored.Filled.Send,
                        isLoading = uiState.isLoading,
                        onClick = {
                            viewModel.postJob(
                                context,
                                selectedCategory,
                                description,
                                preferredDate,
                                preferredTime,
                                isEmergency,
                                address.ifBlank { uiState.locationAddress.orEmpty() },
                                selectedImages
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // --- Section 1: Service Category ---
            FormSectionHeader(number = "1", title = "Select Service Category")
            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Scrollable / Grid Category Selection
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    CategoryCard(
                        category = category,
                        isSelected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        modifier = Modifier.width(105.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Section 2: Problem Description & Photos ---
            FormSectionHeader(number = "2", title = "Describe the Issue")
            Spacer(modifier = Modifier.height(12.dp))

            FixlyTextField(
                value = description,
                onValueChange = { description = it },
                label = "Problem Description",
                placeholder = "Describe what needs repair, e.g. 'Leaking kitchen pipe under the sink'",
                singleLine = false,
                minLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Photo Attachment Picker
            FixlySecondaryButton(
                text = if (selectedImages.isEmpty()) "Attach Photos of Problem (Optional)" else "${selectedImages.size} Photo(s) Attached ✓",
                icon = Icons.Default.AddAPhoto,
                onClick = { multiImagePicker.launch("image/*") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Section 3: Schedule Date & Time ---
            FormSectionHeader(number = "3", title = "Preferred Schedule")
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f).clickable { datePickerDialog.show() }) {
                    FixlyTextField(
                        value = preferredDate,
                        onValueChange = {},
                        label = "Date",
                        placeholder = "YYYY-MM-DD",
                        readOnly = true,
                        enabled = false,
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f).clickable { timePickerDialog.show() }) {
                    FixlyTextField(
                        value = preferredTime,
                        onValueChange = {},
                        label = "Time",
                        placeholder = "e.g. 10:00 AM",
                        readOnly = true,
                        enabled = false,
                        trailingIcon = {
                            IconButton(onClick = { timePickerDialog.show() }) {
                                Icon(Icons.Default.AccessTime, contentDescription = "Pick Time", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Section 4: Location & GPS ---
            FormSectionHeader(number = "4", title = "Location & Address")
            Spacer(modifier = Modifier.height(12.dp))

            FixlyTextField(
                value = address,
                onValueChange = { address = it },
                label = "Property Address / Landmark",
                placeholder = "e.g. No. 45, Main Street, Colombo 03",
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            uiState.capturedLocation?.let { selectedLocation ->
                Text(
                    text = "Selected location: ${uiState.locationAddress ?: formatLocationCoordinates(selectedLocation)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { requestLocation() },
                    enabled = !uiState.isFetchingLocation,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isFetchingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Getting location…")
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use current location")
                    }
                }
                OutlinedButton(
                    onClick = { showLocationPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose on map")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Section 5: Emergency Priority Toggle ---
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isEmergency) FixlyEmergencyContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, if (isEmergency) FixlyEmergency else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (isEmergency) FixlyEmergency else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emergency / Urgent Request",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Flags job as high priority for faster matching with nearby service providers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEmergency,
                        onCheckedChange = { isEmergency = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = FixlyEmergency,
                            checkedTrackColor = FixlyEmergencyContainer
                        )
                    )
                }
            }

            // Error Display
            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLocationPicker) {
        JobLocationPickerDialog(
            initialLocation = uiState.capturedLocation,
            onDismiss = { showLocationPicker = false },
            onConfirm = { point, selectedAddress ->
                address = selectedAddress
                viewModel.setLocation(point, selectedAddress)
                showLocationPicker = false
            }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun JobLocationPickerDialog(
    initialLocation: GeoPoint?,
    onDismiss: () -> Unit,
    onConfirm: (GeoPoint, String) -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraPositionState = rememberCameraPositionState {
        val initialPoint = initialLocation?.let { LatLng(it.latitude, it.longitude) } ?: sriLankaMapCenter
        position = CameraPosition.fromLatLngZoom(initialPoint, if (initialLocation == null) 7f else 15f)
    }
    val scope = rememberCoroutineScope()
    var selectedPoint by remember {
        mutableStateOf(initialLocation?.let { LatLng(it.latitude, it.longitude) } ?: sriLankaMapCenter)
    }
    var selectedAddress by remember {
        mutableStateOf(
            initialLocation?.let(::formatLocationCoordinates)
                ?: formatLocationCoordinates(GeoPoint(sriLankaMapCenter.latitude, sriLankaMapCenter.longitude))
        )
    }
    var isResolvingAddress by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var moveToDeviceLocation by remember { mutableStateOf(false) }

    fun animateToLocation(location: Location?) {
        if (location == null) {
            locationMessage = "Couldn't get your location. You can still choose a point on the map."
            return
        }
        locationMessage = null
        val point = LatLng(location.latitude, location.longitude)
        scope.launch {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(point, 15f))
        }
    }

    fun getDeviceLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    animateToLocation(location)
                } else {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener(::animateToLocation)
                        .addOnFailureListener {
                            locationMessage = "Couldn't get your location. You can still choose a point on the map."
                        }
                }
            }
            .addOnFailureListener {
                locationMessage = "Couldn't get your location. You can still choose a point on the map."
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            moveToDeviceLocation = true
        } else {
            locationMessage = "Location permission denied. You can still choose a point on the map."
        }
    }

    LaunchedEffect(initialLocation) {
        if (initialLocation == null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                animateToLocation(location)
            }
        }
    }

    LaunchedEffect(moveToDeviceLocation) {
        if (moveToDeviceLocation) {
            moveToDeviceLocation = false
            getDeviceLocation()
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            selectedPoint = cameraPositionState.position.target
            val point = GeoPoint(selectedPoint.latitude, selectedPoint.longitude)
            selectedAddress = formatLocationCoordinates(point)
            isResolvingAddress = true
            selectedAddress = reverseGeocode(context, point)
            isResolvingAddress = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close map")
                    }
                    Text(
                        "Choose job location",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    )
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Map selection pin",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp).size(42.dp)
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                getDeviceLocation()
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Move to my location")
                    }
                }
                Text(
                    text = if (isResolvingAddress) "Finding address…" else selectedAddress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                locationMessage?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = {
                        onConfirm(
                            GeoPoint(selectedPoint.latitude, selectedPoint.longitude),
                            selectedAddress.ifBlank {
                                formatLocationCoordinates(GeoPoint(selectedPoint.latitude, selectedPoint.longitude))
                            }
                        )
                    },
                    enabled = !isResolvingAddress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Confirm location")
                }
            }
        }
    }
}

private fun formatLocationCoordinates(point: GeoPoint): String =
    String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)

private suspend fun reverseGeocode(context: android.content.Context, point: GeoPoint): String =
    withContext(Dispatchers.IO) {
        val fallback = formatLocationCoordinates(point)
        if (!Geocoder.isPresent()) return@withContext fallback
        try {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(point.latitude, point.longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
                ?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (_: IOException) {
            fallback
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

@Composable
private fun FormSectionHeader(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}
