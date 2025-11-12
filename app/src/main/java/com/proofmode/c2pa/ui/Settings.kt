@file:OptIn(ExperimentalMaterial3Api::class)

package com.proofmode.c2pa.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ShareLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.proofmode.c2pa.R
import com.proofmode.c2pa.c2pa_signing.IPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: IPreferencesManager
) : ViewModel() {

    val userName: StateFlow<String> = preferencesManager.userName
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userEmail: StateFlow<String> = preferencesManager.userEmail
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isLocationSharingEnabled: StateFlow<Boolean> = preferencesManager.locationSharing
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun saveUserName(name: String) {
        viewModelScope.launch {
            preferencesManager.setUserName(name)
        }
    }

    fun saveUserEmail(email: String) {
        viewModelScope.launch {
            preferencesManager.setUserEmail(email)
        }
    }

    fun saveLocationSharing(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setLocationSharing(enabled)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    val currentName by viewModel.userName.collectAsStateWithLifecycle()
    val currentEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val isLocationEnabled by viewModel.isLocationSharingEnabled.collectAsStateWithLifecycle()
    var showRationaleDialog by remember { mutableStateOf(false) }

    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ) {
        if (it.values.all { perm-> perm }) {
            viewModel.saveLocationSharing(true)
        }
    }

    BackHandler(enabled = true) {
        onNavigateBack?.invoke()
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(stringResource(R.string.settings))
        })
    }) {
        padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            HorizontalDivider()
            EditableSettingRow(
                label = stringResource(R.string.name),
                value = currentName,
                onSave = viewModel::saveUserName,
                leadingIcon = Icons.Default.Person,
                contentDescription = stringResource(R.string.user_name)
            )
            HorizontalDivider()
            EditableSettingRow(
                label = "Email",
                value = currentEmail,
                onSave = viewModel::saveUserEmail,
                leadingIcon = Icons.Default.Email,
                contentDescription = stringResource(R.string.user_email)
            )
            HorizontalDivider()
            SwitchSettingRow(
                label = stringResource(R.string.enable_location),
                isChecked = isLocationEnabled,
                onCheckedChange = { shouldBeEnabled ->
                    if (shouldBeEnabled) {
                        if (locationPermissionsState.allPermissionsGranted) {
                            viewModel.saveLocationSharing(true)
                        } else {
                            if (locationPermissionsState.shouldShowRationale) {
                                showRationaleDialog = true
                            } else {
                                locationPermissionsState.launchMultiplePermissionRequest()
                            }
                        }
                    } else {
                        viewModel.saveLocationSharing(false)
                    }
                },
                leadingIcon = Icons.Outlined.ShareLocation,
                contentDescription = stringResource(R.string.toggle_sharing_location)
            )
            HorizontalDivider()
        }

        if (showRationaleDialog) {
            LocationPermissionRationaleDialog(
                onConfirm = {
                    showRationaleDialog = false
                    locationPermissionsState.launchMultiplePermissionRequest()
                },
                onDismiss = { showRationaleDialog = false }
            )
        }
    }
}

@Composable
private fun LocationPermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_permission_required)) },
        text = { Text(stringResource(R.string.location_permission_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.continue_))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun EditableSettingRow(
    label: String,
    value: String,
    onSave: (String) -> Unit,
    leadingIcon: ImageVector,
    contentDescription: String
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editedValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (!isEditing) {
            editedValue = value
        }
    }

    ListItem(
        headlineContent = { Text(label, fontWeight = FontWeight.Bold) },
        leadingContent = { Icon(imageVector = leadingIcon, contentDescription = contentDescription) },
        supportingContent = {
            if (isEditing) {
                OutlinedTextField(
                    value = editedValue,
                    onValueChange = { editedValue = it },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    singleLine = true
                )
            } else {
                Text(text = value.ifEmpty { "Not set" })
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    IconButton(onClick = {
                        onSave(editedValue)
                        isEditing = false
                        Toast.makeText(context, "$label Saved", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save $label")
                    }
                } else {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit $label")
                    }
                }
            }
        },
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SwitchSettingRow(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: ImageVector,
    contentDescription: String
) {
    ListItem(
        headlineContent = { Text(label, fontWeight = FontWeight.Bold) },
        leadingContent = { Icon(imageVector = leadingIcon, contentDescription = contentDescription) },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = null
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!isChecked) }
    )
}
