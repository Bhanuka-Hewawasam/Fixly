package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
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
import java.util.Calendar
import java.util.Locale

private val predefinedCategories = listOf("Plumbing", "Electrical", "Cleaning", "Carpentry", "Painting", "AC Repair", "Gardening", "Other")

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

    var category by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
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

    // Multiple image picker for problem photos
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

    val filteredCategories = predefinedCategories.filter { it.contains(category, ignoreCase = true) }

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
            // --- Auto-suggest Category Dropdown ---
            ExposedDropdownMenuBox(
                expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {
                        category = it
                        categoryExpanded = true
                    },
                    label = { Text("Service Category (Type to search)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded && filteredCategories.isNotEmpty(),
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    filteredCategories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                category = option
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Describe the problem") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Multiple Image Upload for Problem Photos ---
            OutlinedButton(
                onClick = { multiImagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (selectedImages.isEmpty()) "Attach Photos of Problem (Optional, Multiple)"
                    else "${selectedImages.size} Photo(s) Selected ✓"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Date & Time Pickers ---
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = preferredDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { datePickerDialog.show() },
                    enabled = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = preferredTime,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time") },
                    placeholder = { Text("HH:MM AM/PM") },
                    trailingIcon = {
                        IconButton(onClick = { timePickerDialog.show() }) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Pick Time")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { timePickerDialog.show() },
                    enabled = false
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address (Optional if GPS captured)") },
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
                    viewModel.postJob(context, category, description, preferredDate, preferredTime, isEmergency, address, selectedImages)
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
