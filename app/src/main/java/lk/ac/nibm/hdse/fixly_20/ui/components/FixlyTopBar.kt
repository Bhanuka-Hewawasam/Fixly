package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import lk.ac.nibm.hdse.fixly_20.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixlyTopBar(
    title: String = "Fixly",
    userName: String? = null,
    profilePictureUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null
) {
    val initial = (userName?.firstOrNull() ?: 'S').uppercaseChar()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                if (navigationIcon != null) {
                    navigationIcon()
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.fixly_logo),
                            contentDescription = "Fixly Logo",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            },
            actions = {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            enabled = onAvatarClick != null,
                            onClickLabel = "Open profile and account menu",
                            onClick = { onAvatarClick?.invoke() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!profilePictureUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = profilePictureUrl,
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = {
                                Text(
                                    text = initial.toString(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                )
                            }
                        )
                    } else {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                    }
                }
            }
        )
    }
}
