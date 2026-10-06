package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.AccountType
import lk.ac.nibm.hdse.fixly_20.viewmodel.AuthViewModel

private val serviceCategoryOptions = listOf("Plumbing", "Electrical", "Cleaning", "Other")

/**
 * Shown once, right after a brand-new Google sign-in, to collect the details
 * Google doesn't give us: phone number, account type, and (for providers) category.
 */
@Composable
fun CompleteProfileScreen(
    onProfileCompleted: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current

    var phoneNumber by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf(AccountType.CUSTOMER) }
    val selectedCategories = remember { mutableStateMapOf<String, Boolean>() }
    var certificateUri by remember { mutableStateOf<Uri?>(null) }

    val uiState by viewModel.uiState.collectAsState()

    val certificatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> certificateUri = uri }

    LaunchedEffect(uiState.isProfileCompletionSuccessful) {
        if (uiState.isProfileCompletionSuccessful) onProfileCompleted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Just one more step", style = MaterialTheme.typography.headlineMedium)
        Text(
            "We need a couple more details to set up your Fixly account",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = phoneNumber, onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("I'm signing up as a:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = accountType == AccountType.CUSTOMER, onClick = { accountType = AccountType.CUSTOMER })
            Text("Customer — I want to book services")
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = accountType == AccountType.PROVIDER, onClick = { accountType = AccountType.PROVIDER })
            Text("Service Provider — I want to offer services")
        }

        if (accountType == AccountType.PROVIDER) {
            Text(
                "As a Provider, you can also book services yourself anytime.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("What services do you offer?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))

            serviceCategoryOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = selectedCategories[option] ?: false,
                        onCheckedChange = { checked -> selectedCategories[option] = checked }
                    )
                    Text(option)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { certificatePicker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (certificateUri == null) "Upload Certificate (optional)" else "Certificate Selected ✓")
            }
        }

        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val roles = if (accountType == AccountType.PROVIDER) {
                    listOf("customer", "provider")
                } else {
                    listOf("customer")
                }
                val categories = selectedCategories.filter { it.value }.keys.toList()
                viewModel.completeGoogleProfile(context, phoneNumber, roles, categories, certificateUri)
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Finish Setup")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
