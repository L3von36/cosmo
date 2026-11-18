package com.loypos.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loypos.app.core.LowStockNotifier
import com.loypos.app.ui.AppWorkspace
import com.loypos.app.ui.AuthStage
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.TransactionViewModel
import com.loypos.app.ui.screens.AuthGateScreen
import com.loypos.app.ui.screens.BackOfficeAuthScreen
import com.loypos.app.ui.screens.BackOfficeMainScreen
import com.loypos.app.ui.screens.MainScreen
import com.loypos.app.ui.screens.SplashScreen
import com.loypos.app.ui.theme.LoyPosTheme
import com.loypos.app.ui.theme.UiScale

class MainActivity : ComponentActivity() {
    private val vm: LoyPosViewModel by viewModels()
    private val txVm: TransactionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(LowStockNotifier.EXTRA_OPEN_LOW_STOCK, false) == true) {
            vm.triggerLowStockNavigation()
        }
        setContent {
            // Global zoom: scale density (dp) and fontScale (sp) together so the
            // whole app — cards, buttons, fonts — grows or shrinks like a browser
            // zoom. The value is changed live from Settings → Appearance.
            val baseDensity = LocalDensity.current
            val uiScale = UiScale.scale
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density * uiScale,
                    fontScale = baseDensity.fontScale * uiScale,
                ),
            ) {
                LoyPosTheme {
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
                            hasAnyBackOfficeUser = session.hasAnyBackOfficeUser,
                            initialWorkspace = session.activeWorkspace,
                            lastUserEmail = session.lastUserEmail,
                            lastBackOfficeEmail = session.lastBackOfficeEmail,
                        )
                        AuthStage.LOGGED_IN -> {
                            when (session.activeWorkspace) {
                                AppWorkspace.BACK_OFFICE -> {
                                    if (session.backOfficeUser == null) {
                                        BackOfficeAuthScreen(
                                            viewModel = vm,
                                            hasAnyBackOfficeUser = session.hasAnyBackOfficeUser,
                                            canReturnToPos = session.currentUser != null,
                                            storeName = data.settings.storeName,
                                            lastBackOfficeEmail = session.lastBackOfficeEmail,
                                        )
                                    } else {
                                        BackOfficeMainScreen(
                                            viewModel = vm,
                                            session = session,
                                            data = data,
                                        )
                                    }
                                }
                                AppWorkspace.POS -> {
                                    if (session.currentUser != null) {
                                        MainScreen(
                                            viewModel = vm,
                                            transactionViewModel = txVm,
                                            session = session,
                                            data = data,
                                        )
                                    } else {
                                        AuthGateScreen(
                                            viewModel = vm,
                                            hasAnyUser = session.hasAnyUser,
                                            hasAnyBackOfficeUser = session.hasAnyBackOfficeUser,
                                            initialWorkspace = AppWorkspace.POS,
                                            lastUserEmail = session.lastUserEmail,
                                            lastBackOfficeEmail = session.lastBackOfficeEmail,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(LowStockNotifier.EXTRA_OPEN_LOW_STOCK, false)) {
            vm.triggerLowStockNavigation()
        }
    }
}
