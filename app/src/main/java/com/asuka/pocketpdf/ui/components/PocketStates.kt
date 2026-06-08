package com.asuka.pocketpdf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asuka.pocketpdf.ui.theme.LocalPocketColors
import com.asuka.pocketpdf.ui.theme.PocketRadii
import com.asuka.pocketpdf.ui.theme.PocketSpacing

@Composable
fun PocketEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketColors.current
    Column(
        modifier = modifier
            .padding(PocketSpacing.Xl)
            .widthIn(max = 312.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(PocketRadii.Floating),
                ambientColor = Color(0x10302739),
                spotColor = Color(0x10302739),
            )
            .background(
                colors.paper.copy(alpha = .82f),
                RoundedCornerShape(PocketRadii.Floating),
            )
            .border(
                1.dp,
                colors.crystalBorder,
                RoundedCornerShape(PocketRadii.Floating),
            )
            .padding(horizontal = PocketSpacing.Xl, vertical = PocketSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.Md),
    ) {
        PocketBrandMark()
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mutedInk,
            textAlign = TextAlign.Center,
        )
        PocketCompactButton(actionLabel, onAction)
    }
}
