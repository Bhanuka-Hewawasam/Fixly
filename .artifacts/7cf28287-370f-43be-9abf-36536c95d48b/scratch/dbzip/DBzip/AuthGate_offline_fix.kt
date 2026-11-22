// In AppNavigation.kt, replace the WHOLE private `AuthGate` function (it's at the bottom of the file)
// with this one. Nothing else in the file changes, so it works with whichever AppNavigation
// version you currently have. No new imports are needed.

@Composable
private fun AuthGate(
    onGoToLogin: () -> Unit,
    onGoToHome: () -> Unit,
    onGoToCompleteProfile: () -> Unit
) {
    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onGoToLogin()
            return@LaunchedEffect
        }

        // Offline-safe: if Firestore can't be reached for the profile check, a signed-in user
        // still goes Home (the screens there read the saved local data) instead of crashing.
        val profileExists = try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .get()
                .await()
                .exists()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }

        if (profileExists) onGoToHome() else onGoToCompleteProfile()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
