package com.loypos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.theme.LoyBackground
import com.loypos.app.ui.theme.LoyError
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenAccent
import com.loypos.app.ui.theme.LoyTextSecondary

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoyGreen),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PointOfSale,
                        contentDescription = "LoyPOS",
                        tint = LoyGreen,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "LoyPOS",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun AuthGateScreen(
    viewModel: LoyPosViewModel,
    hasAnyUser: Boolean,
) {
    var showSignUp by remember(hasAnyUser) { mutableStateOf(!hasAnyUser) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoyBackground)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(LoyGreen, LoyGreenAccent)))
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 30.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .align(Alignment.CenterStart),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PointOfSale,
                            contentDescription = "LoyPOS logo",
                            tint = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (showSignUp) "Set up your shop" else "Welcome back",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (showSignUp) {
                        "Create your owner account to start selling offline-first with 300 ready items."
                    } else {
                        "Sign in as owner or seller to open the register."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 1.dp,
            ) {
                if (showSignUp) {
                    SignUpForm(
                        viewModel = viewModel,
                        onSwitchToSignIn = { showSignUp = false },
                    )
                } else {
                    LoginForm(
                        viewModel = viewModel,
                        onSwitchToSignUp = { showSignUp = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    viewModel: LoyPosViewModel,
    onSwitchToSignUp: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password / PIN") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(
                        if (showPwd) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPwd) "Hide password" else "Show password",
                    )
                }
            },
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
        )

        if (error != null) {
            Text(error!!, color = LoyError, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                busy = true
                error = null
                viewModel.signIn(email, password) { err ->
                    busy = false
                    error = err
                }
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("login_button"),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Sign in")
            }
        }

        TextButton(
            onClick = onSwitchToSignUp,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("switch_to_signup_button"),
        ) {
            Text("Create a new shop account", color = LoyGreen)
        }
    }
}

@Composable
private fun SignUpForm(
    viewModel: LoyPosViewModel,
    onSwitchToSignIn: () -> Unit,
) {
    var shopName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("Main branch") }
    var ownerName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = shopName,
            onValueChange = { shopName = it; error = null },
            label = { Text("Shop name") },
            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("signup_shop_input"),
        )
        OutlinedTextField(
            value = branchName,
            onValueChange = { branchName = it },
            label = { Text("First branch name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("signup_branch_input"),
        )
        OutlinedTextField(
            value = ownerName,
            onValueChange = { ownerName = it },
            label = { Text("Owner name (optional)") },
            leadingIcon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("signup_owner_input"),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("signup_email_input"),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password (min 8 characters)") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(
                        if (showPwd) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPwd) "Hide password" else "Show password",
                    )
                }
            },
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("signup_password_input"),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = { Text("Confirm password") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("signup_confirm_input"),
        )

        if (error != null) {
            Text(error!!, color = LoyError, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                if (shopName.trim().isEmpty()) {
                    error = "Enter your shop name"
                    return@Button
                }
                if (password != confirm) {
                    error = "Passwords do not match"
                    return@Button
                }
                busy = true
                error = null
                viewModel.signUp(email, password, shopName, ownerName, branchName) { err ->
                    busy = false
                    error = err
                }
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("signup_button"),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Create account")
            }
        }

        TextButton(
            onClick = onSwitchToSignIn,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("switch_to_login_button"),
        ) {
            Text("Already have an account? Sign in", color = LoyTextSecondary)
        }
    }
}
