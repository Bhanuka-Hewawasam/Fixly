package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.ac.nibm.hdse.fixly_20.R

enum class LogoSize(val imageSize: Dp, val fontSize: Int) {
    SMALL(imageSize = 28.dp, fontSize = 18),
    MEDIUM(imageSize = 44.dp, fontSize = 24),
    LARGE(imageSize = 72.dp, fontSize = 32)
}

@Composable
fun FixlyLogo(
    modifier: Modifier = Modifier,
    size: LogoSize = LogoSize.MEDIUM,
    showTagline: Boolean = false,
    horizontal: Boolean = false
) {
    if (horizontal) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.fixly_logo),
                contentDescription = "Fixly Logo",
                modifier = Modifier.size(size.imageSize)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Fixly",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = size.fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                if (showTagline) {
                    Text(
                        text = "Home repair services",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.fixly_logo),
                contentDescription = "Fixly Logo",
                modifier = Modifier.size(size.imageSize)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Fixly",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = size.fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            if (showTagline) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Trusted home services, made simple",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
