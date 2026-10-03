package lk.ac.nibm.hdse.fixly_20.data.remote

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Handles file uploads (images or PDFs) to Cloudinary using an UNSIGNED upload preset.
 * This is the "Remote Data Source / API" piece of the MAD syllabus requirement —
 * a plain REST API call, separate from Firebase.
 */
object CloudinaryUploader {

    private const val CLOUD_NAME = "rrsnpui6"       // Your Cloudinary cloud name
    private const val UPLOAD_PRESET = "fixly_preset" // Your Cloudinary unsigned upload preset

    private val client = OkHttpClient()

    /**
     * Uploads a file (image or PDF) picked via a content Uri.
     * Returns the secure_url of the uploaded file on success.
     */
    suspend fun uploadFile(context: Context, uri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Cloudinary's "auto" resource type accepts both images and raw files (PDFs)
                val url = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/auto/upload"

                // Copy the content:// Uri into a temp file OkHttp can read
                val tempFile = copyUriToTempFile(context, uri)

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .addFormDataPart(
                        "file",
                        tempFile.name,
                        tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    tempFile.delete()

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("Cloudinary upload failed: ${response.code}")
                        )
                    }

                    val body = response.body?.string() ?: ""
                    val secureUrl = JSONObject(body).getString("secure_url")
                    Result.success(secureUrl)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open file")
        val tempFile = File.createTempFile("upload_", null, context.cacheDir)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        return tempFile
    }
}
