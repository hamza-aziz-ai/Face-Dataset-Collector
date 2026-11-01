package com.example.facedatasetcollector.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.R

@Composable
fun ConnectScreen(busy: Boolean, error: String?, onConnect: (String) -> Unit, modifier: Modifier = Modifier) {
    var address by rememberSaveable { mutableStateOf("") }

    StaffPage(modifier, centred = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connect to your server", style = MaterialTheme.typography.displaySmall)
            Text(
                "Your administrator gives you the server address. You only do this once on each device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Server address") },
            placeholder = { Text("https://attendance.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { onConnect(address) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorMessage(error)
        PrimaryButton("Connect", busy, enabled = address.isNotBlank()) { onConnect(address) }
        Text(
            "HTTPS only. This can be your organisation's own server or a central one run on its behalf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SignInScreen(
    clientName: String,
    host: String,
    isMock: Boolean,
    busy: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // WHY remember, not rememberSaveable: a password must not go into saved instance state.
    var password by remember { mutableStateOf("") }
    val ready = username.isNotBlank() && password.isNotEmpty()

    StaffPage(modifier, centred = true) {
        MockChip(isMock)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_app_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.size(32.dp),
                )
            }
            Text("Face enrolment", style = MaterialTheme.typography.displaySmall)
            Text(
                "Sign in with your staff account to enrol people from your organisation's roster.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (ready) onSignIn(username, password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorMessage(error)
        PrimaryButton("Sign in", busy, enabled = ready) { onSignIn(username, password) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Uploading to: $clientName ($host)",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Viewer accounts cannot enrol people.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChangeServer, modifier = Modifier.height(44.dp)) { Text("Use a different server") }
    }
}

/** Full-screen stop. There is deliberately no way past it except setting up again. */
@Composable
fun ClientChangedScreen(expectedClient: String, onSetUpAgain: () -> Unit, modifier: Modifier = Modifier) {
    StaffPage(modifier, centred = true) {
        Text("This server has changed", style = MaterialTheme.typography.displaySmall)
        Text(
            "This device was set up for $expectedClient, but the server now identifies as a different organisation. Nothing was sent.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Check with your administrator before you continue.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton("Set up this device again", busy = false, onClick = onSetUpAgain)
    }
}
