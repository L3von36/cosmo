package com.loypos.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.loypos.app.ui.LoyPosDataState
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.UiSessionState
import com.loypos.app.ui.navigation.AccountRoute
import com.loypos.app.ui.navigation.CartRoute
import com.loypos.app.ui.navigation.CheckoutRoute
import com.loypos.app.ui.navigation.CustomersRoute
import com.loypos.app.ui.navigation.DashboardRoute
import com.loypos.app.ui.navigation.InventoryRoute
import com.loypos.app.ui.navigation.PaymentRoute
import com.loypos.app.ui.navigation.ReceiptDetailRoute
import com.loypos.app.ui.navigation.ReceiptSuccessRoute
import com.loypos.app.ui.navigation.ReceiptsRoute
import com.loypos.app.ui.navigation.ReportsRoute
import com.loypos.app.ui.navigation.SettingsRoute
import com.loypos.app.ui.navigation.ShiftRoute
import com.loypos.app.ui.navigation.StockRoute
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenLight
import com.loypos.app.ui.theme.LoySurface
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val tag: String,
)

@Composable
fun MainScreen(
    viewModel: LoyPosViewModel,
    session: UiSessionState,
    data: LoyPosDataState,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(session.message) {
        val msg = session.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    val isOwner = session.currentUser?.isOwner != false
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topLevelDestinations = remember(isOwner) {
        if (isOwner) {
            listOf(
                TopLevelDestination(DashboardRoute, DashboardRoute::class, "Dashboard", Icons.Outlined.Dashboard, "nav_dashboard"),
                TopLevelDestination(CheckoutRoute, CheckoutRoute::class, "Checkout", Icons.Outlined.PointOfSale, "nav_checkout"),
                TopLevelDestination(InventoryRoute, InventoryRoute::class, "Inventory", Icons.Outlined.Inventory2, "nav_inventory"),
                TopLevelDestination(ReceiptsRoute, ReceiptsRoute::class, "Receipts", Icons.Outlined.ReceiptLong, "nav_receipts"),
                TopLevelDestination(SettingsRoute, SettingsRoute::class, "Settings", Icons.Outlined.Settings, "nav_settings"),
            )
        } else {
            listOf(
                TopLevelDestination(CheckoutRoute, CheckoutRoute::class, "Checkout", Icons.Outlined.PointOfSale, "nav_checkout"),
                TopLevelDestination(ReceiptsRoute, ReceiptsRoute::class, "Receipts", Icons.Outlined.ReceiptLong, "nav_receipts"),
                TopLevelDestination(ShiftRoute, ShiftRoute::class, "Shift", Icons.Outlined.AccessTime, "nav_shift"),
                TopLevelDestination(AccountRoute, AccountRoute::class, "Account", Icons.Outlined.PersonOutline, "nav_account"),
            )
        }
    }

    val showTopLevelChrome = topLevelDestinations.any { entry ->
        currentDestination?.hierarchy?.any { it.hasRoute(entry.routeClass) } == true
    }

    val navigateToTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelChrome && !useRail) {
                    NavigationBar(containerColor = LoySurface) {
                        topLevelDestinations.forEach { entry ->
                            val selected = currentDestination?.hierarchy?.any { it.hasRoute(entry.routeClass) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(entry.route) },
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                label = { Text(entry.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LoyGreen,
                                    selectedTextColor = LoyGreen,
                                    indicatorColor = LoyGreenLight,
                                ),
                                modifier = Modifier.testTag(entry.tag),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (showTopLevelChrome && useRail) {
                    NavigationRail(
                        containerColor = LoySurface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        topLevelDestinations.forEach { entry ->
                            val selected = currentDestination?.hierarchy?.any { it.hasRoute(entry.routeClass) } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(entry.route) },
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                label = { Text(entry.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = LoyGreen,
                                    selectedTextColor = LoyGreen,
                                    indicatorColor = LoyGreenLight,
                                ),
                                modifier = Modifier.testTag(entry.tag),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = if (isOwner) DashboardRoute else CheckoutRoute,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onNavigateToCheckout = { navigateToTopLevel(CheckoutRoute) },
                                onNavigateToInventory = { navigateToTopLevel(InventoryRoute) },
                                onNavigateToStock = { navController.navigate(StockRoute) },
                                onNavigateToShift = { navController.navigate(ShiftRoute) },
                                onNavigateToCustomers = { navController.navigate(CustomersRoute) },
                                onNavigateToReports = { navController.navigate(ReportsRoute) },
                                onNavigateToReceipts = { navigateToTopLevel(ReceiptsRoute) },
                                onNavigateToReceiptDetail = { receiptId ->
                                    navController.navigate(ReceiptDetailRoute(receiptId))
                                },
                            )
                        }

                        composable<CheckoutRoute> {
                            SellScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onNavigateToCart = { navController.navigate(CartRoute) },
                                onNavigateToPayment = { navController.navigate(PaymentRoute) },
                            )
                        }

                        composable<InventoryRoute> {
                            ItemsScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onNavigateToStock = { navController.navigate(StockRoute) },
                            )
                        }

                        composable<ReceiptsRoute> {
                            ReceiptsScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onOpenReceipt = { receiptId ->
                                    navController.navigate(ReceiptDetailRoute(receiptId))
                                },
                            )
                        }

                        composable<ShiftRoute> {
                            ShiftScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onBack = if (isOwner) {
                                    { navController.popBackStack() }
                                } else {
                                    null
                                },
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onNavigateToStock = { navController.navigate(StockRoute) },
                                onNavigateToCustomers = { navController.navigate(CustomersRoute) },
                                onNavigateToReports = { navController.navigate(ReportsRoute) },
                                onNavigateToShift = { navController.navigate(ShiftRoute) },
                            )
                        }

                        composable<AccountRoute> {
                            CashierAccountScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                            )
                        }

                        composable<CartRoute> {
                            CartScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onBack = { navController.popBackStack() },
                                onNavigateToPayment = { navController.navigate(PaymentRoute) },
                            )
                        }

                        composable<PaymentRoute> {
                            PaymentScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onBack = { navController.popBackStack() },
                                onPaymentCompleted = {
                                    navController.navigate(ReceiptSuccessRoute) {
                                        popUpTo<CheckoutRoute> {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }

                        composable<ReceiptSuccessRoute> {
                            ReceiptSuccessScreen(
                                viewModel = viewModel,
                                session = session,
                                data = data,
                                onDone = {
                                    navController.popBackStack(CheckoutRoute, inclusive = false)
                                },
                            )
                        }

                        composable<ReceiptDetailRoute> { entry ->
                            val args = entry.toRoute<ReceiptDetailRoute>()
                            ReceiptDetailScreen(
                                viewModel = viewModel,
                                receiptId = args.receiptId,
                                data = data,
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<StockRoute> {
                            StockScreen(
                                viewModel = viewModel,
                                data = data,
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<CustomersRoute> {
                            CustomersScreen(
                                viewModel = viewModel,
                                data = data,
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<ReportsRoute> {
                            ReportsScreen(
                                viewModel = viewModel,
                                data = data,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
