package com.senseink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senseink.app.data.PreferencesStore
import com.senseink.app.data.TokenStore
import com.senseink.app.ui.MainViewModel
import com.senseink.app.ui.SensiApp
import com.mudita.mmd.ThemeMMD

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        warmUpLatoFonts()

        val tokenStore = TokenStore(applicationContext)
        val preferencesStore = PreferencesStore(applicationContext)

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(tokenStore, preferencesStore),
            )
            ThemeMMD {
                SensiApp(viewModel = viewModel)
            }
        }
    }

    /**
     * MMD's ThemeMMD (TypographyMMD.kt) builds its whole typography scale -
     * every TextMMD on every screen, including ButtonMMD/OutlinedButtonMMD's
     * own labels - from 10 Lato .ttf resources bundled inside the MMD
     * library itself. A resource Font resolves synchronously the first
     * time Compose actually needs it, but that first resolution still
     * costs real disk read + parse time; on the physical Kompakt's slower
     * hardware that was enough for the very first layout pass of a screen
     * to run before Lato's metrics were ready, then reflow once it loaded
     * a beat later (2026-09-18: "it prints, then quickly reprints with
     * adjusted layout" - including "the buttons were also jumping on the
     * setup screen," same etiology, since SetupScreen's fixed Back/Next
     * footer sits right below the weighted content Column, so any change
     * in the footer's own measured height reflows everything above it).
     *
     * Deliberately BLOCKING on the main thread, not a background thread -
     * a background warm-up can still lose the race against the very first
     * screen (Setup, shown immediately on a fresh install) if Compose's
     * first layout pass happens before the warm-up finishes. Blocking here
     * only extends the ordinary blank window before setContent's first
     * frame (Android already shows the launch theme's windowBackground
     * during onCreate) - a longer pause reads as normal startup, not as a
     * jump, which matters more on e-ink than shaving tens of milliseconds
     * off cold start. The font files are small (11 files, largest 77KB) so
     * this is on the order of tens of milliseconds, not a real delay.
     * Referenced via MMD's own R class (com.mudita.mmd.R), not this app's -
     * these are the library's resources, not ours.
     */
    private fun warmUpLatoFonts() {
        val fontIds = intArrayOf(
            com.mudita.mmd.R.font.lato_regular,
            com.mudita.mmd.R.font.lato_italic,
            com.mudita.mmd.R.font.lato_thin,
            com.mudita.mmd.R.font.lato_thin_italic,
            com.mudita.mmd.R.font.lato_bold,
            com.mudita.mmd.R.font.lato_bold_italic,
            com.mudita.mmd.R.font.lato_light,
            com.mudita.mmd.R.font.lato_light_italic,
            com.mudita.mmd.R.font.lato_black,
            com.mudita.mmd.R.font.lato_black_italic,
        )
        fontIds.forEach { id ->
            runCatching { ResourcesCompat.getFont(applicationContext, id) }
        }
    }
}
