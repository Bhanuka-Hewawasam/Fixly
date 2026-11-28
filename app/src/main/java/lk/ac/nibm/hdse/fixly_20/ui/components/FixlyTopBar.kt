package lk.ac.nibm.hdse.fixly_20.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import lk.ac.nibm.hdse.fixly_20.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixlyTopBar(
    title: String = "Fixly",
    userName: String? = null,
    profilePictureUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    unreadNotificationCount: Int = 0,
    onNotificationClick: (() -> Unit)? = null
) {
    val initial = (userName?.firstOrNull() ?: 'S').uppercaseChar()

    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
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
                            contentDescription = "Fixly",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        letterSpacing = (-0.45).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            actions = {
                if (onNotificationClick != null) {
                    IconButton(onClick = onNotificationClick) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge {
                                        Text(unreadNotificationCount.coerceAtMost(99).toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                }

                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(42.dp)
                        .border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                            CircleShape
                        )
                        .padding(2.dp)
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
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        ) {
                            val state = painter.state
                            if (state is AsyncImagePainter.State.Error) {
                                Text(
                                    text = initial.toString(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    } else {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        )
    }
}
