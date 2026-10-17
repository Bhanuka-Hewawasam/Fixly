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
import androidx.compose.ui.unit.sp

fun getCategoryVectorIcon(category: String): ImageVector {
    return when (category.lowercase().trim()) {
        "plumbing" -> Icons.Outlined.WaterDrop
        "electrical" -> Icons.Outlined.ElectricBolt
        "cleaning" -> Icons.Outlined.AutoAwesome
        "carpentry" -> Icons.Outlined.Handyman
        "painting" -> Icons.Outlined.FormatPaint
        "ac repair" -> Icons.Outlined.AcUnit
        "gardening" -> Icons.Outlined.Yard
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
    val lower = category.lowercase().trim()

    val (bgTint, iconColor) = when {
        lower.contains("plum") -> Pair(Color(0xFFF0F9FF), Color(0xFF0284C7)) // Sky
        lower.contains("electr") -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706)) // Amber
        lower.contains("clean") -> Pair(Color(0xFFF3E8FF), Color(0xFF9333EA)) // Purple
        else -> Pair(Color(0xFFECFDF5), Color(0xFF059669)) // Emerald
    }

    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0)
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF334155)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        shadowElevation = if (isSelected) 4.dp else 1.5.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 8.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category,
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                ),
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
