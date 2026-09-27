package com.bradflaugher.aboutthataction.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.aboutthataction.R
import com.bradflaugher.aboutthataction.engine.Zone

/** The menu palette: neon on near-black. */
object Neon {
    val night = Color(0xFF07060F)
    val ink = Color(0xFF0D0B1E)
    val panel = Color(0xE00C0A1C)
    val panelHi = Color(0xF0151230)
    val line = Color(0x33B8B0FF)
    val magenta = Color(0xFFFF2E88)
    val hotPink = Color(0xFFFF6FB5)
    val cyan = Color(0xFF21E6FF)
    val lava = Color(0xFFFF6A1A)
    val blood = Color(0xFFFF2D3C)
    val gold = Color(0xFFFFD23F)
    val text = Color(0xFFF2EEFF)
    val soft = Color(0xFFC6C0E6)
    val dim = Color(0xFF8C86B0)
    val faint = Color(0xFF4A456B)

    val title = FontFamily(Font(R.font.audiowide))
    val mono = FontFamily(Font(R.font.share_tech_mono))

    /** A signature color per zone, for the depth reveal and chart. */
    fun zone(z: Zone): Color = when (z) {
        Zone.ROOFTOP -> Color(0xFF9AD8FF)
        Zone.TOWER -> cyan
        Zone.LABS -> Color(0xFF5CFFB0)
        Zone.METRO -> Color(0xFFB78CFF)
        Zone.MINES -> Color(0xFFFFB347)
        Zone.MAGMA -> lava
        Zone.HELL -> blood
        Zone.VOID -> Color(0xFFE9E4FF)
    }
}

/** The type scale. Every menu string uses one of these sizes. */
object Type {
    val hero = 64.sp // big depth number
    val display = 34.sp // screen titles
    val headline = 22.sp // primary buttons
    val title = 17.sp // secondary buttons, row labels
    val body = 15.sp // row labels, values
    val small = 13.sp // helper text
    val micro = 11.sp // section kickers, chart labels
}

/** A strict 4dp grid. */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Minimum touch target. */
    val touch = 48.dp
}

/** Chamfered corners: the tactical-HUD silhouette every surface shares. */
object Shapes {
    val button = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    val small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
    val panel = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
    val chip = CutCornerShape(6.dp)
}

/** Motion: short, decisive, never floaty. */
object Motion {
    val out = CubicBezierEasing(0.16f, 1f, 0.3f, 1f) // expo-out
    val inOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    const val fast = 140
    const val base = 220
    const val slow = 420
}
