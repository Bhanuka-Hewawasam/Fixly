package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.ui.components.EvidencePhotoCard
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderTrackingViewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderTrackingViewModelFactory
import java.io.File

/** Creates an empty temp .jpg in the cache folder and returns a shareable content:// Uri for the camera app. */
private fun createEvidenceImageUri(context: Context): Uri {
    val folder = File(context.cacheDir, "evidence").apply { mkdirs() }
    val file = File.createTempFile("evidence_", ".jpg", folder)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderTrackingScreen(
    jobId: String,
    onBack: () -> Unit,
    onJobCompleted: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProviderTrackingViewModel = viewModel(factory = ProviderTrackingViewModelFactory(jobId))
    val uiState by viewModel.uiState.collectAsState()

    // ---------- GPS sharing (unchanged) ----------
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startSharingLocation(context) }

    fun toggleSharing() {
        if (uiState.isSharingLocation) {
            viewModel.stopSharingLocation()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) viewModel.startSharingLocation(context)
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ---------- Camera evidence ----------
    // rememberSaveable: the phone may recreate the activity while the camera app is open
    var pendingPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingPhotoType by rememberSaveable { mutableStateOf("after") }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            viewModel.uploadEvidence(context.applicationContext, uri, pendingPhotoType)
        }
    }

    fun takePhoto(type: String) {
        val uri = createEvidenceImageUri(context)
        pendingPhotoType = type
        pendingPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(uiState.job?.status) {
        if (uiState.job?.status == "Completed") {
            viewModel.stopSharingLocation()
            onJobCompleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Tracking") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        val job = uiState.job

        if (job == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(job.category, style = MaterialTheme.typography.headlineSmall)
            Text(job.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("📍 ${job.address}", style = MaterialTheme.typography.bodySmall)
            Text("Customer: ${job.customerName}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = { toggleSharing() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (uiState.isSharingLocation) "Stop Sharing Location" else "Start Sharing Location")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (job.status == "Assigned") {
                Button(onClick = { viewModel.markInProgress() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mark as In Progress")
                }
            }

            // ---------- Work evidence section ----------
            Spacer(modifier = Modifier.height(20.dp))
            Text("Work Evidence", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            EvidencePhotoCard(
                title = "Before photo (optional)",
                photoUrl = job.beforePhotoUrl,
                isUploading = uiState.uploadingType == "before",
                onTakePhoto = { takePhoto("before") }
            )

            if (job.status == "In Progress") {
                Spacer(modifier = Modifier.height(12.dp))

                EvidencePhotoCard(
                    title = "After photo (required to complete)",
                    photoUrl = job.afterPhotoUrl,
                    isUploading = uiState.uploadingType == "after",
                    onTakePhoto = { takePhoto("after") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                val canComplete = !job.afterPhotoUrl.isNullOrBlank() && uiState.uploadingType == null

                Button(
                    onClick = { viewModel.markCompleted() },
                    enabled = canComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as Completed")
                }

                if (!canComplete && job.afterPhotoUrl.isNullOrBlank()) {
                    Text(
                        "Take the after photo to enable completion.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Keep this screen open while sharing your location — the customer can see it live.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
