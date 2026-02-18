package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainNavigationScaffold(
    currentView: MainView,
    onOpenPlayerSurface: () -> Unit,
    onHomeRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    content: @Composable (mainPadding: PaddingValues, targetView: MainView) -> Unit
) {
    val shouldShowBrowserHomeAction = currentView == MainView.Browser
    val shouldShowSettingsAction = currentView != MainView.Settings

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // Keep a stable top-bar slot height so route transition content does not
                // jump vertically when entering/leaving Settings.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    AnimatedVisibility(
                        visible = currentView != MainView.Settings,
                        enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                            initialOffsetY = { fullHeight -> -fullHeight / 3 },
                            animationSpec = tween(220, easing = LinearOutSlowInEasing)
                        ),
                        exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                            targetOffsetY = { fullHeight -> -fullHeight / 4 },
                            animationSpec = tween(150, easing = FastOutLinearInEasing)
                        )
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Silicon Player",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(onClick = onOpenPlayerSurface)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            },
                            actions = {
                                AnimatedVisibility(
                                    visible = shouldShowBrowserHomeAction,
                                    enter = fadeIn(animationSpec = tween(150)),
                                    exit = fadeOut(animationSpec = tween(120))
                                ) {
                                    IconButton(onClick = onHomeRequested) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Go to app home"
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = shouldShowSettingsAction,
                                    enter = fadeIn(animationSpec = tween(150)),
                                    exit = fadeOut(animationSpec = tween(120))
                                ) {
                                    IconButton(onClick = onSettingsRequested) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Open settings"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { mainPadding ->
            AnimatedContent(
                targetState = currentView,
                transitionSpec = {
                    val forward = mainViewOrder(targetState) >= mainViewOrder(initialState)
                    val enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 210,
                            delayMillis = 60,
                            easing = LinearOutSlowInEasing
                        )
                    )
                    val exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 120,
                            easing = FastOutLinearInEasing
                        )
                    )
                    enter togetherWith exit
                },
                label = "mainViewTransition",
                modifier = Modifier
            ) { targetView ->
                val routePadding = if (targetView == MainView.Settings) {
                    PaddingValues(0.dp)
                } else {
                    mainPadding
                }
                content(routePadding, targetView)
            }
        }
    }
}
