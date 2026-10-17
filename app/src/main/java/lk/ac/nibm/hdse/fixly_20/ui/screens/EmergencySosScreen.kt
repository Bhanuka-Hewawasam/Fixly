package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.remote.CloudinaryUploader
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlertPolicy
import lk.ac.nibm.hdse.fixly_20.model.EmergencyLocationPolicy
import lk.ac.nibm.hdse.fixly_20.model.EmergencyResponse
import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import lk.ac.nibm.hdse.fixly_20.util.dialNumber
import lk.ac.nibm.hdse.fixly_20.util.openInMaps
import lk.ac.nibm.hdse.fixly_20.viewmodel.EmergencySosViewModel

private val sosCategories = listOf(
    "Vehicle Mechanic",
    "Electrician",
    "Plumber",
    "AC Technician",
    "Computer Technician",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // permission is checked in onSendClicked() before any location call
@Composable
fun EmergencySosScreen(
    onBack: () -> Unit,
    onBrowseProviders: () -> Unit,
    viewModel: EmergencySosViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val uiState by viewModel.uiState.collectAsState()
    var dismissedAlertId by remember { mutableStateOf<String?>(null) }
    val myAlert = uiState.myAlert?.takeIf {
        it.alertId != dismissedAlertId && EmergencyAlertPolicy.isLiveForCustomer(it.status)
    }

    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Any Available Provider") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    var isLocating by remember { mutableStateOf(false) }
    var locationDisabled by remember { mutableStateOf(false) }
    var sendAfterLocation by remember { mutableStateOf(false) }

    fun fetchCurrentLocation() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!EmergencyLocationPolicy.canReadCurrentLocation(hasPermission, isLocationEnabled)) {
            locationDisabled = !isLocationEnabled
            viewModel.showError(
                if (!hasPermission) "Location permission is needed to send an emergency alert."
                else "Turn on Location services to share your current position."
            )
            return
        }

        locationDisabled = false
        isLocating = true
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                isLocating = false
                if (location != null) {
                    val point = GeoPoint(location.latitude, location.longitude)
                    viewModel.setCustomerLocation(point)
                    if (sendAfterLocation) {
                        coroutineScope.launch {
                            var finalNote = note
                            if (selectedImageUri != null) {
                                isUploadingPhoto = true
                                val uploadResult = CloudinaryUploader.uploadFile(context, selectedImageUri!!)
                                isUploadingPhoto = false
                                if (uploadResult.isSuccess) {
                                    val imageUrl = uploadResult.getOrNull()
                                    if (!imageUrl.isNullOrBlank()) {
                                        finalNote = if (finalNote.isNotBlank()) "$finalNote\n[Photo: $imageUrl]" else "[Photo: $imageUrl]"
                                    }
                                } else {
                                    viewModel.showError("Failed to upload photo. Sending emergency request without photo.")
                                }
                            }
                            val serviceCategory = if (category == "Any Available Provider") {
                                "Any"
                            } else category
                            viewModel.sendAlert(point, finalNote, serviceCategory)
                        }
                    }
                    sendAfterLocation = false
                } else {
                    viewModel.showError("Couldn't get a current location fix. Check GPS and try again.")
                }
            }
            .addOnFailureListener {
                isLocating = false
                viewModel.showError("Location could not be read. Check your connection and GPS, then retry.")
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchCurrentLocation()
        else viewModel.showError("Location permission is needed to send an emergency alert.")
    }

    fun requestCurrentLocation(sendAlertAfterFix: Boolean) {
        sendAfterLocation = sendAlertAfterFix
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) fetchCurrentLocation()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "Emergency",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.padding(start = 12.dp).size(38.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF334155), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ElectricBolt,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "24/7 Live",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading || isUploadingPhoto -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        if (isUploadingPhoto) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Uploading photo evidence...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            myAlert != null -> WaitingContent(
                alert = myAlert,
                responses = uiState.responses,
                paddingValues = padding,
                onCall = { phone -> context.dialNumber(phone) },
                onOpenMaps = { lat, lon, label -> context.openInMaps(lat, lon, label) },
                onClose = viewModel::closeAlert,
                onBrowseProviders = onBrowseProviders,
                onNewRequest = { dismissedAlertId = myAlert.alertId },
                errorMessage = uiState.errorMessage
            )

            else -> SendAlertContent(
                paddingValues = padding,
                isBusy = isLocating || uiState.isSending,
                note = note,
                onNoteChange = { note = it.take(500) },
                category = category,
                onCategoryChange = { category = it },
                selectedImageUri = selectedImageUri,
                onPickImage = { imagePickerLauncher.launch("image/*") },
                onRemoveImage = { selectedImageUri = null },
                errorMessage = uiState.errorMessage,
                customerLocation = uiState.customerLocation,
                locationDisabled = locationDisabled,
                isLocating = isLocating,
                onFindLocation = { requestCurrentLocation(false) },
                onOpenLocationSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                onSend = { requestCurrentLocation(true) },
                onBrowseProviders = onBrowseProviders
            )
        }
    }
}

