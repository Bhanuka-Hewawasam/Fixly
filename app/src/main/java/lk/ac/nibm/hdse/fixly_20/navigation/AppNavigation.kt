package lk.ac.nibm.hdse.fixly_20.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.ui.screens.AvailableJobsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.CompleteProfileScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.HomeScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.JobBidsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.LoginScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.MyBookingsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.PostJobScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.RegisterScreen

object Routes {
    const val AUTH_GATE = "auth_gate"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val COMPLETE_PROFILE = "complete_profile"
    const val HOME = "home"
    const val POST_JOB = "post_job"
    const val MY_BOOKINGS = "my_bookings"
    const val AVAILABLE_JOBS = "available_jobs"
    const val ACCEPTED_JOBS = "accepted_jobs"      // TODO: next step
    const val JOB_BIDS = "job_bids/{jobId}"
    fun jobBids(jobId: String) = "job_bids/$jobId"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.AUTH_GATE) {

        // Decides, once at app start, whether to show Login, Home, or Complete Profile —
        // this is what makes the session "stay logged in until logout".
        composable(Routes.AUTH_GATE) {
            AuthGate(
                onGoToLogin = {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.AUTH_GATE) { inclusive = true } }
                },
                onGoToHome = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.AUTH_GATE) { inclusive = true } }
                },
                onGoToCompleteProfile = {
                    navController.navigate(Routes.COMPLETE_PROFILE) { popUpTo(Routes.AUTH_GATE) { inclusive = true } }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNeedsProfileCompletion = {
                    navController.navigate(Routes.COMPLETE_PROFILE) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.COMPLETE_PROFILE) {
            CompleteProfileScreen(
                onProfileCompleted = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.COMPLETE_PROFILE) { inclusive = true } }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onPostJob = { navController.navigate(Routes.POST_JOB) },
                onViewBookings = { navController.navigate(Routes.MY_BOOKINGS) },
                onViewAvailableJobs = { navController.navigate(Routes.AVAILABLE_JOBS) },
                onViewAcceptedJobs = { navController.navigate(Routes.ACCEPTED_JOBS) }
            )
        }

        composable(Routes.POST_JOB) {
            PostJobScreen(
                onJobPosted = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.AVAILABLE_JOBS) {
            AvailableJobsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MY_BOOKINGS) {
            MyBookingsScreen(
                onBack = { navController.popBackStack() },
                onOpenJobBids = { jobId -> navController.navigate(Routes.jobBids(jobId)) }
            )
        }

        composable(
            route = Routes.JOB_BIDS,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            JobBidsScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() },
                onBidSelected = { navController.popBackStack() }
            )
        }

        // --- Placeholder — built in upcoming step ---
        composable(Routes.ACCEPTED_JOBS) { Text("Accepted Jobs screen — coming next") }
    }
}

/**
 * Runs once at app launch: checks if a Firebase session already exists.
 * - No session -> Login
 * - Session exists but no Firestore profile yet (e.g. Google sign-in interrupted) -> Complete Profile
 * - Session exists and profile exists -> Home
 */
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

        val doc = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .get()
            .await()

        if (doc.exists()) onGoToHome() else onGoToCompleteProfile()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
