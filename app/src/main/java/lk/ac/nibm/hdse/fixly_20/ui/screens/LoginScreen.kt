package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyLogo
import lk.ac.nibm.hdse.fixly_20.ui.components.GoogleIcon
import lk.ac.nibm.hdse.fixly_20.ui.components.LogoSize
import lk.ac.nibm.hdse.fixly_20.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNeedsProfileCompletion: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var passwordResetSecondsRemaining by remember { mutableIntStateOf(0) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.passwordResetCooldownUntil) {
        while (true) {
            val remainingMillis = uiState.passwordResetCooldownUntil - SystemClock.elapsedRealtime()
            passwordResetSecondsRemaining = ((remainingMillis + 999L) / 1000L).toInt().coerceAtLeast(0)
            if (remainingMillis <= 0L) break
            delay(250L)
        }
    }

    // Google Sign-In client setup using the exact Firebase Web Client ID
    val googleSignInClient: GoogleSignInClient = remember {
        val webClientId = "18940704850-nimu7vjb258tamkb2s4ekhecls74sg8g.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    var googleError by remember { mutableStateOf<String?>(null) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("GoogleSignIn", "Result code: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                Log.d("GoogleSignIn", "Got idToken: ${idToken != null}")
                if (idToken != null) {
                    googleError = null
                    viewModel.signInWithGoogle(idToken)
                } else {
                    Log.e("GoogleSignIn", "idToken is NULL!")
                    googleError = "Google Sign-In error: ID Token is null."
                }
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "ApiException code: ${e.statusCode}, message: ${e.message}")
                googleError = "Google Sign-In failed (Code ${e.statusCode}): Check SHA-1 in Firebase Console."
            }
        } else {
            googleError = "Google Sign-In cancelled or failed (Result: ${result.resultCode})"
        }
    }

    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) onLoginSuccess()
    }
    LaunchedEffect(uiState.needsProfileCompletion) {
        if (uiState.needsProfileCompletion) onNeedsProfileCompletion()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. BRAND HEADER WITH LOGO
            FixlyLogo(
                size = LogoSize.LARGE,
                showTagline = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Home repair services, on demand",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(36.dp))

            // 2. INPUT FIELDS
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (uiState.errorMessage != null) viewModel.clearError()
                },
                label = { Text("Email Address") },
                placeholder = { Text("name@example.com") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = "Email Icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (uiState.errorMessage != null) viewModel.clearError()
                },
                label = { Text("Password") },
                placeholder = { Text("Enter your password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock Icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.login(email, password)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        resetEmail = email
                        viewModel.clearPasswordResetResult()
                        showForgotPassword = true
                    }
                ) {
                    Text("Forgot password?")
                }
            }

            // 3. ERROR MESSAGES IN A STYLISH CARD
            val activeError = uiState.errorMessage ?: googleError
            AnimatedVisibility(
                visible = activeError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeError?.let { errorText ->
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
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. PRIMARY LOGIN BUTTON
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login(email, password)
                },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp))
            ) {
                if (uiState.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.25.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Signing in...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Login",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. "OR" DIVIDER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. GOOGLE SIGN-IN BUTTON WITH GOOGLE LOGO
            OutlinedButton(
                onClick = {
                    try {
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleLauncher.launch(googleSignInClient.signInIntent)
                        }
                    } catch (e: Exception) {
                        googleLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        GoogleIcon(size = 22.dp)
                    }
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 7. REGISTER FOOTER LINK
            Row(
                modifier = Modifier
                    .clickable { onNavigateToRegister() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account? ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "Register",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showForgotPassword) {
        val resetSucceeded = uiState.passwordResetMessage != null
        AlertDialog(
            onDismissRequest = {
                if (!resetSucceeded) showForgotPassword = false
            },
            title = { Text("Reset password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = {
                            resetEmail = it
                            if (uiState.passwordResetError != null) viewModel.clearPasswordResetResult()
                        },
                        enabled = !uiState.isPasswordResetLoading && !resetSucceeded,
                        label = { Text("Email address") },
                        placeholder = { Text("name@example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    uiState.passwordResetError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    uiState.passwordResetMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (resetSucceeded && passwordResetSecondsRemaining > 0) {
                        TextButton(onClick = {}, enabled = false) {
                            Text("Wait ${passwordResetSecondsRemaining}s")
                        }
                    } else if (!resetSucceeded) {
                        TextButton(
                            onClick = { viewModel.sendPasswordResetEmail(resetEmail) },
                            enabled = !uiState.isPasswordResetLoading && passwordResetSecondsRemaining == 0
                        ) {
                            when {
                                uiState.isPasswordResetLoading -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                passwordResetSecondsRemaining > 0 -> Text("Wait ${passwordResetSecondsRemaining}s")
                                else -> Text("Send reset link")
                            }
                        }
                    }
                    if (resetSucceeded) {
                        TextButton(
                            onClick = {
                                viewModel.clearPasswordResetResult()
                                showForgotPassword = false
                            }
                        ) {
                            Text("OK")
                        }
                    }
                }
            },
            dismissButton = if (resetSucceeded) null else ({
                TextButton(
                    onClick = { showForgotPassword = false },
                    enabled = !uiState.isPasswordResetLoading
                ) {
                    Text("Cancel")
                }
            })
        )
    }
}
