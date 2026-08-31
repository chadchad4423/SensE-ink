package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/**
 * Shown until a refresh_token is stored. The token is harvested manually by the
 * user via browser DevTools (sensi-client-spec.md section 3) - this screen only
 * accepts and persists it, it never attempts a password-based login.
 */
@Composable
fun SetupScreen(onTokenSaved: (String) -> Unit) {
    var tokenInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextMMD(text = "Connect to Sensi")
        TextMMD(
            text = "Paste the refresh_token harvested from manager.sensicomfort.com's " +
                "login request (see sensi-client-spec.md section 3). It is stored " +
                "encrypted on this device only.",
        )
        TextFieldMMD(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { TextMMD(text = "refresh_token") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        ButtonMMD(
            onClick = { onTokenSaved(tokenInput) },
            enabled = tokenInput.isNotBlank(),
        ) {
            TextMMD(text = "Save")
        }
    }
}
