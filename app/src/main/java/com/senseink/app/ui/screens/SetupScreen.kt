package com.senseink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senseink.app.data.PairingServer
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
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
    // Built and verified against one specific unit (a Sensi ST55); this is
    // the one place a new user sees that caveat before they've invested
    // time pairing, rather than discovering it later as a confusing Mode/
    // Fan bug. See README.md's Compatibility section for the full detail
    // this is condensed from.
    // Rewritten 2026-09-18 as a welcome + compatibility combined step,
    // replacing the earlier prose version - same underlying facts (only
    // ST55 is actually tested; the rest share the same Sensi cloud
    // account so they're expected to work, not confirmed), reformatted
    // as a supported/untested list instead of a paragraph.
    // This step is the single longest one, and briefly (2026-09-18) had to
    // be condensed into one combined list with inline (tested)/(untested)
    // tags because it was clipping its last line in the tighter Settings >
    // Connection reauth chrome. Reverted back to the original two-list
    // format now that that chrome hides the bottom nav bar during any
    // overlay (including Setup/Reauth) - freeing up the room this needed.
    OnboardingStep(
        title = "Welcome to SensE-ink, a remote control app for Sensi " +
            "brand thermostats.",
        body = "Supported model(s):\n" +
            "- Sensi ST55\n\n" +
            "Untested model(s):\n" +
            "- Sensi Lite\n" +
            "- Sensi Touch\n" +
            "- Sensi Touch 2\n\n" +
            "Multistage, heat pumps, aux heat, and other setups are " +
            "currently untested.",
    ),
    // Replaced the earlier two-step "why not log in directly" / "what
    // happens instead" split (2026-09-18: "we're going to eliminate one
    // page from the current lineup") with a single combined Overview step,
    // then split back into two (still 2026-09-18: "let's move the text
    // beginning with 'In the next step...' to a second page, and return
    // to our 3 page setup") once the single combined version turned out
    // too long to fit in the tighter Settings > Connection reauth chrome
    // (top bar + bottom nav) now that this screen's content no longer
    // scrolls - net result is the same 3-numbered-page count as before,
    // just with different text than the original three steps had.
    OnboardingStep(
        title = "Overview",
        body = "This app uses your Sensi's refresh token to authenticate " +
            "with the Sensi API.\n\n" +
            "To complete setup, you'll need access to a computer " +
            "connected to the same network your phone is using.",
    ),
    OnboardingStep(
        title = "Tiny web server",
        body = "In the next step, your phone will act as a tiny local web " +
            "server and publish a page you must visit on your computer to " +
            "get further instructions and enter your token.\n\n" +
            "When you are nearby that computer, proceed to the next step.",
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
    val pasteStepIndex = ONBOARDING_STEPS.size
    val totalSteps = pasteStepIndex + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Without these, the keyboard covered the Next/Back button on
            // the physical Kompakt instead of the layout making room for
            // it - not visible on the emulator, whose window doesn't show
            // a software keyboard the same way. No longer strictly needed
            // now that the paste step has no keyboard-triggering field of
            // its own, but harmless to keep for whatever IME the Kompakt's
            // system UI itself might show.
            .imePadding()
            // Trimmed from 24/40dp (2026-09-18 layout-fit pass) - reclaims
            // height for the tighter Settings > Connection reauth chrome
            // (top bar + bottom nav) now that content doesn't scroll; the
            // longest step (the unnumbered intro) was clipping its last
            // line there otherwise, confirmed on-device.
            .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 16.dp),
    ) {
        // Content lives in its own weighted Column (not scrollable - 2026-09-18:
        // "I do not want the content to be scrollable. I want it to fit on
        // the screen along with the buttons") so Next/Back below always
        // land at the same fixed Y position no matter how long a given
        // step's text is (Chad, 2026-09-18: "keep the next and back
        // buttons in the same position on each page so they don't
        // reprint when moving between screens") - the same fixed-slot
        // reasoning as ModeScreen/FanScreen's reserved rows, applied to
        // this screen's own footer. Every step's content must actually
        // fit in the space left after the buttons; there's no scroll to
        // fall back on if one doesn't.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The welcome/compatibility intro isn't numbered at all
            // (2026-09-18: "I'd like screen 1 of 4 to not be numbered") -
            // it's an intro, not a step in the numbered walkthrough.
            // Numbering starts at "1" on the following page, so the
            // displayed number is stepIndex itself (1, 2, ...), out of
            // totalSteps - 1 (the count of steps that actually get a
            // number). "Step X of Y" is on the same line as the title,
            // not its own line above it (2026-09-18).
            val stepPrefix = if (stepIndex > 0) "Step $stepIndex of ${totalSteps - 1}: " else ""

            if (stepIndex < ONBOARDING_STEPS.size) {
                val step = ONBOARDING_STEPS[stepIndex]
                TextMMD(text = stepPrefix + step.title)
                TextMMD(text = step.body)
            } else {
                TextMMD(text = "${stepPrefix}Connect from a computer")
                PasteStep(onTokenSaved = onTokenSaved)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // The paste step has nothing left to submit locally (see
        // PasteStep's doc comment) - pairing completes on its own once the
        // browser submits the form, so there's no "Next"/"Save" action for
        // it at all, only Back. Side by side on one row, Back on the left
        // and Next/Begin Setup on the right (Chad, 2026-09-18: "put them
        // on the same line, half and half, back on the left, next on the
        // right") rather than stacked - this one row is the entire fixed
        // footer, so both buttons land at the same fixed Y position on
        // every step regardless of which one(s) are present that step.
        // When Back is absent (step 1), Next still fills the row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (stepIndex > 0) {
                OutlinedButtonMMD(
                    onClick = { stepIndex-- },
                    modifier = Modifier.weight(1f),
                ) {
                    TextMMD(text = "Back")
                }
            }

            if (stepIndex != pasteStepIndex) {
                ButtonMMD(
                    onClick = { stepIndex++ },
                    modifier = Modifier.weight(1f),
                ) {
                    // Only the very first page calls it "Begin Setup"
                    // (2026-09-18) - every later "Next" is just advancing
                    // within the walkthrough, not starting anything.
                    TextMMD(text = if (stepIndex == 0) "Begin Setup" else "Next")
                }
            }
        }
    }
}

