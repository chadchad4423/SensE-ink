package com.senseink.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senseink.app.BuildConfig
import com.senseink.app.R
import com.mudita.mmd.components.text.TextMMD

// bayou.png's actual pixel dimensions (drawable-nodpi/bayou.png) - update
// this if the source image is re-cropped. Drives Modifier.aspectRatio
// below instead of leaving height to the painter's own intrinsic size.
private const val BayouAspectRatio = 1983f / 597f

/**
 * Reached from Settings > About (2026-09-18). Privacy policy and license
 * are still reachable directly from the gear menu (see SensiApp.kt) - no
 * longer duplicated here as sub-rows (2026-09-18: "Remove 'privacy policy'
 * and 'license' submenu items from the About page").
 */
@Composable
fun AboutScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Version/Support replaced the earlier single "Contact: .../Made
        // with care..." block (2026-09-18: "let's use this general
        // layout") - version is read from BuildConfig rather than
        // hardcoded so it can't drift from the real app version.
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextMMD(text = "Version ${BuildConfig.VERSION_NAME}")
            TextMMD(text = "Support: dev@naughtynutria.com")
        }

        // "Made with love" + the bayou banner are one bottom-anchored unit
        // (2026-09-18: "I want the 'made with love' text to be just a
        // little bit above the bayou scene") - previously the text lived
        // in the top block with Version/Support and the image was pinned
        // separately, leaving them nowhere near each other. Grouping them
        // in their own Column, itself bottom-aligned, keeps a small fixed
        // gap between the two regardless of how much (or little) text is
        // in the top block above.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            TextMMD(
                text = "Made with love in Louisiana ⚜️",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            // aspectRatio(), not just fillMaxWidth() (2026-09-18: "the
            // image prints well above the bottom, then moves") - height
            // was previously left to the decoded bitmap's own intrinsic
            // size, which this large PNG didn't have ready on the very
            // first layout pass on the physical Kompakt's slower hardware:
            // the bottom-anchored Column above briefly measured shorter
            // than its final height, sat higher than it should, then
            // jumped down once the real bitmap size arrived a frame later.
            // aspectRatio is a plain float known at compose time, not
            // dependent on decode completing, so height is correct on the
            // very first pass.
            Image(
                painter = painterResource(R.drawable.bayou),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BayouAspectRatio),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}
