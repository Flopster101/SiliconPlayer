package com.flopster101.siliconplayer

internal data class BrowserLaunchState(
    val locationId: String? = null,
    val directoryPath: String? = null,
    val smbSourceNodeId: Long? = null,
    val httpSourceNodeId: Long? = null,
    val httpRootPath: String? = null
)

internal data class BrowserOpenRequest(
    val launchState: BrowserLaunchState,
    val returnToNetworkOnExit: Boolean
)

internal fun browserOpenRequest(
    locationId: String? = null,
    directoryPath: String? = null,
    smbSourceNodeId: Long? = null,
    httpSourceNodeId: Long? = null,
    httpRootPath: String? = null,
    returnToNetworkOnExit: Boolean = false
): BrowserOpenRequest {
    return BrowserOpenRequest(
        launchState = BrowserLaunchState(
            locationId = locationId,
            directoryPath = directoryPath,
            smbSourceNodeId = smbSourceNodeId,
            httpSourceNodeId = httpSourceNodeId,
            httpRootPath = httpRootPath
        ),
        returnToNetworkOnExit = returnToNetworkOnExit
    )
}