/**
 * The pairing webpage is the *only* place this app hosts the DevTools
 * harvest walkthrough (deliberately - see [ONBOARDING_STEPS]'s doc
 * comment); nothing else in the app duplicates those steps. So when that
 * page can't be reached, this is a hard stop, not a paste field pointing
 * at nothing - showing a plain field anyway used to strand the user with
 * "see sensi-client-spec.md," a repo file with no way to open it from
 * this screen.
 *
 * No manual-paste fallback on the success path either (2026-09-18:
 * "remove the direct entry of the key, I only want to use the web server
 * route") - this device never accepts a token typed or pasted into it
 * directly. [onTokenSaved] is only ever reached via
 * [PairingServer.onTokenReceived], i.e. by the browser actually
 * submitting the pairing form. That also means this step has no local
 * "Save"/"Next" action of its own - see [SetupScreen], which only shows
 * Back for this step.
 */
@Composable
private fun PasteStep(onTokenSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var server by remember { mutableStateOf<PairingServer?>(null) }

    DisposableEffect(Unit) {
        val instance = PairingServer(
            context = context,
            onTokenReceived = { token -> scope.launch { onTokenSaved(token) } },
        )
        runCatching { instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            .onSuccess { server = instance }
        onDispose { instance.stop() }
    }

    val ip = remember { PairingServer.localIpAddress() }
    val activeServer = server

    when {
        ip == null -> {
            // The common case: PairingServer.localIpAddress() returns null
            // whenever the WiFi station interface (wlan0) has no address -
            // i.e. WiFi isn't connected, confirmed against the physical
            // Kompakt (see PairingServer.kt's doc comment on that
            // function). No computer can reach this device at all without
            // it, pairing or otherwise.
            TextMMD(text = "Connect to WiFi to continue", fontWeight = FontWeight.Bold)
            TextMMD(
                text = "This step needs WiFi so a computer on the same " +
                    "network can open the pairing page and send your " +
                    "Sensi token here. Connect to WiFi, then come back to " +
                    "this step.",
            )
        }
        activeServer == null -> {
            // Rare: WiFi is connected but the local server itself failed
            // to start (e.g. a port collision). Distinct from the WiFi
            // case above - telling the user to connect to WiFi here would
            // be wrong, since they already are.
            TextMMD(text = "Couldn't start the pairing page", fontWeight = FontWeight.Bold)
            TextMMD(text = "Leave this step and come back to try again.")
        }
        else -> {
            // The URL used to be embedded mid-sentence and wrapped
            // wherever the line happened to break, splitting the address
            // itself across lines - harder to copy correctly by eye than
            // a line that's nothing but the address. Same reasoning as
            // giving the PIN its own bold line.
            //
            // Each label+value pair is its own tightly-spaced (4dp) inner
            // Column, not four separate children of the outer Column: the
            // outer Arrangement.spacedBy(16dp) would otherwise put a full
            // 16dp gap between "open:" and the URL right under it, and
            // between the PIN's own label and the PIN itself, which reads
            // as those two things being unrelated when they're actually
            // one instruction split across two lines for legibility. This
            // also claws back real height on a step that's otherwise tight
            // against the physical Kompakt's screen in the Settings >
            // Connection reauth path (see [SetupScreen]'s own padding
            // comment) - four full 16dp outer gaps here versus two,
            // reclaiming exactly the room needed for that fix to actually
            // land inside the visible viewport instead of past the fold.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextMMD(text = "On a computer on the same WiFi, open:")
                TextMMD(text = "http://$ip:${activeServer.listeningPort}", fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextMMD(text = "and enter this PIN there:")
                TextMMD(text = activeServer.pin, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            // No animation/spinner (AGENTS.md) - a static line instead,
            // same idiom as HomeScreen's freshness line. This step ends on
            // its own once the browser submits the form; there's nothing
            // left to tap here.
            TextMMD(text = "Waiting for that page to send your token here.")
        }
    }
}