@Composable
private fun SendAlertContent(
    paddingValues: PaddingValues,
    isBusy: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    selectedImageUri: Uri?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    errorMessage: String?,
    customerLocation: GeoPoint?,
    locationDisabled: Boolean,
    isLocating: Boolean,
    onFindLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSend: () -> Unit,
    onBrowseProviders: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- URGENT ALERT BANNER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFE11D48), Color(0xFFBE123C))
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Urgent help, when you need it",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )

                        Text(
                            text = "We'll share your real-time location and instantly notify nearby available emergency providers.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF991B1B).copy(alpha = 0.6f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFFCD34D),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Location verified on request",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Text("Choose the help you need", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sosCategories.chunked(2).forEach { rowCategories ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCategories.forEach { option ->
                        val selected = category == option
                        val icon = when (option.lowercase().trim()) {
                            "vehicle mechanic" -> Icons.Outlined.Build
                            "electrician" -> Icons.Outlined.ElectricBolt
                            "plumber" -> Icons.Outlined.WaterDrop
                            "ac technician" -> Icons.Outlined.AcUnit
                            "computer technician", "computer tech" -> Icons.Outlined.Computer
                            else -> Icons.Outlined.MoreHoriz
                        }

                        Surface(
                            onClick = { onCategoryChange(option) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // Any Available Provider (Full Width Highlighted Red Card below grid)
            val isAnySelected = category == "Any Available Provider" || category == "Any"
            Card(
                onClick = { onCategoryChange("Any Available Provider") },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEF4444)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = if (isAnySelected) BorderStroke(2.dp, Color.White) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Any Available Provider",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Fastest dispatch response",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- OPTIONAL NOTES BOX ("What happened?") WITH PHOTO ATTACHMENT ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "What happened?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF334155))
                        )
                        Text(
                            text = "(optional)",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                        )
                    }
                    Text(
                        text = "${note.length}/500",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    placeholder = {
                        Text(
                            text = "Describe your emergency situation briefly...",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 12.sp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Photo attachment preview or button
                if (selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Attached emergency photo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = onRemoveImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onPickImage,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF8FAFC),
                            contentColor = Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attach Photo Evidence",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onFindLocation,
            enabled = !isLocating,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFF334155)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isLocating) "Getting location…" else "Get my current location",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
        }

        customerLocation?.let { point ->
            val position = LatLng(point.latitude, point.longitude)
            val cameraState = rememberCameraPositionState {
                this.position = CameraPosition.fromLatLngZoom(position, 15f)
            }
            LaunchedEffect(position) {
                cameraState.position = CameraPosition.fromLatLngZoom(position, 15f)
            }
            Text("Your current location", style = MaterialTheme.typography.titleMedium)
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp)),
                cameraPositionState = cameraState
            ) {
                Marker(
                    state = MarkerState(position = position),
                    title = "Emergency location",
                    contentDescription = "Your emergency location"
                )
            }
        }

        if (locationDisabled) {
            OutlinedButton(
                onClick = onOpenLocationSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Turn on Location services")
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // Primary Send Emergency Request Button
        Button(
            onClick = onSend,
            enabled = !isBusy,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFDC2626), Color(0xFFE11D48))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Finding your location and sending…",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.5.sp
                        )
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send Emergency Request",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 13.5.sp
                            )
                        )
                    }
                }
            }
        }

        Text(
            "Your location is shared only to help providers reach you.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = onBrowseProviders,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF475569)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "Browse nearby providers instead",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            )
        }
    }
}

