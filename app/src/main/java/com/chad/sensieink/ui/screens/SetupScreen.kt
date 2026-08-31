package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

private data class OnboardingStep(val title: String, val body: String)

/**
 * Mirrors the exact steps confirmed working against the live Sensi backend
 * 2026-08-31 (see PROJECT-STATUS.md): the paywall step in particular exists
 * because a real account hit it and it's easy to mistake for a hard blocker.
 */
private val ONBOARDING_STEPS = listOf(
    OnboardingStep(
        title = "Connect to Sensi",
        body = "This app can't log in with your Sensi username and password " +
            "directly - Sensi's login page is protected by reCAPTCHA. Instead " +
            "you'll copy a one-time refresh_token out of your browser. It takes " +
            "about a minute, and you'll only need to repeat it if you change " +
            "your Sensi password.",
    ),
    OnboardingStep(
        title = "1. Open developer tools",
        body = "On a computer, open Chrome or Edge and go to " +
            "manager.sensicomfort.com. Press F12 to open Developer Tools, " +
            "then click its Network tab.",
    ),
    OnboardingStep(
        title = "2. Filter and log in",
        body = "Type \"token\" into the Network tab's filter box. Then log in " +
            "with the same email and password you use in the Sensi mobile app.",
    ),
    OnboardingStep(
        title = "3. Ignore the paywall",
        body = "You may land on a screen asking for \$1.50/mo per thermostat. " +
            "That's a separate paid product (Sensi Manager) - you don't need " +
            "it and don't need to subscribe. Your token was already captured " +
            "by the login request in the previous step.",
    ),
    OnboardingStep(
        title = "4. Copy the token",
        body = "In the filtered request list, find \"token?device=...\". " +
            "There may be two - use the one whose Response tab has content. " +
            "Open it and copy the full refresh_token value (a long string " +
            "starting with \"eyJ\").",
    ),
)

/**
 * Shown until a refresh_token is stored. Walks a new user through harvesting
 * one via browser DevTools (sensi-client-spec.md section 3), then accepts and
 * persists it - this screen never attempts a password-based login itself.
 */
@Composable
fun SetupScreen(onTokenSaved: (String) -> Unit) {
    var stepIndex by remember { mutableIntStateOf(0) }
    var tokenInput by remember { mutableStateOf("") }
    val pasteStepIndex = ONBOARDING_STEPS.size
    val totalSteps = pasteStepIndex + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextMMD(text = "Step ${stepIndex + 1} of $totalSteps")

        if (stepIndex < ONBOARDING_STEPS.size) {
            val step = ONBOARDING_STEPS[stepIndex]
            TextMMD(text = step.title)
            TextMMD(text = step.body)

            if (stepIndex == 0) {
                OutlinedButtonMMD(
                    onClick = { stepIndex = pasteStepIndex },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = "I already have my refresh_token")
                }
            }
        } else {
            TextMMD(text = "5. Paste it here")
            TextMMD(text = "It's stored encrypted on this device only.")
            TextFieldMMD(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { TextMMD(text = "refresh_token") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }

        if (stepIndex == pasteStepIndex) {
            ButtonMMD(
                onClick = { onTokenSaved(tokenInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = tokenInput.isNotBlank(),
            ) {
                TextMMD(text = "Save")
            }
        } else {
            ButtonMMD(
                onClick = { stepIndex++ },
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextMMD(text = "Next")
            }
        }

        if (stepIndex > 0) {
            OutlinedButtonMMD(
                onClick = { stepIndex-- },
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextMMD(text = "Back")
            }
        }
    }
}
