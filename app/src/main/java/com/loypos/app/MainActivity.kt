package com.loypos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loypos.app.ui.AuthStage
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.TransactionViewModel
import com.loypos.app.ui.screens.AuthGateScreen
import com.loypos.app.ui.screens.MainScreen
import com.loypos.app.ui.screens.SplashScreen
import com.loypos.app.ui.theme.LoyPosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoyPosTheme {
                val vm: LoyPosViewModel = viewModel()
                val txVm: TransactionViewModel = viewModel()
                val session by vm.session.collectAsStateWithLifecycle()
                val data by vm.dataState.collectAsStateWithLifecycle()

                LaunchedEffect(session.authStage) {
                    if (session.authStage == AuthStage.LOGGED_OUT) {
                        txVm.clearCart()
                    }
                }

                when (session.authStage) {
                    AuthStage.SPLASH -> SplashScreen()
                    AuthStage.LOGGED_OUT -> AuthGateScreen(
                        viewModel = vm,
                        hasAnyUser = session.hasAnyUser,
                    )
                    AuthStage.LOGGED_IN -> MainScreen(
                        viewModel = vm,
                        transactionViewModel = txVm,
                        session = session,
                        data = data,
                    )
                }
            }
        }
    }
}
