package com.stocksense.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.ui.theme.Blue600
import com.stocksense.app.ui.theme.Green400

/**
 * Lightweight line chart rendered purely with Compose Canvas.
 * No third-party charting library required.
 */
@Composable
fun StockChart(
    history: List<HistoryPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Blue600
) {
    if (history.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No chart data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val prices = history.map { it.close.toFloat() }
    val minPrice = prices.min()
    val maxPrice = prices.max()
    val priceRange = (maxPrice - minPrice).takeIf { it > 0f } ?: 1f

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val width = size.width
        val height = size.height
        val stepX = if (prices.size > 1) width / (prices.size - 1) else width

        // Build path
        val path = Path()
        prices.forEachIndexed { i, price ->
            val x = i * stepX
            val y = height - ((price - minPrice) / priceRange) * height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Fill under curve
        val fillPath = Path().apply {
            addPath(path)
            lineTo((prices.size - 1) * stepX, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent)
            )
        )

        // Draw line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw last price dot
        val lastX = (prices.size - 1) * stepX
        val lastY = height - ((prices.last() - minPrice) / priceRange) * height
        drawCircle(color = Green400, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
    }
}
