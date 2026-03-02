package com.aura.music.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.music.auth.viewmodel.AuthViewModel
import com.aura.music.player.MusicService
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.DarkSurfaceVariant
import com.aura.music.ui.theme.DarkTextPrimary
import com.aura.music.ui.theme.DarkTextSecondary
import com.aura.music.ui.theme.GradientBackground
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.theme.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.aura.music.ui.viewmodel.ViewModelFactory

@Composable
fun ProfileScreen(
    musicService: MusicService?,
    onNavigateBack: () -> Unit,
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToLikedSongs: () -> Unit = {},
    onNavigateToThemeSettings: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel(),
    themeManager: ThemeManager? = null
) {
    val context = LocalContext.current
    val actualThemeManager: ThemeManager = themeManager ?: viewModel(factory = ViewModelFactory.create(context.applicationContext as android.app.Application))
    val themeState by actualThemeManager.themeState.collectAsState()
    
    var userName by remember { mutableStateOf("User") }

    val currentUser = FirebaseAuth.getInstance().currentUser
    userName = currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: "User"
    val userEmail = currentUser?.email ?: "No email"
    val photoUrl = currentUser?.photoUrl

    GradientBackground(
        gradientTheme = themeState.gradientTheme,
        isDark = themeState.themeMode != com.aura.music.ui.theme.ThemeMode.LIGHT,
        hasDynamicColors = themeState.currentDynamicColors.dominant != null,
        dynamicColorsEnabled = themeState.dynamicAlbumColors
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)  // Extra space for mini player
        ) {
            item {
                // Top hero section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface)
                            )
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape),
                            shadowElevation = 8.dp,
                            tonalElevation = 0.dp,
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            if (photoUrl != null) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Profile photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userName.firstOrNull()?.uppercase() ?: "U",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = userName,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userEmail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ProfileActionRow(
                            icon = Icons.Outlined.QueueMusic,
                            label = "My Playlists",
                            onClick = onNavigateToPlaylists
                        )

                        ProfileActionRow(
                            icon = Icons.Outlined.QueueMusic,
                            label = "Liked Songs",
                            onClick = onNavigateToLikedSongs
                        )

                        ProfileActionRow(
                            icon = Icons.Outlined.TrendingUp,
                            label = "Listening Insights",
                            onClick = onNavigateToInsights
                        )

                        ProfileActionRow(
                            icon = Icons.Outlined.Palette,
                            label = "Theme Settings",
                            onClick = onNavigateToThemeSettings
                        )

                        ProfileActionRow(
                            icon = Icons.Outlined.Edit,
                            label = "Edit Profile",
                            onClick = onNavigateToEditProfile
                        )

                        ProfileActionRow(
                            icon = Icons.Outlined.ExitToApp,
                            label = "Logout",
                            onClick = { authViewModel.signout() }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = label, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(Color.Transparent)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

