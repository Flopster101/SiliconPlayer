package com.flopster101.siliconplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties

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
            OutlinedTextField(
                value = folderName,
                onValueChange = onFolderNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { RequiredFieldLabel("Folder name") },
                shape = MaterialTheme.shapes.extraLarge,
                colors = networkDialogTextFieldColors()
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
