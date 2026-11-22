package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Five tappable stars. Pass onRatingChange to make it interactive (customer giving a review),
 * leave it null for a read-only display.
 */
@Composable
fun StarRatingBar(
    rating: Int,
    modifier: Modifier = Modifier,
    onRatingChange: ((Int) -> Unit)? = null
) {
    Row(modifier = modifier) {
        for (star in 1..5) {
            val filled = star <= rating
            val tapModifier = if (onRatingChange != null) {
                Modifier.clickable { onRatingChange(star) }
            } else {
                Modifier
            }

            Text(
                text = if (filled) "★" else "☆",
                fontSize = 34.sp,
                color = if (filled) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline,
                modifier = tapModifier.padding(end = 6.dp)
            )
        }
    }
}

/** e.g. "★ 4.5 (12 reviews)" or "New provider · no reviews yet". */
fun formatRating(average: Double, reviewCount: Int): String {
    if (reviewCount <= 0) return "New provider · no reviews yet"

    val averageText = String.format(java.util.Locale.getDefault(), "%.1f", average)
    val label = if (reviewCount == 1) "review" else "reviews"
    return "★ $averageText ($reviewCount $label)"
}
