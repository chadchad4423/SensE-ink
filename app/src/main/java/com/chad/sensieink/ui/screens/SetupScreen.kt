package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chad.sensieink.data.PairingServer
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.launch

private data class OnboardingStep(val title: String, val body: String)

/**
 * The full DevTools walkthrough (opening manager.sensicomfort.com, filtering
 * the Network tab, ignoring the paywall, copying the token) used to live
 * here as separate wizard steps, but it only ever needs to be read on a
 * computer - so it now lives on the pairing webpage itself (see
 * PairingServer's PAGE), right next to the form the user is about to fill
 * in. This screen just explains why a computer is needed and gets out of
 * the way.
 */
private val ONBOARDING_STEPS = listOf(
    OnboardingStep(
        title = "Connect to Sensi",
        body = "This app can't log in with your Sensi username and password " +
            "directly - Sensi's login page is protected by reCAPTCHA. " +
            "Instead, you'll pair with a computer on the same WiFi network: " +
            "it'll walk you through getting a one-time token from Sensi's " +
            "website, then send it here automatically. You'll only need to " +
            "repeat this if you change your Sensi password.",
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
            // Without these, the keyboard covered the Save/Next button on
            // the physical Kompakt instead of the layout making room for
            // it - not visible on the emulator, whose window doesn't show
            // a software keyboard the same way.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextMMD(text = "Step ${stepIndex + 1} of $totalSteps")

        if (stepIndex < ONBOARDING_STEPS.size) {
            val step = ONBOARDING_STEPS[stepIndex]
            TextMMD(text = step.title)
            TextMMD(text = step.body)
        } else {
            TextMMD(text = "2. Connect from a computer")
            PasteStep(
                tokenInput = tokenInput,
                onTokenInputChange = { tokenInput = it },
                onTokenSaved = onTokenSaved,
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

/**
 * Offers two ways to get the token in: a local pairing server a desktop
 * browser on the same WiFi can submit to (avoids typing a ~330-char string
 * on the Kompakt's own keyboard - see PROJECT-STATUS.md for why on-device
 * entry turned out to be unreliable), and the plain paste field as a
 * fallback when there's no second device handy. The pairing webpage also
 * hosts the full DevTools walkthrough now, since it only ever needs to be
 * read on the same computer the user is already on.
 */
@Composable
private fun PasteStep(
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    onTokenSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf<PairingServer?>(null) }

    DisposableEffect(Unit) {
        val instance = PairingServer(
            onTokenReceived = { token -> scope.launch { onTokenSaved(token) } },
        )
        // A collision on the chosen port, or no network at all, just means
        // no pairing option is shown - the manual field below still works.
        runCatching { instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            .onSuccess { server = instance }
        onDispose { instance.stop() }
    }

    val ip = remember { PairingServer.localIpAddress() }
    val activeServer = server
    if (ip != null && activeServer != null) {
        TextMMD(
            text = "On a computer on the same WiFi, open " +
                "http://$ip:${activeServer.listeningPort} - it has full " +
                "instructions for getting your token from Sensi's website. " +
                "Enter this PIN there:",
        )
        TextMMD(text = activeServer.pin, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        HorizontalDividerMMD()
        TextMMD(text = "No second device handy? Paste the token here directly:")
    } else {
        TextMMD(
            text = "Paste your refresh_token here. See sensi-client-spec.md " +
                "for how to get one from manager.sensicomfort.com. It's " +
                "stored encrypted on this device only.",
        )
    }

    TextFieldMMD(
        value = tokenInput,
        onValueChange = onTokenInputChange,
        modifier = Modifier.fillMaxWidth(),
        label = { TextMMD(text = "refresh_token") },
        singleLine = true,
        // A refresh_token is an opaque ~330-char string with no spaces to
        // break words on - on the physical Kompakt, predictive text actively
        // suggested completions while typing (visible in the suggestion
        // strip). autoCorrectEnabled = false is enough to stop that;
        // KeyboardType.Password was tried too, but it turns on Android's
        // credential-autofill popup (visibly a 1Password suggestion here),
        // which covers the field and is a worse problem than the one it
        // solves.
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
    )
}
