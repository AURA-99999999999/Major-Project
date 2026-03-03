package com.aura.music.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.aura.music.data.model.*
import com.aura.music.ui.viewmodel.ListeningInsightsViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalDensity
import android.os.Build
import android.animation.ValueAnimator
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.view.View
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import androidx.compose.ui.graphics.toArgb

/**
 * ListeningInsights Screen - Visual analytics dashboard
 * 
 * Displays:
 * - Artist listening distribution (pie chart)
 * - Weekly listening activity (bar chart)
 * - Time-of-day listening patterns (segmented bar)
 * - Top played tracks (horizontal bars)
 * 
 * Features:
 * - Empty state with friendly message
 * - Pull-to-refresh functionality
 * - User-specific data only
 * - Smooth animations and visual polish
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningInsightsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ListeningInsightsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    val sectionProgress = remember { Animatable(0f) }
    val chartProgress = remember { Animatable(0f) }
    val chartLabelAlpha = remember { Animatable(0f) }

    val animationsEnabled = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            true
        }
    }

    // Reset animations on screen entry
    LaunchedEffect(Unit) {
        sectionProgress.snapTo(0f)
        chartProgress.snapTo(0f)
        chartLabelAlpha.snapTo(0f)
        viewModel.loadInsights()
    }

    // Trigger animations when data loads
    LaunchedEffect(uiState) {
        if (uiState is InsightsUiState.Success) {
            if (animationsEnabled) {
                // Start all animations (they will run)
                sectionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                )
                chartProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                )
                chartLabelAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
            } else {
                sectionProgress.snapTo(1f)
                chartProgress.snapTo(1f)
                chartLabelAlpha.snapTo(1f)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Listening Insights",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            viewModel.refreshInsights()
                            isRefreshing = false
                        },
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        Crossfade(
            targetState = uiState,
            animationSpec = tween(durationMillis = 250),
            label = "insights_loading_crossfade"
        ) { state ->
            when (state) {
                is InsightsUiState.Loading -> {
                    InsightsShimmerLoading(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
                is InsightsUiState.Success -> {
                    ListeningInsightsContent(
                        insights = state.insights,
                        sectionProgress = sectionProgress.value,
                        chartProgress = chartProgress.value,
                        chartLabelAlpha = chartLabelAlpha.value,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
                is InsightsUiState.Empty -> {
                    EmptyInsightsState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
                is InsightsUiState.Error -> {
                    ErrorInsightsState(
                        message = state.message,
                        onRetry = { viewModel.refreshInsights() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsShimmerLoading(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    var shimmerRef by remember { mutableStateOf<ShimmerFrameLayout?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            shimmerRef?.stopShimmer()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val corner = with(density) { 16.dp.toPx().toInt() }

            val shimmer = ShimmerFrameLayout(context).apply {
                setShimmer(
                    Shimmer.AlphaHighlightBuilder()
                        .setDuration(1000)
                        .setBaseAlpha(0.75f)
                        .setHighlightAlpha(1f)
                        .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
                        .build()
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    with(density) { 16.dp.toPx().toInt() },
                    with(density) { 16.dp.toPx().toInt() },
                    with(density) { 16.dp.toPx().toInt() },
                    with(density) { 16.dp.toPx().toInt() }
                )
            }

            fun card(heightDp: Dp) = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    with(density) { heightDp.toPx().toInt() }
                ).also {
                    it.bottomMargin = with(density) { 16.dp.toPx().toInt() }
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = corner.toFloat()
                    setColor(placeholderColor)
                }
            }

            container.addView(card(220.dp))
            container.addView(card(220.dp))
            container.addView(card(260.dp))
            shimmer.addView(container)
            shimmer.startShimmer()
            shimmerRef = shimmer
            shimmer
        },
        update = {
            shimmerRef = it
            it.startShimmer()
        }
    )
}

@Composable
private fun ListeningInsightsContent(
    insights: ListeningInsights,
    sectionProgress: Float,
    chartProgress: Float,
    chartLabelAlpha: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 0.dp)
    ) {
        val totalSections = 5f
        fun sectionModifier(index: Int): Modifier {
            val threshold = (index / totalSections) * 0.7f
            val local = ((sectionProgress - threshold) / 0.3f).coerceIn(0f, 1f)
            val translateY = (1f - local) * 10f
            return Modifier.graphicsLayer(
                alpha = local,
                translationY = with(density) { translateY.dp.toPx() }
            )
        }

        item {
            Box(modifier = sectionModifier(0)) {
                SummaryStatsCard(
                    totalPlays = insights.totalPlays,
                    uniqueArtists = insights.uniqueArtists
                )
            }
        }

        if (insights.artistDistribution.isNotEmpty()) {
            item {
                Box(modifier = sectionModifier(1)) {
                    ArtistDistributionCard(
                        artists = insights.artistDistribution,
                        chartProgress = chartProgress,
                        labelAlpha = chartLabelAlpha
                    )
                }
            }
        }

        if (insights.weeklyActivity.isNotEmpty()) {
            item {
                Box(modifier = sectionModifier(2)) {
                    WeeklyActivityCard(
                        weeklyData = insights.weeklyActivity,
                        chartProgress = chartProgress
                    )
                }
            }
        }

        if (insights.timeOfDayPattern.isNotEmpty()) {
            item {
                Box(modifier = sectionModifier(3)) {
                    TimeOfDayPatternCard(timeOfDay = insights.timeOfDayPattern)
                }
            }
        }

        if (insights.topTracks.isNotEmpty()) {
            item {
                Box(modifier = sectionModifier(4)) {
                    TopTracksCard(
                        tracks = insights.topTracks,
                        chartProgress = chartProgress
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryStatsCard(
    totalPlays: Int,
    uniqueArtists: Int
) {
    val safeTotalPlays = totalPlays.coerceAtLeast(0)
    val safeUniqueArtists = uniqueArtists.coerceAtLeast(0)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                label = "Total Plays",
                value = safeTotalPlays,
                modifier = Modifier.weight(1f)
            )
            Divider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            StatItem(
                label = "Artists",
                value = safeUniqueArtists,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateIntAsState(
        targetValue = value.coerceAtLeast(0),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "stat_$label"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = animatedValue.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ArtistDistributionCard(
    artists: List<ArtistListeningData>,
    chartProgress: Float,
    labelAlpha: Float
) {
    val safeArtists = artists.map {
        it.copy(
            playCount = it.playCount.coerceAtLeast(0),
            percentage = it.percentage.coerceAtLeast(0f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Artist Distribution",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            drawPieChart(
                artists = safeArtists,
                progress = chartProgress
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            safeArtists.forEach { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(artist.color))
                    )
                    Text(
                        text = artist.artistName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${artist.percentage.coerceIn(0f, 100f).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                            .copy(alpha = labelAlpha),
                        modifier = Modifier.alpha(labelAlpha)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPieChart(
    artists: List<ArtistListeningData>,
    progress: Float
) {
    val easedProgress = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
    val radius = (size.minDimension / 2.5f) * (0.7f + 0.3f * easedProgress)
    val center = Offset(size.width / 2f, size.height / 2f)
    var currentAngle = -90f + (1f - easedProgress) * 20f

    artists.forEach { artist ->
        val sweepAngle = (artist.percentage / 100f) * 360f * easedProgress
        drawArc(
            color = Color(artist.color),
            startAngle = currentAngle,
            sweepAngle = sweepAngle,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            alpha = 0.9f
        )
        currentAngle += sweepAngle
    }
}

@Composable
private fun WeeklyActivityCard(
    weeklyData: List<DailyListeningData>,
    chartProgress: Float
) {
    val safeWeeklyData = weeklyData.map {
        it.copy(
            playCount = it.playCount.coerceAtLeast(0),
            normalized = it.normalized.coerceIn(0f, 1f)
        )
    }
    val maxWeeklyPlays = safeWeeklyData.maxOfOrNull { it.playCount } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Weekly Activity",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                safeWeeklyData.forEachIndexed { index, day ->
                    val staggeredProgress = ((chartProgress - index * 0.08f) / (1f - (safeWeeklyData.size - 1) * 0.08f))
                        .coerceIn(0f, 1f)
                    val animatedHeight by animateFloatAsState(
                        targetValue = (day.normalized.coerceIn(0f, 1f) * staggeredProgress),
                        animationSpec = tween(durationMillis = 700),
                        label = "weekly_bar_${day.dayOfWeek}"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(animatedHeight)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                safeWeeklyData.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.dayOfWeek,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeOfDayPatternCard(timeOfDay: Map<String, Int>) {
    val safeTimeOfDay = timeOfDay.mapValues { (_, value) -> value.coerceAtLeast(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Time of Day",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val total = safeTimeOfDay.values.sum().coerceAtLeast(0)
        if (total > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val periods = listOf("Morning", "Afternoon", "Evening", "Night")
                val colors = listOf(
                    Color(0xFFFB8C00),  // Deep Orange
                    Color(0xFF1E88E5),  // Bright Blue
                    Color(0xFF8E24AA),  // Deep Purple
                    Color(0xFF37474F)   // Dark Blue Grey
                )

                periods.forEachIndexed { index, period ->
                    val count = safeTimeOfDay[period] ?: 0
                    val percentage = (count.toFloat() / total)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(percentage)
                            .background(colors[index])
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Morning", "Afternoon", "Evening", "Night").zip(
                    listOf(0xFFFB8C00, 0xFF1E88E5, 0xFF8E24AA, 0xFF37474F)
                ).forEach { (period, color) ->
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                        )
                        Text(
                            text = period,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTracksCard(
    tracks: List<TrackListeningData>,
    chartProgress: Float
) {
    val safeTracks = tracks.map {
        it.copy(
            playCount = it.playCount.coerceAtLeast(0),
            normalized = it.normalized.coerceIn(0f, 1f)
        )
    }
    val maxTrackPlays = safeTracks.maxOfOrNull { it.playCount } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Top Tracks",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            safeTracks.forEachIndexed { index, track ->
                val rowStagger = ((chartProgress - index * 0.1f) / (1f - (safeTracks.size - 1) * 0.1f)).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                            Text(
                                text = track.artist,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = "${track.playCount.coerceAtLeast(0)}x",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(35.dp),
                            textAlign = TextAlign.End
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((track.normalized * rowStagger).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInsightsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "♪",
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Listen to more music to unlock insights.",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your listening patterns will be visualized here once you've played more songs.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorInsightsState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 40.sp
            )
            Text(
                text = "Unable to Load Insights",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Retry")
            }
        }
    }
}
