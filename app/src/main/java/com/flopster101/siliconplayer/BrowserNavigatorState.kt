package com.flopster101.siliconplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class BrowserNavigatorSnapshot(
    val launchState: BrowserLaunchState,
    val returnToNetworkOnExit: Boolean
)

internal class BrowserNavigatorState(
    initialSnapshot: BrowserNavigatorSnapshot = BrowserNavigatorSnapshot(
        launchState = BrowserLaunchState(),
        returnToNetworkOnExit = false
    )
) {
    private val backStack = mutableStateListOf<BrowserNavigatorSnapshot>()
    private var currentSnapshot by mutableStateOf(initialSnapshot)

    val launchState: BrowserLaunchState
        get() = currentSnapshot.launchState

    val returnToNetworkOnExit: Boolean
        get() = currentSnapshot.returnToNetworkOnExit

    val hasBackStackEntries: Boolean
        get() = backStack.isNotEmpty()

    fun open(request: BrowserOpenRequest, keepHistory: Boolean = true) {
        if (keepHistory && currentSnapshot.launchState != BrowserLaunchState()) {
            backStack += currentSnapshot
        }
        currentSnapshot = BrowserNavigatorSnapshot(
            launchState = request.launchState,
            returnToNetworkOnExit = request.returnToNetworkOnExit
        )
    }

    fun updateLaunchState(launchState: BrowserLaunchState) {
        currentSnapshot = currentSnapshot.copy(launchState = launchState)
    }

    fun updateReturnTarget(returnToNetworkOnExit: Boolean) {
        currentSnapshot = currentSnapshot.copy(returnToNetworkOnExit = returnToNetworkOnExit)
    }

    fun clearCurrent() {
        currentSnapshot = currentSnapshot.copy(
            launchState = BrowserLaunchState(),
            returnToNetworkOnExit = false
        )
    }

    fun popToPrevious(): Boolean {
        if (backStack.isEmpty()) return false
        currentSnapshot = backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun clearHistory() {
        backStack.clear()
    }
}
