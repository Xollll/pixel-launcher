package com.example.pixellauncher

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

object Px {
    val bg = Color(0xFF0A0807)
    val panel = Color(0xFF120D0B)
    val line = Color(0xFF5A3626)
    val coral = Color(0xFFE8795A)
    val coralDark = Color(0xFF7A3A24)
    val cream = Color(0xFFF4EDE4)
    val dim = Color(0xFF9C8678)
}

/** Bundled pixel typefaces used throughout the launcher UI. */
object PixelFonts {
    val display = FontFamily(Font(R.font.press_start_2p_regular))
    val body = FontFamily(Font(R.font.vt323_regular))
}

/** Hard-edged pixel box: flat fill, 2dp border, solid offset shadow. No rounded corners. */
fun Modifier.pixelBox(
    fill: Color = Px.panel,
    border: Color = Px.line,
    shadow: Dp = 4.dp,
    borderWidth: Dp = 2.dp,
    shadowColor: Color = Color.Black,
): Modifier = this.drawBehind {
    val s = shadow.toPx()
    val bw = borderWidth.toPx()
    drawRect(shadowColor, topLeft = Offset(s, s), size = size)
    drawRect(fill)
    drawRect(
        border,
        topLeft = Offset(bw / 2, bw / 2),
        size = Size(size.width - bw, size.height - bw),
        style = Stroke(bw),
    )
}

/** Pixel-styled text; body copy uses VT323 unless a display face is requested. */
@Composable
fun PxText(
    text: String,
    size: TextUnit,
    color: Color = Px.cream,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    fontFamily: FontFamily = PixelFonts.body,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = fontFamily,
            textAlign = align,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
