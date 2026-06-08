package com.asuka.pocketpdf.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.asuka.pocketpdf.ui.theme.PocketRadii

@Composable
fun PocketCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 34.dp),
        enabled = enabled,
        shape = RoundedCornerShape(PocketRadii.Control),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF302739),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF302739).copy(alpha = .38f),
        ),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 1.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
