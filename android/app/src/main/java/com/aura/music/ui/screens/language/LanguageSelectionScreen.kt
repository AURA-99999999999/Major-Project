package com.aura.music.ui.screens.language

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.data.remote.dto.Language
import com.aura.music.ui.theme.GradientBackground
import com.aura.music.ui.theme.ThemeManager
import com.aura.music.ui.viewmodel.ViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    onNavigateBack: () -> Unit,
    onLanguagesSaved: () -> Unit = {},
    initialSelectedLanguages: List<String> = emptyList(),
    isFirstTime: Boolean = false,
    viewModel: LanguageSelectionViewModel,
    themeManager: ThemeManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actualThemeManager: ThemeManager = themeManager
        ?: androidx.lifecycle.viewmodel.compose.viewModel(factory = ViewModelFactory.create(context.applicationContext as android.app.Application))
    val themeState by actualThemeManager.themeState.collectAsState()

    // State
    var selectedLanguages by rememberSaveable { mutableStateOf(initialSelectedLanguages.toSet()) }
    var isLoading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    
    // Get current user
    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid ?: ""
    
    // Handle save button click
    fun saveLanguages() {
        if (selectedLanguages.isEmpty()) {
            Toast.makeText(context, "Please select at least one language", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (uid.isEmpty()) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoading = true
        scope.launch {
            try {
                val result = viewModel.updateLanguages(uid, selectedLanguages.toList())
                
                if (result) {
                    Toast.makeText(
                        context,
                        "Language preferences updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    onLanguagesSaved()
                    if (!isFirstTime) {
                        onNavigateBack()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Failed to update preferences. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isLoading = false
            }
        }
    }
    
    // Handle language selection toggle
    fun toggleLanguage(languageId: String) {
        if (selectedLanguages.contains(languageId)) {
            selectedLanguages = selectedLanguages - languageId
        } else {
            selectedLanguages = selectedLanguages + languageId
        }
    }
    
    GradientBackground(
        gradientTheme = themeState.gradientTheme,
        isDark = themeState.themeMode != com.aura.music.ui.theme.ThemeMode.LIGHT,
        hasDynamicColors = themeState.currentDynamicColors.dominant != null,
        dynamicColorsEnabled = themeState.dynamicAlbumColors
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Language Preferences",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (!isFirstTime) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                Surface(
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Button(
                        onClick = { saveLanguages() },
                        enabled = selectedLanguages.isNotEmpty() && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = "Apply",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // Description text
                Text(
                    text = "Select languages to personalize your music recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Selected count
                Text(
                    text = "${selectedLanguages.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Language grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(Language.SUPPORTED_LANGUAGES) { language ->
                        LanguageCard(
                            language = language.displayName,
                            isSelected = selectedLanguages.contains(language.id),
                            onClick = { toggleLanguage(language.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(
    language: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        label = "cardBackgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurface,
        label = "cardContentColor"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Text(
                    text = language,
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
