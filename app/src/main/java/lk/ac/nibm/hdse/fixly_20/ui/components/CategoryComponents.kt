package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

fun getCategoryVectorIcon(category: String): ImageVector {
    return when (category.lowercase().trim()) {
        "plumbing", "plumber" -> Icons.Outlined.WaterDrop
        "electrical", "electrician" -> Icons.Outlined.ElectricBolt
        "cleaning" -> Icons.Outlined.AutoAwesome
        "carpentry" -> Icons.Outlined.Handyman
        "painting" -> Icons.Outlined.FormatPaint
        "ac repair", "ac technician" -> Icons.Outlined.AcUnit
        "gardening" -> Icons.Outlined.Yard
        "vehicle mechanic", "mechanic" -> Icons.Outlined.DirectionsCar
        "computer technician", "computer repair" -> Icons.Outlined.Computer
        else -> Icons.Outlined.Construction
    }
}

fun getCategoryIcon(category: String): ImageVector = getCategoryVectorIcon(category)

@Composable
fun CategoryCard(
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = getCategoryVectorIcon(category)
    val normalized = category.lowercase().trim()
    val accent = when {
        normalized.contains("plumb") -> Color(0xFF1686C7)
        normalized.contains("electr") -> Color(0xFFB7791F)
        normalized.contains("clean") -> Color(0xFF8756C5)
        normalized.contains("ac") -> Color(0xFF168A9B)
        normalized.contains("mechanic") -> Color(0xFF4774D8)
        else -> MaterialTheme.colorScheme.tertiary
    }
    val tint = accent.copy(alpha = 0.10f)
    val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    val border = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = container,
        border = BorderStroke(if (isSelected) 1.7.dp else 1.dp, border),
        shadowElevation = if (isSelected) 3.dp else 0.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 17.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(27.dp)
                )
            }

            Spacer(modifier = Modifier.height(11.dp))

            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = foreground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
