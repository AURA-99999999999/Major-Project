package com.aura.music.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun SongOptionsMenuButton(
    isLiked: Boolean,
    isDownloaded: Boolean = false,
    onPlayNext: (() -> Unit)? = null,
    onToggleLike: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveFromDownloads: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove",
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (onPlayNext == null && onToggleLike == null && onAddToPlaylist == null && onDownload == null && onRemoveFromDownloads == null && onRemove == null) {
        return
    }

    var showMenu by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showMenu = true },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Song options",
            tint = iconTint
        )
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        if (onPlayNext != null) {
            DropdownMenuItem(
                text = { Text("Play Next") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onPlayNext()
                }
            )
        }

        if (onToggleLike != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs"
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onToggleLike()
                }
            )
        }

        if (onAddToPlaylist != null) {
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.LibraryAdd,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onAddToPlaylist()
                }
            )
        }

        if (isDownloaded && onRemoveFromDownloads != null) {
            DropdownMenuItem(
                text = { Text("Remove from downloads") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.RemoveCircleOutline,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onRemoveFromDownloads()
                }
            )
        } else if (onDownload != null) {
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onDownload()
                }
            )
        }

        if (onRemove != null) {
            DropdownMenuItem(
                text = { Text(removeLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.RemoveCircleOutline,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onRemove()
                }
            )
        }
    }
}
