package com.flopster101.siliconplayer

internal object ManualRemoteOpenCoordinator {
    @Volatile
    private var cancelPendingOpenWorkCallback: (() -> Unit)? = null

    fun registerCancellationCallback(callback: () -> Unit) {
        cancelPendingOpenWorkCallback = callback
    }

    fun cancelPendingOpenWork() {
        cancelPendingOpenWorkCallback?.invoke()
    }
}
