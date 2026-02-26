package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.FileItem
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BrowserDirectoryListing<Entry, Metadata>(
    val entries: List<Entry>,
    val metadata: Metadata
)

internal interface BrowserListingAdapter<Location, Entry, Metadata> {
    suspend fun list(
        location: Location,
        onProgress: (suspend (loadedCount: Int, partialEntries: List<Entry>) -> Unit)? = null
    ): Result<BrowserDirectoryListing<Entry, Metadata>>
}

internal data class LocalBrowserListLocation(
    val directory: File
)

internal class LocalBrowserListingAdapter(
    private val repository: FileRepository
) : BrowserListingAdapter<LocalBrowserListLocation, FileItem, Unit> {
    override suspend fun list(
        location: LocalBrowserListLocation,
        onProgress: (suspend (loadedCount: Int, partialEntries: List<FileItem>) -> Unit)?
    ): Result<BrowserDirectoryListing<FileItem, Unit>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = repository.getFiles(location.directory)
            BrowserDirectoryListing(
                entries = entries,
                metadata = Unit
            )
        }
    }
}

internal data class SmbBrowserListLocation(
    val spec: SmbSourceSpec,
    val pathInsideShare: String?,
    val listHostShares: Boolean
)

internal class SmbBrowserListingAdapter :
    BrowserListingAdapter<SmbBrowserListLocation, SmbBrowserEntry, Unit> {
    override suspend fun list(
        location: SmbBrowserListLocation,
        onProgress: (suspend (loadedCount: Int, partialEntries: List<SmbBrowserEntry>) -> Unit)?
    ): Result<BrowserDirectoryListing<SmbBrowserEntry, Unit>> {
        val result = if (location.listHostShares) {
            listSmbHostShareEntries(location.spec)
        } else {
            listSmbDirectoryEntries(
                spec = location.spec,
                pathInsideShare = location.pathInsideShare
            )
        }
        return result.map { entries ->
            BrowserDirectoryListing(
                entries = entries,
                metadata = Unit
            )
        }
    }
}

internal data class HttpBrowserListLocation(
    val spec: HttpSourceSpec
)

internal class HttpBrowserListingAdapter :
    BrowserListingAdapter<HttpBrowserListLocation, HttpBrowserEntry, HttpSourceSpec> {
    override suspend fun list(
        location: HttpBrowserListLocation,
        onProgress: (suspend (loadedCount: Int, partialEntries: List<HttpBrowserEntry>) -> Unit)?
    ): Result<BrowserDirectoryListing<HttpBrowserEntry, HttpSourceSpec>> {
        return listHttpDirectoryEntries(
            spec = location.spec,
            onProgress = onProgress
        ).map { listing ->
            BrowserDirectoryListing(
                entries = listing.entries,
                metadata = listing.resolvedDirectorySpec
            )
        }
    }
}
