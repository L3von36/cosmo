package com.loypos.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.loypos.app.ui.AppWorkspace
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.theme.BackOfficeNavy
import com.loypos.app.ui.theme.BackOfficeSlate
import com.loypos.app.ui.theme.BackOfficeTeal
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
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PointOfSale,
                        contentDescription = "LoyPOS",
                        tint = LoyGreen,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "LoyPOS",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun AuthGateScreen(
    viewModel: LoyPosViewModel,
    hasAnyUser: Boolean,
    hasAnyBackOfficeUser: Boolean,
    initialWorkspace: AppWorkspace = AppWorkspace.POS,
    lastUserEmail: String? = null,
    lastBackOfficeEmail: String? = null,
) {
    // Sign-in ONLY: accounts live on the LoyPOS server (created in
    // devices.php → staff accounts) and mirror onto this device on first
    // sign-in. No in-app account creation anywhere.
    val headerBrush = Brush.linearGradient(listOf(LoyGreen, LoyGreenAccent))

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
                .background(headerBrush)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .align(Alignment.CenterStart),
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PointOfSale,
                            contentDescription = "LoyPOS logo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Sign in with the account created for you on the LoyPOS server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(14.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                ) {
                    LoginForm(
                        viewModel = viewModel,
                        initialEmail = lastUserEmail,
                    )
                }
            }
        }
    }
}

@Composable
fun BackOfficeAuthScreen(
    viewModel: LoyPosViewModel,
    hasAnyBackOfficeUser: Boolean,
    canReturnToPos: Boolean,
    storeName: String,
    lastBackOfficeEmail: String? = null,
) {
    if (canReturnToPos) {
        BackHandler { viewModel.switchToPos() }
    }

    // Sign-in ONLY — no back-office account creation here. The shop owner is
    // mirrored automatically when they open the Back Office; extra admins are
    // created inside the app (Back Office → Admin).

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
                .background(Brush.linearGradient(listOf(BackOfficeNavy, BackOfficeSlate)))
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .align(Alignment.CenterStart),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AdminPanelSettings,
                                contentDescription = "Back Office Auth",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BackOfficeTeal,
                    ) {
                        Text(
                            text = "BACK OFFICE AUTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "$storeName · Back Office",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Sign in with a Back Office admin account to access dashboards, stock controls, staff, and settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(14.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                ) {
                    BackOfficeLoginForm(
                        viewModel = viewModel,
                        initialEmail = lastBackOfficeEmail,
                    )
                }

                if (canReturnToPos) {
                    OutlinedButton(
                        onClick = { viewModel.switchToPos() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("backoffice_auth_return_pos"),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Return to POS Register")
                    }
                }
            }
        }
    }
}

@Composable
private fun BackOfficeLoginForm(
    viewModel: LoyPosViewModel,
    initialEmail: String? = null,
) {
    var email by remember { mutableStateOf(initialEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Back Office Email") },
            leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("bo_login_email_input"),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Back Office Password") },
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
            modifier = Modifier.fillMaxWidth().testTag("bo_login_password_input"),
        )

        if (error != null) {
            Text(error!!, color = LoyError, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                busy = true
                error = null
                viewModel.signInBackOffice(email, password) { err ->
                    busy = false
                    error = err
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = BackOfficeNavy, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("bo_login_button"),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Unlock Back Office")
            }
        }

        Text(
            text = "Back Office admins are created inside the app: Back Office → Admin → Add back-office admin.",
            style = MaterialTheme.typography.bodySmall,
            color = LoyTextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}


@Composable
private fun LoginForm(
    viewModel: LoyPosViewModel,
    initialEmail: String? = null,
) {
    var email by remember { mutableStateOf(initialEmail ?: "") }
    var password by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
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
                .height(44.dp)
                .testTag("login_button"),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Sign in")
            }
        }

        Text(
            text = "No account yet? The owner creates accounts on the LoyPOS server (devices.php → Staff accounts).",
            style = MaterialTheme.typography.bodySmall,
            color = LoyTextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
