package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Shows one work-evidence photo.
 * - Provider screen: pass onTakePhoto -> shows a "Take Photo / Retake" button.
 * - Customer screen: leave onTakePhoto null -> read-only view.
 */
@Composable
fun EvidencePhotoCard(
    title: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    isUploading: Boolean = false,
    onTakePhoto: (() -> Unit)? = null
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            when {
                isUploading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Uploading photo...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                photoUrl != null -> {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (onTakePhoto != null) "No photo yet" else "Not provided",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (onTakePhoto != null && !isUploading) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                    Text(if (photoUrl == null) "📷 Take Photo" else "Retake Photo")
                }
            }
        }
    }
}
