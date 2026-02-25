package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.SmbBrowserEntry
import com.flopster101.siliconplayer.SmbSourceSpec
import com.flopster101.siliconplayer.buildSmbEntrySourceSpec
import com.flopster101.siliconplayer.buildSmbRequestUri
import com.flopster101.siliconplayer.buildSmbSourceId
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.isSmbAuthenticationFailure
import com.flopster101.siliconplayer.joinSmbRelativePath
import com.flopster101.siliconplayer.listSmbDirectoryEntries
import com.flopster101.siliconplayer.listSmbHostShareEntries
import com.flopster101.siliconplayer.normalizeSmbPathForShare
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween

private val SMB_ICON_BOX_SIZE = 38.dp
private val SMB_ICON_GLYPH_SIZE = 26.dp

@Composable
internal fun SmbFileBrowserScreen(
    sourceSpec: SmbSourceSpec,
    bottomContentPadding: Dp,
    backHandlingEnabled: Boolean,
    onExitBrowser: () -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onRememberSmbCredentials: (String, String?, String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val launchShare = remember(sourceSpec.share) { sourceSpec.share.trim() }
    val canBrowseHostShares = launchShare.isBlank()
    val sourceId = remember(sourceSpec) { buildSmbSourceId(sourceSpec) }
    val rootPath = remember(sourceSpec.path, launchShare) {
        if (launchShare.isBlank()) "" else normalizeSmbPathForShare(sourceSpec.path).orEmpty()
    }

    var currentShare by remember(sourceSpec) { mutableStateOf(launchShare) }
    var currentSubPath by remember(sourceSpec) { mutableStateOf("") }
    var entries by remember(sourceSpec) { mutableStateOf<List<SmbBrowserEntry>>(emptyList()) }
    var isLoading by remember(sourceSpec) { mutableStateOf(false) }
    var errorMessage by remember(sourceSpec) { mutableStateOf<String?>(null) }
    var listJob by remember(sourceSpec) { mutableStateOf<Job?>(null) }
    val loadingLogLines = remember(sourceSpec) { mutableStateListOf<String>() }
    var authDialogVisible by remember(sourceSpec) { mutableStateOf(false) }
    var authDialogErrorMessage by remember(sourceSpec) { mutableStateOf<String?>(null) }
    var authDialogUsername by remember(sourceSpec) { mutableStateOf(sourceSpec.username.orEmpty()) }
    var authDialogPassword by remember(sourceSpec) { mutableStateOf("") }
    var authDialogPasswordVisible by remember(sourceSpec) { mutableStateOf(false) }
    var authRememberPassword by remember(sourceSpec) { mutableStateOf(true) }
    var sessionUsername by remember(sourceSpec) { mutableStateOf(sourceSpec.username) }
    var sessionPassword by remember(sourceSpec) { mutableStateOf(sourceSpec.password) }
    val credentialsSpec = remember(sourceSpec, sessionUsername, sessionPassword) {
        sourceSpec.copy(
            share = "",
            path = null,
            username = sessionUsername,
            password = sessionPassword
        )
    }

    fun appendLoadingLog(message: String) {
        val lineNumber = loadingLogLines.size + 1
        loadingLogLines += "[${lineNumber.toString().padStart(2, '0')}] $message"
        val maxLines = 80
        if (loadingLogLines.size > maxLines) {
            repeat(loadingLogLines.size - maxLines) {
                loadingLogLines.removeAt(0)
            }
        }
    }

    fun effectivePath(): String? {
        val sub = normalizeSmbPathForShare(currentSubPath).orEmpty()
        return when {
            rootPath.isBlank() && sub.isBlank() -> null
            sub.isBlank() -> rootPath
            rootPath.isBlank() -> sub
            else -> "$rootPath/$sub"
        }
    }

    fun isSharePickerMode(): Boolean = currentShare.isBlank()

    fun loadCurrentDirectory() {
        listJob?.cancel()
        isLoading = true
        errorMessage = null
        loadingLogLines.clear()
        val share = currentShare
        val pathInsideShare = effectivePath()
        appendLoadingLog("Connecting to smb://${credentialsSpec.host}")
        if (share.isBlank()) {
            appendLoadingLog("Enumerating host shares")
        } else {
            appendLoadingLog("Opening share '$share'")
            appendLoadingLog(
                if (pathInsideShare.isNullOrBlank()) {
                    "Listing share root directory"
                } else {
                    "Listing '$pathInsideShare'"
                }
            )
        }
        listJob = coroutineScope.launch {
            val result = if (share.isBlank()) {
                listSmbHostShareEntries(credentialsSpec)
            } else {
                listSmbDirectoryEntries(
                    credentialsSpec.copy(share = share),
                    pathInsideShare
                )
            }
            result.onSuccess { resolved ->
                entries = resolved
                errorMessage = null
                val folders = resolved.count { it.isDirectory }
                val files = resolved.size - folders
                appendLoadingLog("Found ${resolved.size} entries")
                appendLoadingLog("$folders folders, $files files")
                appendLoadingLog("Load finished")
            }.onFailure { throwable ->
                entries = emptyList()
                val authFailed = isSmbAuthenticationFailure(throwable)
                if (authFailed) {
                    authDialogUsername = credentialsSpec.username.orEmpty()
                    authDialogPassword = ""
                    authDialogPasswordVisible = false
                    authRememberPassword = true
                    authDialogErrorMessage = if (credentialsSpec.password.isNullOrBlank()) {
                        "This SMB source requires authentication."
                    } else {
                        "Authentication failed. Check username and password."
                    }
                    authDialogVisible = true
                    errorMessage = "Authentication required."
                } else {
                    errorMessage = throwable.message ?: if (share.isBlank()) {
                        "Unable to list SMB shares"
                    } else {
                        "Unable to list SMB directory"
                    }
                }
                appendLoadingLog(
                    "Load failed: ${throwable.message ?: throwable.javaClass.simpleName ?: "Unknown error"}"
                )
            }
            isLoading = false
            listJob = null
        }
    }

    fun navigateUpWithinBrowser(): Boolean {
        if (currentSubPath.isNotBlank()) {
            currentSubPath = currentSubPath.substringBeforeLast('/', missingDelimiterValue = "")
            loadCurrentDirectory()
            return true
        }
        if (canBrowseHostShares && currentShare.isNotBlank()) {
            currentShare = ""
            currentSubPath = ""
            loadCurrentDirectory()
            return true
        }
        return false
    }

    DisposableEffect(Unit) {
        onDispose {
            listJob?.cancel()
        }
    }

    LaunchedEffect(sourceSpec) {
        currentShare = launchShare
        currentSubPath = ""
        authDialogVisible = false
        authDialogErrorMessage = null
        authDialogUsername = sourceSpec.username.orEmpty()
        authDialogPassword = ""
        authDialogPasswordVisible = false
        authRememberPassword = true
        sessionUsername = sourceSpec.username
        sessionPassword = sourceSpec.password
        loadCurrentDirectory()
    }

    BackHandler(enabled = backHandlingEnabled) {
        if (!navigateUpWithinBrowser()) {
            onExitBrowser()
        }
    }

    val canNavigateUp = currentSubPath.isNotBlank() || (canBrowseHostShares && currentShare.isNotBlank())
    val sharePickerMode = isSharePickerMode()
    val subtitle = buildString {
        append("smb://")
        append(credentialsSpec.host)
        if (currentShare.isNotBlank()) {
            append('/')
            append(currentShare)
            effectivePath()?.let { path ->
                if (path.isNotBlank()) {
                    append('/')
                    append(path)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (!navigateUpWithinBrowser()) {
                                onExitBrowser()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (canNavigateUp) {
                                    "Navigate up"
                                } else {
                                    "Back to home"
                                }
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = "File Browser",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    ) { paddingValues ->
        val showLoadingCard = isLoading && entries.isEmpty()
        AnimatedContent(
            targetState = showLoadingCard,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 170))
            },
            label = "smbBrowserLoadingTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { showLoading ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomContentPadding)
            ) {
                if (canNavigateUp) {
                    item("parent") {
                        SmbParentDirectoryRow(
                            onClick = {
                                navigateUpWithinBrowser()
                            }
                        )
                    }
                }

                if (showLoading) {
                    item("loading") {
                        SmbLoadingCard(
                            title = if (sharePickerMode) {
                                "Loading SMB shares..."
                            } else {
                                "Loading SMB directory..."
                            },
                            subtitle = if (sharePickerMode) {
                                "Fetching available shares from host"
                            } else {
                                "Fetching folder entries"
                            },
                            logLines = loadingLogLines
                        )
                    }
                } else if (!errorMessage.isNullOrBlank()) {
                    item("error") {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (sharePickerMode) {
                                        "Unable to list SMB shares"
                                    } else {
                                        "Unable to open SMB directory"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { loadCurrentDirectory() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                } else if (entries.isEmpty()) {
                    item("empty") {
                        SmbInfoCard(
                            title = if (sharePickerMode) {
                                "No SMB shares found"
                            } else {
                                "This directory is empty"
                            },
                            subtitle = if (sharePickerMode) {
                                "No accessible disk shares on this host"
                            } else {
                                "No files or folders found"
                            }
                        )
                    }
                } else {
                    items(entries, key = { entry -> "${entry.isDirectory}:${entry.name}" }) { entry ->
                        SmbEntryRow(
                            entry = entry,
                            showAsShare = sharePickerMode,
                            onClick = {
                                if (sharePickerMode) {
                                    currentShare = entry.name
                                    currentSubPath = ""
                                    loadCurrentDirectory()
                                } else if (entry.isDirectory) {
                                    currentSubPath = joinSmbRelativePath(currentSubPath, entry.name)
                                    loadCurrentDirectory()
                                } else {
                                    val targetPath = joinSmbRelativePath(effectivePath().orEmpty(), entry.name)
                                    val targetSpec = buildSmbEntrySourceSpec(
                                        credentialsSpec.copy(share = currentShare),
                                        targetPath
                                    )
                                    onOpenRemoteSource(buildSmbRequestUri(targetSpec))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (authDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                authDialogVisible = false
                authDialogPasswordVisible = false
            },
            title = { Text("SMB authentication required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    authDialogErrorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedTextField(
                        value = authDialogUsername,
                        onValueChange = { authDialogUsername = it },
                        singleLine = true,
                        label = { Text("Username") }
                    )
                    OutlinedTextField(
                        value = authDialogPassword,
                        onValueChange = { authDialogPassword = it },
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = if (authDialogPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { authDialogPasswordVisible = !authDialogPasswordVisible }) {
                                Icon(
                                    imageVector = if (authDialogPasswordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (authDialogPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    }
                                )
                            }
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { authRememberPassword = !authRememberPassword },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = authRememberPassword,
                            onCheckedChange = { checked -> authRememberPassword = checked }
                        )
                        Text(
                            text = "Remember password",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                val hasCredentials = authDialogUsername.trim().isNotEmpty() || authDialogPassword.trim().isNotEmpty()
                TextButton(
                    enabled = hasCredentials,
                    onClick = {
                        val normalizedUsername = authDialogUsername.trim().ifBlank { null }
                        val normalizedPassword = authDialogPassword.trim().ifBlank { null }
                        sessionUsername = normalizedUsername
                        sessionPassword = normalizedPassword
                        if (authRememberPassword) {
                            onRememberSmbCredentials(
                                sourceId,
                                normalizedUsername,
                                normalizedPassword
                            )
                        }
                        authDialogVisible = false
                        authDialogPasswordVisible = false
                        loadCurrentDirectory()
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    authDialogVisible = false
                    authDialogPasswordVisible = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SmbParentDirectoryRow(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(SMB_ICON_BOX_SIZE),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Parent directory",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(SMB_ICON_GLYPH_SIZE)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "..",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = "Parent directory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmbEntryRow(
    entry: SmbBrowserEntry,
    showAsShare: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chipContainerColor = if (entry.isDirectory) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val chipContentColor = if (entry.isDirectory) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Box(
            modifier = Modifier
                .size(SMB_ICON_BOX_SIZE)
                .background(
                    color = chipContainerColor,
                    shape = RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.AudioFile,
                contentDescription = null,
                tint = chipContentColor,
                modifier = Modifier.size(SMB_ICON_GLYPH_SIZE)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showAsShare) {
                    "Share"
                } else if (entry.isDirectory) {
                    "Folder"
                } else {
                    "${inferSmbFormatLabel(entry.name)} • ${formatSmbFileSize(entry.sizeBytes)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmbInfoCard(
    title: String,
    subtitle: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmbLoadingCard(
    title: String,
    subtitle: String,
    logLines: List<String>
) {
    val logListState = rememberLazyListState()
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            logListState.animateScrollToItem(logLines.lastIndex)
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (logLines.isEmpty()) {
                    Text(
                        text = "[00] Waiting for SMB response...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = logListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logLines.size, key = { index -> "$index:${logLines[index]}" }) { index ->
                            Text(
                                text = logLines[index],
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun inferSmbFormatLabel(name: String): String {
    val ext = inferredPrimaryExtensionForName(name)
    return ext?.uppercase(Locale.ROOT) ?: "Unknown"
}

private fun formatSmbFileSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) return "$safeBytes B"
    val kb = safeBytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.ROOT, "%.1f GB", gb)
}
