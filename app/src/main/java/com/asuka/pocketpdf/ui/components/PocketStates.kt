package com.asuka.pocketpdf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.asuka.pocketpdf.ui.theme.LocalPocketColors
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
        modifier = modifier.padding(PocketSpacing.Xxl),
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
