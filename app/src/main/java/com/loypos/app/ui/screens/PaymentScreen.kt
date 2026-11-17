package com.loypos.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loypos.app.core.formatCents
import com.loypos.app.core.parseAmountToCents
import com.loypos.app.ui.LoyPosDataState
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.TransactionViewModel
import com.loypos.app.ui.UiSessionState
import com.loypos.app.ui.components.CurrencyNumPad
import com.loypos.app.ui.theme.LocalLoyMetrics
import com.loypos.app.ui.theme.LocalLoyWindowSize
import com.loypos.app.ui.theme.LoyBackground
import com.loypos.app.ui.theme.LoyBorder
import com.loypos.app.ui.theme.LoyError
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenLight
import com.loypos.app.ui.theme.LoySurface
import com.loypos.app.ui.theme.LoySurfaceVariant
import com.loypos.app.ui.theme.LoyTextPrimary
import com.loypos.app.ui.theme.LoyTextSecondary
import com.loypos.app.ui.theme.TabularMoneyStyle
import com.loypos.app.ui.theme.accessibleTouchTarget
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    viewModel: LoyPosViewModel,
    transactionViewModel: TransactionViewModel,
    session: UiSessionState,
    data: LoyPosDataState,
    onBack: () -> Unit,
    onPaymentCompleted: () -> Unit,
) {
    val cartSession by transactionViewModel.cartSession.collectAsStateWithLifecycle()
    val totals by transactionViewModel.cartTotals.collectAsStateWithLifecycle()
    val sym = data.settings.currencySymbol
    val totalCents = totals.totalCents
    val selectedCustomer = data.customers.firstOrNull { it.id == cartSession.customerId }
    val windowSize = LocalLoyWindowSize.current
    val metrics = LocalLoyMetrics.current

    var paymentMethod by remember { mutableStateOf("cash") }
    var tenderedText by remember(totalCents) {
        mutableStateOf((totalCents / 100.0).let {
            if (totalCents % 100L == 0L) (totalCents / 100L).toString() else "%.2f".format(it)
        })
    }

    val paidCents = if (paymentMethod == "card") {
        totalCents
    } else {
        parseAmountToCents(tenderedText) ?: 0L
    }
    val canCharge = cartSession.items.isNotEmpty() && (paymentMethod == "card" || paidCents >= totalCents)
    val diffCents = paidCents - totalCents

    val quickAmounts = remember(totalCents) {
        val exact = totalCents
        val units = totalCents / 100.0
        val next50 = (ceil(units / 50.0) * 50.0).toLong() * 100L
        val next100 = (ceil(units / 100.0) * 100.0).toLong() * 100L
        listOf(exact, next50, next100).distinct().filter { it >= exact }.take(3)
    }

    val onCompletePayment: () -> Unit = {
        transactionViewModel.finalizeSale(
            paymentType = paymentMethod,
            paidCents = paidCents,
            settings = data.settings,
            customer = selectedCustomer,
            branchId = session.activeBranchId,
            cashier = session.currentUser,
        ) {
            viewModel.refreshHealthAndBackups()
            onPaymentCompleted()
        }
    }

    val totalCard: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = LoySurface,
            border = BorderStroke(1.dp, LoyBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(metrics.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Total due", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatCents(totalCents, sym),
                    style = TabularMoneyStyle.merge(MaterialTheme.typography.displaySmall),
                    fontWeight = FontWeight.Bold,
                    color = LoyTextPrimary,
                )
                if (totals.discountCents > 0L || totals.taxCents > 0L) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("Subtotal ${formatCents(totals.subtotalCents, sym)}")
                            if (totals.discountCents > 0L) append(" · Disc -${formatCents(totals.discountCents, sym)}")
                            if (totals.taxCents > 0L) append(" · Tax ${formatCents(totals.taxCents, sym)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LoyTextSecondary,
                    )
                }
            }
        }
    }

    val methodSelectorRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.itemGap),
        ) {
            MethodCard(
                selected = paymentMethod == "cash",
                icon = Icons.Outlined.Payments,
                label = "Cash",
                onClick = { paymentMethod = "cash" },
                modifier = Modifier.weight(1f).testTag("method_cash"),
            )
            MethodCard(
                selected = paymentMethod == "card",
                icon = Icons.Outlined.CreditCard,
                label = "Card",
                onClick = { paymentMethod = "card" },
                modifier = Modifier.weight(1f).testTag("method_card"),
            )
        }
    }

    val tenderStatusRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.itemGap),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = LoySurface,
                border = BorderStroke(1.dp, LoyGreen),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(metrics.cardPadding)) {
                    Text("Cash received", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "$sym${tenderedText.ifEmpty { "0" }}",
                        style = TabularMoneyStyle.merge(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (diffCents >= 0L) LoyGreenLight else LoySurfaceVariant,
                border = BorderStroke(1.dp, if (diffCents >= 0L) LoyGreen else LoyBorder),
                modifier = Modifier.weight(1f),
            ) {
                Column(modifier = Modifier.padding(metrics.cardPadding)) {
                    Text(
                        if (diffCents >= 0L) "Change" else "Remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diffCents >= 0L) LoyGreen else LoyError,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = formatCents(kotlin.math.abs(diffCents), sym),
                        style = TabularMoneyStyle.merge(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                        color = if (diffCents >= 0L) LoyGreen else LoyError,
                    )
                }
            }
        }
    }

    val chargeSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.itemGap)) {
            if (!canCharge && paymentMethod == "cash") {
                Text(
                    text = "Enter at least ${formatCents(totalCents, sym)} to complete cash payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LoyError,
                )
            }
            Button(
                onClick = onCompletePayment,
                enabled = canCharge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.minTouchTarget)
                    .testTag("payment_charge_button"),
            ) {
                Text("Charge ${formatCents(totalCents, sym)}", fontWeight = FontWeight.Bold)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = metrics.topBarHeight,
                title = { Text("Payment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.accessibleTouchTarget(metrics.minTouchTarget)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LoySurface),
            )
        },
        containerColor = LoyBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(metrics.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!windowSize.isCompact) {
                Row(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.screenPadding),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                    ) {
                        totalCard()
                        methodSelectorRow()
                        if (paymentMethod == "cash") {
                            tenderStatusRow()
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = LoySurface,
                                border = BorderStroke(1.dp, LoyBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Confirm card terminal approval for ${formatCents(totalCents, sym)} before pressing Charge.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LoyTextSecondary,
                                    modifier = Modifier.padding(metrics.cardPadding),
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                    ) {
                        if (paymentMethod == "cash") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                quickAmounts.forEach { amt ->
                                    FilterChip(
                                        selected = paidCents == amt,
                                        onClick = {
                                            tenderedText = if (amt % 100L == 0L) (amt / 100L).toString() else "%.2f".format(amt / 100.0)
                                        },
                                        label = { Text(formatCents(amt, sym), style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier.height(metrics.chipHeight),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { tenderedText = "" },
                                    modifier = Modifier.heightIn(min = metrics.minTouchTarget).testTag("payment_clear_cash"),
                                ) {
                                    Text("Clear")
                                }
                            }
                            CurrencyNumPad(
                                onKey = { d ->
                                    if (d == "." && tenderedText.contains(".")) return@CurrencyNumPad
                                    tenderedText = if (tenderedText == "0" && d != ".") d else tenderedText + d
                                },
                                onBackspace = {
                                    if (tenderedText.isNotEmpty()) tenderedText = tenderedText.dropLast(1)
                                },
                                onClear = { tenderedText = "" },
                                allowDecimal = true,
                            )
                        }
                        chargeSection()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {
                    totalCard()
                    methodSelectorRow()
                    if (paymentMethod == "cash") {
                        tenderStatusRow()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            quickAmounts.forEach { amt ->
                                FilterChip(
                                    selected = paidCents == amt,
                                    onClick = {
                                        tenderedText = if (amt % 100L == 0L) (amt / 100L).toString() else "%.2f".format(amt / 100.0)
                                    },
                                    label = { Text(formatCents(amt, sym), style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.height(metrics.chipHeight),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { tenderedText = "" },
                                modifier = Modifier.heightIn(min = metrics.minTouchTarget).testTag("payment_clear_cash"),
                            ) {
                                Text("Clear")
                            }
                        }

                        CurrencyNumPad(
                            onKey = { d ->
                                if (d == "." && tenderedText.contains(".")) return@CurrencyNumPad
                                tenderedText = if (tenderedText == "0" && d != ".") d else tenderedText + d
                            },
                            onBackspace = {
                                if (tenderedText.isNotEmpty()) tenderedText = tenderedText.dropLast(1)
                            },
                            onClear = { tenderedText = "" },
                            allowDecimal = true,
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = LoySurface,
                            border = BorderStroke(1.dp, LoyBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Confirm card terminal approval for ${formatCents(totalCents, sym)} before pressing Charge.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LoyTextSecondary,
                                modifier = Modifier.padding(metrics.cardPadding),
                            )
                        }
                    }
                    chargeSection()
                }
            }
        }
    }
}

@Composable
private fun MethodCard(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalLoyMetrics.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) LoyGreenLight else LoySurface,
        border = BorderStroke(1.5.dp, if (selected) LoyGreen else LoyBorder),
        modifier = modifier
            .heightIn(min = metrics.minTouchTarget)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) LoyGreen else LoyTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) LoyGreen else LoyTextPrimary,
            )
        }
    }
}
