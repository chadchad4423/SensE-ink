package com.senseink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

// Adapted 2026-09-18 from MK Volume Plus's privacy policy (Chad's other
// app), not copied verbatim - SensE-ink's actual data flow is different
// (MK Volume Plus does no networking at all; this app necessarily talks to
// Sensi's cloud to control the thermostat, so a flat "never transmitted
// outside your device" claim would be false here). Adjusted to describe
// what this app actually does: the Sensi token goes to Sensi's own API
// (unavoidable - that's the whole feature), the local pairing step stays
// on the user's own WiFi, and the optional remote-config fetch is a plain
// unauthenticated GET with nothing personal attached. Still needs Chad's
// own review before shipping, same as the other two docs here.
const val PRIVACY_POLICY_TEXT = "Effective date: September 18, 2026\n\n" +
    "SensE-ink respects your privacy.\n\n" +
    "Your Sensi account token: to control your thermostat, this app sends " +
    "your Sensi refresh token to Sensi's own cloud service (operated by " +
    "Emerson/Sensi, not by this app's developer) - this is unavoidable, " +
    "since that's how your thermostat is actually reached. That token is " +
    "stored only in this device's encrypted local storage; the developer " +
    "never receives it and it is never sent anywhere else.\n\n" +
    "Pairing: setting up or updating your Sensi token uses a temporary, " +
    "PIN-protected page hosted by this app directly on your phone, reached " +
    "only by a computer on the same WiFi network. That exchange stays on " +
    "your own network and is not sent to the developer or any third " +
    "party.\n\n" +
    "Local preferences: display settings (such as your preferred " +
    "temperature unit) are stored locally on your device so the app can " +
    "remember them. The developer cannot access these, and the app does " +
    "not transmit them anywhere.\n\n" +
    "Remote notices: at launch, this app may fetch a small config file " +
    "from GitHub to show an in-app notice or flag a new version. This is " +
    "an ordinary web request with no personal or account information " +
    "attached, subject to GitHub's own standard server logging.\n\n" +
    "SensE-ink does not contain advertising, analytics, or tracking " +
    "technologies, and does not sell or share data with third parties.\n\n" +
    "You can remove all locally stored data by uninstalling the app.\n\n" +
    "SensE-ink does not knowingly collect personal information from " +
    "anyone, including children.\n\n" +
    "This policy applies only to SensE-ink. Your device, its operating " +
    "system, and Sensi's own service process information independently " +
    "under their own privacy policies.\n\n" +
    "If this app's privacy practices change, this policy will be updated " +
    "and the effective date above will be revised."

const val LICENSE_TEXT = "MIT License\n\n" +
    "Copyright (c) 2026 Chad Carson\n\n" +
    "Permission is hereby granted, free of charge, to any person obtaining " +
    "a copy of this software and associated documentation files (the " +
    "\"Software\"), to deal in the Software without restriction, including " +
    "without limitation the rights to use, copy, modify, merge, publish, " +
    "distribute, sublicense, and/or sell copies of the Software, and to " +
    "permit persons to whom the Software is furnished to do so, subject to " +
    "the following conditions:\n\n" +
    "The above copyright notice and this permission notice shall be " +
    "included in all copies or substantial portions of the Software.\n\n" +
    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, " +
    "EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF " +
    "MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND " +
    "NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS " +
    "BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN " +
    "ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN " +
    "CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE " +
    "SOFTWARE."

// LazyColumnMMD's scrollbar thumb size is item-count-based (visible items /
// total items), not pixel-based - a paragraph much longer than the others
// counts as only "1 item" the same as a short one, so a document with one
// or two outsized paragraphs among many short ones gets a thumb size that
// doesn't track how much of the document is actually on screen. Long
// paragraphs are split at sentence boundaries above this length so the
// two documents' item counts (and so their thumb proportions) are
// comparable instead of one being dominated by a couple of long blocks
// (2026-09-18: "the fill... is inconsistent between the pages").
private const val MaxParagraphChars = 150

private fun splitIntoItems(text: String): List<String> =
    text.split("\n\n").flatMap { paragraph ->
        if (paragraph.length <= MaxParagraphChars) {
            listOf(paragraph)
        } else {
            // Sentence boundaries only, not every paragraph - most stay
            // whole and read as normal flowing prose; only the handful of
            // long outliers split into a couple of shorter blocks.
            paragraph.split(Regex("(?<=[.!?])\\s+"))
        }
    }

/**
 * LazyColumnMMD instead of a plain Modifier.verticalScroll (2026-09-18:
 * "add the mmd scrollbar and paged scrolling") - that gets this screen
 * MMD's own scrollbar (draggable/tappable track plus up/down chevrons) and
 * its default paged-drag behavior for free: a drag on the list jumps by a
 * fixed number of items (LazyColumnMMD's own scrollStep, 4 by default)
 * rather than following the finger continuously. That behavior operates on
 * lazy items, not raw scroll distance, so the document is split into
 * paragraph-ish chunks (see [splitIntoItems]) and each becomes one item -
 * a single one-item list would have nothing to page between.
 */
@Composable
fun LegalDocScreen(text: String) {
    val chunks = splitIntoItems(text)
    LazyColumnMMD(
        // Asymmetric padding - the scrollbar sits inside this padding too
        // (it's a sibling of the list inside LazyColumnMMD's own Row, not
        // outside it), and already carries its own vertical (16dp, around
        // each chevron) and horizontal (8dp) insets internally. end=4dp
        // (2026-09-18: "the scrollbar is too far off of the rightmost
        // margin") keeps it close to the true edge instead of doubling up
        // both insets; top/bottom=8dp (2026-09-18, compared directly
        // against the MMD demo app's own component list: "too much space"
        // between the scroll arrows and the divider/screen edge) for the
        // same reason - the previous 20dp was on top of MMD's own 16dp
        // per-chevron padding, doubling the gap the demo doesn't have.
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(chunks) { chunk ->
            TextMMD(text = chunk)
        }
    }
}
