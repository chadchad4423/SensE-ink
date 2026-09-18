package com.senseink.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.divider.DividerDefaultsMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * Shared between SettingsScreen and AboutScreen (2026-09-18) - both are the
 * same row-list idiom, so the row and its divider live here instead of
 * being duplicated per screen.
 */
@Composable
fun SettingsRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    // A visible chevron only for rows that navigate somewhere else (Chad,
    // 2026-09-18: "There's no UI clue to indicate it is tappable" about
    // Connection - the chevron is that clue). Units also has an onClick,
    // but it cycles its own value in place rather than navigating, so it
    // deliberately leaves this false - a chevron there would promise a
    // destination that doesn't exist.
    showChevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextMMD(text = label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextMMD(text = value, fontSize = 16.sp) // bodyMedium/labelLarge
            if (showChevron) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * HorizontalDividerMMD (DividerMMD.kt) is a thin wrapper around Material
 * 3's HorizontalDivider - modifier/thickness/color only, no dash-pattern
 * support at all, since M3 doesn't have one either. Hand-rolled here via
 * Canvas + dashPathEffect rather than reaching for a third-party divider
 * library for one screen's worth of dashes. Reuses MMD's own color
 * constant (DividerDefaultsMMD.color) rather than inventing a new one,
 * so this still reads as "the app's divider," just dashed.
 *
 * Thickness is a literal 1px (2026-09-18: "1px instead of the
 * current thick version"), not MMD's own 3dp default. Deliberately not
 * `Dp.Hairline` either, even though DividerMMD's own doc comment invokes
 * exactly that term ("a single pixel divider regardless of screen
 * density") - Hairline only gets that special "round up to 1 real pixel"
 * behavior from the specific built-in APIs that check for it explicitly
 * (HorizontalDivider's own thickness param, Modifier.border, etc.). A raw
 * Canvas.drawLine()'s strokeWidth is just a Float in this DrawScope's
 * pixel space - passing `1f` directly *is* one physical pixel, no
 * Dp.Hairline detection required.
 */
@Composable
fun DashedDividerMMD() {
    val color = DividerDefaultsMMD.color
    Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f),
        )
    }
}
