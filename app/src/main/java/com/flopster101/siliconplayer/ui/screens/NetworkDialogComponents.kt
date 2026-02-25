package com.flopster101.siliconplayer.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha
import kotlin.math.roundToInt

@Composable
internal fun NetworkCreateFolderDialog(
    isEditing: Boolean,
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit folder" else "Create folder") },
        text = {
            NetworkDialogScrollableContent {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = onFolderNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { RequiredFieldLabel("Folder name") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = folderName.trim().isNotEmpty(),
                onClick = onConfirm
            ) {
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun NetworkRemoteSourceDialog(
    isEditing: Boolean,
    sourceName: String,
    onSourceNameChange: (String) -> Unit,
    sourcePath: String,
    onSourcePathChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit remote source" else "Add remote source") },
        text = {
            NetworkDialogScrollableContent {
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = onSourceNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name (optional)") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = sourcePath,
                    onValueChange = onSourcePathChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { RequiredFieldLabel("URL or path") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = sourcePath.trim().isNotEmpty(),
                onClick = onConfirm
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun NetworkSmbSourceDialog(
    isEditing: Boolean,
    sourceName: String,
    onSourceNameChange: (String) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    share: String,
    onShareChange: (String) -> Unit,
    path: String,
    onPathChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit SMB share" else "Add SMB share") },
        text = {
            NetworkDialogScrollableContent {
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = onSourceNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name (optional)") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { RequiredFieldLabel("Host") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = share,
                    onValueChange = onShareChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Share (optional)") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = onPathChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Path inside share (optional)") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Username (optional)") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password (optional)") },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                }
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.trim().isNotEmpty(),
                onClick = onConfirm
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun NetworkHttpSourceDialog(
    isEditing: Boolean,
    sourceName: String,
    onSourceNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    treatAsRoot: Boolean,
    onTreatAsRootChange: (Boolean) -> Unit,
    isUrlValid: Boolean,
    showUrlError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit HTTP/HTTPS server" else "Add HTTP/HTTPS server") },
        text = {
            NetworkDialogScrollableContent {
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = onSourceNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name (optional)") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { RequiredFieldLabel("Server URL") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Username (optional)") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password (optional)") },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                }
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = networkDialogTextFieldColors()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTreatAsRootChange(!treatAsRoot) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = treatAsRoot,
                        onCheckedChange = { checked -> onTreatAsRootChange(checked) }
                    )
                    Text(
                        text = "Treat URL directory as browser root",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (showUrlError) {
                    Text(
                        text = "Enter a valid http:// or https:// URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isUrlValid,
                onClick = onConfirm
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun networkDialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@Composable
private fun NetworkDialogScrollableContent(
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxHeight = configuration.screenHeightDp.dp * 0.58f
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = scrollState,
        label = "networkDialogScrollbarAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .onSizeChanged { viewportHeightPx = it.height }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )

        if (viewportHeightPx > 0 && scrollState.maxValue > 0) {
            val viewportHeightDp = with(density) { viewportHeightPx.toDp() }
            NetworkDialogScrollbar(
                scrollState = scrollState,
                viewportHeightPx = viewportHeightPx,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(4.dp)
                    .height(viewportHeightDp)
                    .offset(x = (-2).dp)
                    .graphicsLayer(alpha = scrollbarAlpha)
            )
        }
    }
}

@Composable
private fun NetworkDialogScrollbar(
    scrollState: ScrollState,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val totalContentPx = viewportHeightPx + scrollState.maxValue
    if (totalContentPx <= 0) return
    val thumbHeightPx = (viewportHeightPx.toFloat() * (viewportHeightPx.toFloat() / totalContentPx.toFloat()))
        .coerceAtLeast(18f)
        .coerceAtMost(viewportHeightPx.toFloat())
    val maxOffsetPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val offsetFraction = if (scrollState.maxValue == 0) {
        0f
    } else {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }
    val thumbOffsetPx = maxOffsetPx * offsetFraction
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
    val thumbOffsetDp = with(density) { thumbOffsetPx.roundToInt().toDp() }

    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            shape = RoundedCornerShape(999.dp)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = thumbOffsetDp)
                .height(thumbHeightDp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(999.dp)
                )
        )
    }
}

@Composable
private fun RequiredFieldLabel(text: String) {
    Text(
        text = buildAnnotatedString {
            append(text)
            append(" ")
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append("*")
            }
        }
    )
}
