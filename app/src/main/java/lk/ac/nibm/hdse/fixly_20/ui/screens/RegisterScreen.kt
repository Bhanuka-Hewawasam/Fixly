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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.AuthViewModel

private val serviceCategoryOptions = listOf("Plumbing", "Electrical", "Cleaning", "Other")

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current

    // --- Step control: 1 = account details, 2 = role + category ---
    var currentStep by remember { mutableIntStateOf(1) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var step1Error by remember { mutableStateOf<String?>(null) }

    var isCustomer by remember { mutableStateOf(true) }
    var isProvider by remember { mutableStateOf(false) }
    val selectedCategories = remember { mutableStateMapOf<String, Boolean>() }
    var certificateUri by remember { mutableStateOf<Uri?>(null) }

    val uiState by viewModel.uiState.collectAsState()

    val certificatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> certificateUri = uri }

    LaunchedEffect(uiState.isRegisterSuccessful) {
        if (uiState.isRegisterSuccessful) onRegisterSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Create Account", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = if (currentStep == 1) "Step 1 of 2 — Your details" else "Step 2 of 2 — How you'll use Fixly",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (currentStep == 1) {
            // ---------------- STEP 1: Account details ----------------
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = phoneNumber, onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            step1Error?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    step1Error = when {
                        name.isBlank() || email.isBlank() || phoneNumber.isBlank() || password.isBlank() ->
                            "Please fill in all fields"
                        password != confirmPassword -> "Passwords don't match"
                        else -> null
                    }
                    if (step1Error == null) currentStep = 2
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next")
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onNavigateToLogin) { Text("Already have an account? Login") }

        } else {
            // ---------------- STEP 2: Role + category ----------------
            Text("I want to:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isCustomer, onCheckedChange = { isCustomer = it })
                Text("Book services (Customer)")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isProvider, onCheckedChange = { isProvider = it })
                Text("Offer services (Provider)")
            }

            if (isProvider) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "What services do you offer?",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "This decides which job posts you'll see — an electrician won't see plumbing jobs.",
                    style = MaterialTheme.typography.bodySmall
                )
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
                    Text(
                        if (certificateUri == null) "Upload Certificate (image or PDF, optional)"
                        else "Certificate Selected ✓"
                    )
                }
                Text(
                    "Providers with a verified certificate get a trust badge",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { currentStep = 1 },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val roles = buildList {
                            if (isCustomer) add("customer")
                            if (isProvider) add("provider")
                        }
                        val categories = selectedCategories.filter { it.value }.keys.toList()
                        viewModel.register(
                            context, name, email, phoneNumber, password, confirmPassword,
                            roles, categories, certificateUri
                        )
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Register")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
