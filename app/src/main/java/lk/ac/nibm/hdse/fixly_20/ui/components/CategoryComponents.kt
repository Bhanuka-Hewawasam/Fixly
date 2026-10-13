package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val iconContainerBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        shadowElevation = if (isSelected) 3.dp else 1.5.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 10.dp)
                .fillMaxWidth()
        ) {
            // Soft Circular Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconContainerBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category,
                    tint = iconTint,
                    modifier = Modifier.size(23.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
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
