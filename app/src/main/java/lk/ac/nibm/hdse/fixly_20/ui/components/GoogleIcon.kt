package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GoogleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.22f
        val radius = (w - strokeWidth) / 2f
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val center = Offset(w / 2f, h / 2f)

        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)
        val blue = Color(0xFF4285F4)

        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

        // 1. Red arc (Top)
        drawArc(
            color = red,
            startAngle = -155f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )

        // 2. Yellow arc (Left)
        drawArc(
            color = yellow,
            startAngle = 105f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )

        // 3. Green arc (Bottom)
        drawArc(
            color = green,
            startAngle = 5f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )

        // 4. Blue arc (Right)
        drawArc(
            color = blue,
            startAngle = -55f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )

        // 5. Blue Horizontal Bar
        drawLine(
            color = blue,
            start = Offset(center.x - strokeWidth * 0.2f, center.y),
            end = Offset(center.x + radius + strokeWidth / 2f, center.y),
            strokeWidth = strokeWidth
        )
    }
}
