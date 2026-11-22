package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.AccountType
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyLogo
import lk.ac.nibm.hdse.fixly_20.ui.components.LogoSize
import lk.ac.nibm.hdse.fixly_20.ui.components.RoleSelectionCard
import lk.ac.nibm.hdse.fixly_20.viewmodel.AuthViewModel

import lk.ac.nibm.hdse.fixly_20.util.ServiceCategories

private val serviceCategoryOptions = ServiceCategories.canonicalList

/**
 * Shown once, right after a brand-new Google sign-in, to collect details
 * Google doesn't provide: phone number, account type, and (for providers) service categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(
    onProfileCompleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current

    var phoneNumber by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf(AccountType.CUSTOMER) }
    val selectedCategories = remember { mutableStateMapOf<String, Boolean>() }
    var certificateUri by remember { mutableStateOf<Uri?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val uiState by viewModel.uiState.collectAsState()

    val certificatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> certificateUri = uri }

    LaunchedEffect(uiState.isProfileCompletionSuccessful) {
        if (uiState.isProfileCompletionSuccessful) onProfileCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // BRAND LOGO
                FixlyLogo(size = LogoSize.MEDIUM, showTagline = false)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Complete Profile",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Just a few more details to set up your account",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PHONE NUMBER INPUT
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                            phoneNumber = it
                            localError = null
                        }
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("0712345678") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "I'm signing up as a:",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ROLE CARDS
                RoleSelectionCard(
                    title = "Customer",
                    description = "Book trusted home repair services",
                    icon = Icons.Outlined.Home,
                    isSelected = accountType == AccountType.CUSTOMER,
                    onSelect = {
                        accountType = AccountType.CUSTOMER
                        localError = null
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                RoleSelectionCard(
                    title = "Service Provider",
                    description = "Offer your repair and maintenance services",
                    icon = Icons.Outlined.Build,
                    isSelected = accountType == AccountType.PROVIDER,
                    onSelect = {
                        accountType = AccountType.PROVIDER
                        localError = null
                    }
                )

                // PROVIDER CATEGORIES
                AnimatedVisibility(
                    visible = accountType == AccountType.PROVIDER,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "As a Provider, you can also book services yourself anytime.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "What services do you offer?",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            serviceCategoryOptions.chunked(2).forEach { rowOptions ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowOptions.forEach { option ->
                                        val isChecked = selectedCategories[option] ?: false
                                        FilterChip(
                                            selected = isChecked,
                                            onClick = {
                                                selectedCategories[option] = !isChecked
                                                localError = null
                                            },
                                            label = { Text(option) },
                                            leadingIcon = if (isChecked) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { certificatePicker.launch(arrayOf("image/*", "application/pdf")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (certificateUri == null) Icons.Outlined.FileUpload else Icons.Outlined.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (certificateUri == null) "Upload Certificate (Optional)" else "Certificate Selected ✓")
                        }
                    }
                }

                // ERROR BADGE
                val activeError = localError ?: uiState.errorMessage
                AnimatedVisibility(
                    visible = activeError != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    activeError?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        if (phoneNumber.isBlank()) {
                            localError = "Please enter your phone number"
                            return@Button
                        }
                        if (phoneNumber.length != 10) {
                            localError = "Phone number must be exactly 10 digits"
                            return@Button
                        }
                        val categories = selectedCategories.filter { it.value }.keys.toList()
                        if (accountType == AccountType.PROVIDER && categories.isEmpty()) {
                            localError = "Please select at least one service category"
                            return@Button
                        }
                        localError = null

                        val roles = if (accountType == AccountType.PROVIDER) {
                            listOf("customer", "provider")
                        } else {
                            listOf("customer")
                        }

                        viewModel.completeGoogleProfile(context, phoneNumber, roles, categories, certificateUri)
                    },
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp))
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Finish Setup",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
