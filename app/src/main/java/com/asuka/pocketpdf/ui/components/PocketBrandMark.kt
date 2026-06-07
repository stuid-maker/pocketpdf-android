package com.asuka.pocketpdf.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.asuka.pocketpdf.ui.theme.LocalPocketColors

@Composable
fun PocketBrandMark(modifier: Modifier = Modifier) {
    val colors = LocalPocketColors.current
    Canvas(modifier.size(92.dp, 84.dp)) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * .22f, 0f),
            size = Size(size.width * .58f, size.height * .72f),
            cornerRadius = CornerRadius(12f),
        )
        repeat(3) { index ->
            drawRoundRect(
                color = colors.crystalBorder.copy(alpha = .7f),
                topLeft = Offset(size.width * .35f, size.height * (.18f + index * .12f)),
                size = Size(size.width * .3f, 5f),
                cornerRadius = CornerRadius(4f),
            )
        }
        drawRoundRect(
            color = colors.crystal,
            topLeft = Offset(size.width * .12f, size.height * .55f),
            size = Size(size.width * .72f, size.height * .4f),
            cornerRadius = CornerRadius(18f),
        )
        val sparkle = Path().apply {
            moveTo(size.width * .86f, size.height * .18f)
            lineTo(size.width * .9f, size.height * .27f)
            lineTo(size.width * .98f, size.height * .31f)
            lineTo(size.width * .9f, size.height * .35f)
            lineTo(size.width * .86f, size.height * .44f)
            lineTo(size.width * .82f, size.height * .35f)
            lineTo(size.width * .74f, size.height * .31f)
            lineTo(size.width * .82f, size.height * .27f)
            close()
        }
        drawPath(sparkle, color = Color(0xFFC4A4ED))
        drawPath(sparkle, color = Color.White.copy(alpha = .65f), style = Stroke(2f))
    }
}