@Composable
private fun WaitingContent(
    alert: EmergencyAlert,
    responses: List<EmergencyResponse>,
    paddingValues: PaddingValues,
    onCall: (String) -> Unit,
    onOpenMaps: (Double, Double, String) -> Unit,
    onClose: () -> Unit,
    onBrowseProviders: () -> Unit,
    onNewRequest: () -> Unit,
    errorMessage: String?
) {
    val isAccepted = alert.status == "ACCEPTED"
    val isActive = alert.status == "ACTIVE"
    val terminalStatus = alert.status in listOf("CANCELLED", "EXPIRED", "CLOSED")
    var showCloseConfirmation by remember(alert.alertId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (terminalStatus) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (alert.status) {
                            "CANCELLED" -> "Request cancelled"
                            "EXPIRED" -> "Request expired"
                            else -> "Request closed"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("This request is no longer active. You can send a new request if you still need help.")
                }
            }
        } else if (isAccepted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Emergency",
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Provider accepted your request",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Help needed: ${alert.category}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (alert.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Note: ${alert.note}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Name: ${alert.acceptedProviderName ?: "Provider"}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Phone: ${alert.acceptedProviderPhone ?: "N/A"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!alert.acceptedProviderPhone.isNullOrBlank()) {
                            Button(
                                onClick = { onCall(alert.acceptedProviderPhone) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📞 Call Provider")
                            }
                        }
                        if (alert.location != null) {
                            OutlinedButton(
                                onClick = {
                                    onOpenMaps(alert.location.latitude, alert.location.longitude, alert.acceptedProviderName ?: "Provider")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🗺️ Open in Maps")
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Searching for nearby providers…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your emergency request is active. Available providers matching your service will be notified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (alert.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Your note: ${alert.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Provider responses (${responses.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (responses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Waiting for a provider to accept...", textAlign = TextAlign.Center)
                    }
                }
            } else {
                responses.forEach { response ->
                    ResponseCard(response = response, onCall = { onCall(response.providerPhone) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onBrowseProviders,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Browse nearby providers & call manually (Fallback)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when {
            terminalStatus -> Button(onClick = onNewRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Send another emergency request")
            }
            isActive -> OutlinedButton(
                onClick = { showCloseConfirmation = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel emergency request") }
            isAccepted -> OutlinedButton(
                onClick = { showCloseConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Close request") }
        }
    }

    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmation = false },
            title = { Text(if (isAccepted) "Close this request?" else "Cancel this emergency request?") },
            text = {
                Text(
                    if (isAccepted) "This will mark the accepted request as closed."
                    else "Nearby providers will no longer be able to accept this request."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirmation = false
                        onClose()
                    }
                ) { Text(if (isAccepted) "Close request" else "Cancel request") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirmation = false }) { Text("Keep request") }
            }
        )
    }
}

@Composable
private fun ResponseCard(response: EmergencyResponse, onCall: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(response.providerName, style = MaterialTheme.typography.titleMedium)
            Text(
                formatRating(response.providerRating, response.providerReviewCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            response.distanceKm?.let { distance ->
                Text(
                    "📍 ${String.format(java.util.Locale.getDefault(), "%.1f", distance)} km away — on the way",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onCall,
                enabled = response.providerPhone.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (response.providerPhone.isBlank()) "No phone number"
                    else "📞 Call ${response.providerName}"
                )
            }
        }
    }
}
