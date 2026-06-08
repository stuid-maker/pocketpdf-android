package com.asuka.pocketpdf.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AnnotationToolbar(
    selectedText: String,
    onHighlight: (color: Int) -> Unit,
    onUnderline: (color: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorOptions = listOf(
        0x80FFD700.toInt() to "黄",  // Gold
        0x8000FF00.toInt() to "绿",  // Green
        0x800000FF.toInt() to "蓝",  // Blue
        0x80FF0000.toInt() to "红",  // Red
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF0302739), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(12.dp),
    ) {
        Text(
            selectedText,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            colorOptions.forEach { (color, label) ->
                Button(
                    onClick = { onHighlight(color) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(color)),
                    modifier = Modifier.size(36.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(label, fontSize = 10.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onHighlight(colorOptions[0].first) }) { Text("🔆 高亮") }
            Button(onClick = { onUnderline(colorOptions[2].first) }) { Text("📝 下划线") }
        }
        TextButton(onClick = onDismiss) { Text("取消") }
    }
}
