package lk.ac.nibm.hdse.fixly_20.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import lk.ac.nibm.hdse.fixly_20.ui.screens.AvailableJobsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.HomeScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.JobBidsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.LoginScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.MyBookingsScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.PostJobScreen
import lk.ac.nibm.hdse.fixly_20.ui.screens.RegisterScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
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
    NavHost(navController = navController, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
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
