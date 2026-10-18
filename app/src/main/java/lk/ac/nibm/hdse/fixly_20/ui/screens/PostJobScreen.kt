package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.ui.components.CategoryCard
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyPrimaryButton
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlySecondaryButton
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyTextField
import lk.ac.nibm.hdse.fixly_20.ui.theme.FixlyEmergency
import lk.ac.nibm.hdse.fixly_20.ui.theme.FixlyEmergencyContainer
import lk.ac.nibm.hdse.fixly_20.viewmodel.PostJobViewModel
import java.util.Calendar
import java.util.Locale

import lk.ac.nibm.hdse.fixly_20.util.ServiceCategories

private val categories = ServiceCategories.canonicalList

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

    var selectedCategory by remember { mutableStateOf("Plumbing") }
    var description by remember { mutableStateOf("") }
    var preferredDate by remember { mutableStateOf("") }
    var preferredTime by remember { mutableStateOf("") }
    var isEmergency by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

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
                                address,
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

            OutlinedButton(
                onClick = { requestLocation() },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (uiState.capturedLocation != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (uiState.capturedLocation != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    when {
                        uiState.isFetchingLocation -> {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Detecting GPS position...")
                        }
                        uiState.capturedLocation != null -> {
                            Icon(Icons.Default.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GPS Captured (${String.format(Locale.US, "%.4f, %.4f", uiState.capturedLocation?.latitude, uiState.capturedLocation?.longitude)}) ✓",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        else -> {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Current GPS Location")
                        }
                    }
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
