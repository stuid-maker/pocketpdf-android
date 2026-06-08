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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asuka.pocketpdf.ui.theme.LocalPocketColors

@Composable
fun PocketBrandMark(modifier: Modifier = Modifier) {
    val colors = LocalPocketColors.current
    Canvas(
        modifier
            .size(78.dp, 76.dp)
            .semantics { contentDescription = "PocketPDF" },
    ) {
        drawRoundRect(
            color = Color(0xFFF1E6FF),
            topLeft = Offset(size.width * .26f, size.height * .06f),
            size = Size(size.width * .52f, size.height * .62f),
            cornerRadius = CornerRadius(10f),
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
            color = colors.crystal.copy(alpha = .92f),
            topLeft = Offset(size.width * .15f, size.height * .61f),
            size = Size(size.width * .68f, size.height * .3f),
            cornerRadius = CornerRadius(13f),
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
        drawPath(sparkle, color = Color(0xFF9E6DE0))
        drawPath(sparkle, color = Color.White.copy(alpha = .65f), style = Stroke(2f))
    }
}
