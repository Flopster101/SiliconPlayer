package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainNavigationScaffold(
    currentView: MainView,
    onOpenPlayerSurface: () -> Unit,
    onHomeRequested: () -> Unit,
    onOpenUrlOrPathRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    mainContentModifier: Modifier = Modifier,
    content: @Composable (mainPadding: PaddingValues, targetView: MainView) -> Unit
) {
    val isMainTopBarVisible =
        currentView != MainView.Settings &&
            currentView != MainView.Network &&
            currentView != MainView.Playlists
    var lastVisibleTopBarView by remember { mutableStateOf(currentView) }
    var previousContentView by remember { mutableStateOf(currentView) }
    val viewTransition = updateTransition(targetState = currentView, label = "mainViewTopBarTransition")
    val displayedTopBarView = if (isMainTopBarVisible) currentView else lastVisibleTopBarView
    val shouldShowBrowserHomeAction =
        displayedTopBarView == MainView.Browser ||
            displayedTopBarView == MainView.Network ||
            displayedTopBarView == MainView.Playlists
    val shouldShowSettingsAction = displayedTopBarView != MainView.Settings
    val homeScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val browserInvolvedInContentTransition =
        previousContentView == MainView.Browser || currentView == MainView.Browser

    LaunchedEffect(currentView, viewTransition.currentState, viewTransition.targetState) {
        if (isMainTopBarVisible) {
            lastVisibleTopBarView = currentView
        }
        val homeIsInvolvedInTransition =
            viewTransition.currentState == MainView.Home || viewTransition.targetState == MainView.Home
        if (currentView != MainView.Home && !homeIsInvolvedInTransition) {
            homeScrollBehavior.state.heightOffset = 0f
            homeScrollBehavior.state.contentOffset = 0f
        }
    }
    LaunchedEffect(currentView) {
        previousContentView = currentView
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (displayedTopBarView == MainView.Home) {
                    Modifier.nestedScroll(homeScrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            ),
        topBar = {
            AnimatedVisibility(
                visible = isMainTopBarVisible,
                enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight / 3 },
                    animationSpec = tween(220, easing = LinearOutSlowInEasing)
                ),
                exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                    targetOffsetY = { fullHeight -> -fullHeight / 4 },
                    animationSpec = tween(150, easing = FastOutLinearInEasing)
                )
            ) {
                AnimatedContent(
                    targetState = displayedTopBarView == MainView.Home,
                    transitionSpec = {
                        val enteringFromTop = targetState
                        val enter = fadeIn(
                            animationSpec = tween(260, delayMillis = 40, easing = LinearOutSlowInEasing)
                        ) + slideInVertically(
                            initialOffsetY = { fullHeight ->
                                if (enteringFromTop) -fullHeight / 3 else fullHeight / 3
                            },
                            animationSpec = tween(280, easing = LinearOutSlowInEasing)
                        )
                        val exit = fadeOut(
                            animationSpec = tween(180, easing = FastOutLinearInEasing)
                        ) + slideOutVertically(
                            targetOffsetY = { fullHeight ->
                                if (enteringFromTop) fullHeight / 4 else -fullHeight / 4
                            },
                            animationSpec = tween(180, easing = FastOutLinearInEasing)
                        )
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    label = "mainTopBarTypeTransition"
                ) { showHomeBar ->
                    if (showHomeBar) {
                        LargeTopAppBar(
                            title = {
                                MainTopBarPlayerTitle(
                                    onClick = onOpenPlayerSurface
                                )
                            },
                            actions = {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(150)),
                                    exit = fadeOut(animationSpec = tween(120))
                                ) {
                                    IconButton(onClick = onOpenUrlOrPathRequested) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = "Open URL or path"
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
                            },
                            scrollBehavior = homeScrollBehavior
                        )
                    } else {
                        TopAppBar(
                            title = {
                                MainTopBarPlayerTitle(
                                    onClick = onOpenPlayerSurface
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
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(150)),
                                    exit = fadeOut(animationSpec = tween(120))
                                ) {
                                    IconButton(onClick = onOpenUrlOrPathRequested) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = "Open URL or path"
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
        }
    ) { mainPadding ->
            val routePadding = if (
                currentView == MainView.Settings ||
                currentView == MainView.Network ||
                currentView == MainView.Playlists
            ) {
                PaddingValues(0.dp)
            } else {
                mainPadding
            }
            if (browserInvolvedInContentTransition) {
                Box(modifier = mainContentModifier) {
                    content(routePadding, currentView)
                }
                return@Scaffold
            }
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
                modifier = mainContentModifier
            ) { targetView ->
                val targetRoutePadding = if (
                    targetView == MainView.Settings ||
                    targetView == MainView.Network ||
                    targetView == MainView.Playlists
                ) {
                    PaddingValues(0.dp)
                } else {
                    mainPadding
                }
                content(targetRoutePadding, targetView)
            }
    }
}

@Composable
private fun MainTopBarPlayerTitle(
    onClick: () -> Unit
) {
    Text(
        text = "Silicon Player",
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
