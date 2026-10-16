package lk.ac.nibm.hdse.fixly_20.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.ui.screens.AcceptedJobsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.AvailableJobsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.CompleteProfileScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.CustomerTrackingScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.EmergencyScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.EmergencySosScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.HomeScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.JobBidsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.LoginScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.MyBookingsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.PostJobScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.ProviderTrackingScreen
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
    const val ACCEPTED_JOBS = "accepted_jobs"
    const val EMERGENCY = "emergency"
    const val EMERGENCY_SOS = "emergency_sos"
    const val JOB_BIDS = "job_bids/{jobId}"
    fun jobBids(jobId: String) = "job_bids/$jobId"
    const val PROVIDER_TRACKING = "provider_tracking/{jobId}"
    fun providerTracking(jobId: String) = "provider_tracking/$jobId"
    const val CUSTOMER_TRACKING = "customer_tracking/{jobId}"
    fun customerTracking(jobId: String) = "customer_tracking/$jobId"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.AUTH_GATE) {

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
                },
                onBack = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.COMPLETE_PROFILE) { inclusive = true } }
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
                onViewAcceptedJobs = { navController.navigate(Routes.ACCEPTED_JOBS) },
                onEmergency = { navController.navigate(Routes.EMERGENCY_SOS) }
            )
        }

        composable(Routes.EMERGENCY_SOS) {
            EmergencySosScreen(
                onBack = { navController.popBackStack() },
                onBrowseProviders = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.EMERGENCY) {
            EmergencyScreen(
                onBack = { navController.popBackStack() },
                onPostUrgentJob = { navController.navigate(Routes.POST_JOB) }
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
                onOpenJobBids = { jobId -> navController.navigate(Routes.jobBids(jobId)) },
                onTrackProvider = { jobId -> navController.navigate(Routes.customerTracking(jobId)) }
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

        composable(Routes.ACCEPTED_JOBS) {
            AcceptedJobsScreen(
                onBack = { navController.popBackStack() },
                onOpenJob = { jobId -> navController.navigate(Routes.providerTracking(jobId)) }
            )
        }

        composable(
            route = Routes.PROVIDER_TRACKING,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            ProviderTrackingScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() },
                onJobCompleted = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CUSTOMER_TRACKING,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            CustomerTrackingScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

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
