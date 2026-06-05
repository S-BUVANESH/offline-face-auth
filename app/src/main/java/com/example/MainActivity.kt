package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.repository.DatalakeAuthRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RegistrationScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DatalakeAuthViewModel
import com.example.ui.viewmodel.DatalakeAuthViewModelFactory

// Simple, reliable state-machine navigation for the offline prototyping flow
sealed class Destination {
    object Splash : Destination()
    object Dashboard : Destination()
    object Registration : Destination()
    object Scanner : Destination()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Initialize Room local storage instance
                val context = this
                val appDatabase = remember { AppDatabase.getDatabase(context) }
                val authRepository = remember {
                    DatalakeAuthRepository(
                        personnelDao = appDatabase.personnelDao(),
                        checkInLogDao = appDatabase.checkInLogDao()
                    )
                }

                // Inject Repository into ViewModel using clean factory pattern
                val factory = remember { DatalakeAuthViewModelFactory(authRepository) }
                val authViewModel: DatalakeAuthViewModel = viewModel(factory = factory)

                var currentScreen by remember { mutableStateOf<Destination>(Destination.Splash) }
                var isSupervisorAuthenticated by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        when (currentScreen) {
                            is Destination.Splash -> {
                                SplashScreen(
                                    onInitializationComplete = {
                                        currentScreen = Destination.Dashboard
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            is Destination.Dashboard -> {
                                DashboardScreen(
                                    viewModel = authViewModel,
                                    isSupervisorAuthenticated = isSupervisorAuthenticated,
                                    onSupervisorAuthenticatedChange = { isSupervisorAuthenticated = it },
                                    onNavigateToRegistration = { currentScreen = Destination.Registration },
                                    onNavigateToScanner = { currentScreen = Destination.Scanner },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            is Destination.Registration -> {
                                RegistrationScreen(
                                    viewModel = authViewModel,
                                    onNavigateBack = {
                                        isSupervisorAuthenticated = false // Expire supervisor session immediately upon returning to main menu
                                        currentScreen = Destination.Dashboard
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            is Destination.Scanner -> {
                                ScannerScreen(
                                    viewModel = authViewModel,
                                    onNavigateBack = { currentScreen = Destination.Dashboard },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
