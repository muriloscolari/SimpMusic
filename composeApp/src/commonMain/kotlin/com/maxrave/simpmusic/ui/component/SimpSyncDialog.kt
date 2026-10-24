package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.maxrave.domain.repository.SyncDeviceInfo
import com.maxrave.simpmusic.viewModel.SyncViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpSyncDialog(
    onDismiss: () -> Unit,
    viewModel: SyncViewModel = koinViewModel(),
) {
    val status by viewModel.status.collectAsState()
    val freshRecoveryKey by viewModel.freshRecoveryKey.collectAsState()
    val clipboard = LocalClipboardManager.current

    var serverUrl by rememberSaveable(status.serverUrl) { mutableStateOf(status.serverUrl) }
    var email by rememberSaveable { mutableStateOf(status.email.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var recoveryKeyInput by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(status.deviceName ?: viewModel.defaultDeviceName()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Login, 1 = Register, 2 = Recover
    var showStoredRecoveryKey by rememberSaveable { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.openDialog()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "SimpSync Cloud (E2EE)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Sincronização criptografada ponta-a-ponta de configurações, contas, músicas curtidas, playlists locais e estatísticas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                status.error?.let { errMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (!status.authenticated) {
                    SecondaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Entrar") },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Criar Conta") },
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Recuperar") },
                        )
                    }

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Servidor SimpSync") },
                        placeholder = { Text("https://sync.muriloscolari.me") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (selectedTab == 2) {
                        OutlinedTextField(
                            value = recoveryKeyInput,
                            onValueChange = { recoveryKeyInput = it },
                            label = { Text("Chave de Recuperação (Recovery Key)") },
                            placeholder = { Text("XXXX-XXXX-XXXX-XXXX-...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = {
                            Text(if (selectedTab == 2) "Nova Senha Mestra (mín. 8 caracteres)" else "Senha Mestra (mín. 8 caracteres)")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Nome deste dispositivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Fechar")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !status.syncing &&
                                serverUrl.isNotBlank() &&
                                email.isNotBlank() &&
                                password.length >= 8 &&
                                (selectedTab != 2 || recoveryKeyInput.length >= 16),
                            onClick = {
                                when (selectedTab) {
                                    0 -> viewModel.login(serverUrl, email, password, deviceName)
                                    1 -> viewModel.register(serverUrl, email, password, deviceName)
                                    2 -> viewModel.recover(serverUrl, email, recoveryKeyInput, password, deviceName)
                                }
                            },
                        ) {
                            if (status.syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                when (selectedTab) {
                                    0 -> "Entrar e Sincronizar"
                                    1 -> "Criar Conta"
                                    else -> "Recuperar Conta"
                                },
                            )
                        }
                    }
                } else {
                    // Prominent Recovery Key banner right after registration or when requested
                    val keyToShow = freshRecoveryKey ?: status.recoveryKey?.takeIf { showStoredRecoveryKey }
                    if (keyToShow != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Guarde sua Chave de Recuperação (Recovery Key)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    text = "Se você esquecer sua Senha Mestra, esta chave é a única forma de recuperar seus dados criptografados no servidor:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                SelectionContainer {
                                    Text(
                                        text = keyToShow,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                                RoundedCornerShape(8.dp),
                                            )
                                            .padding(10.dp),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(keyToShow))
                                            copiedFeedback = true
                                        },
                                    ) {
                                        Text(if (copiedFeedback) "Copiado!" else "Copiar Chave")
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.dismissFreshRecoveryKey()
                                            showStoredRecoveryKey = false
                                            copiedFeedback = false
                                        },
                                    ) {
                                        Text("Ocultar")
                                    }
                                }
                            }
                        }
                    }

                    // Connected Account Overview
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = status.email ?: "Conta Conectada",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = status.serverUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = status.enabled,
                                    onCheckedChange = { viewModel.toggleEnabled(it) },
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = if (status.realtimeConnected) {
                                    "Canal em tempo real (WebSocket): Conectado"
                                } else if (status.enabled) {
                                    "Canal em tempo real (WebSocket): Aguardando / Reconectando"
                                } else {
                                    "Sincronização automática pausada"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status.realtimeConnected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )

                            val lastSyncFormatted = remember(status.lastSyncedAt) {
                                if (status.lastSyncedAt <= 0L) {
                                    "Nunca sincronizado"
                                } else {
                                    val dt = Instant.fromEpochMilliseconds(status.lastSyncedAt)
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                    "%02d/%02d/%04d %02d:%02d:%02d".format(
                                        dt.dayOfMonth,
                                        dt.monthNumber,
                                        dt.year,
                                        dt.hour,
                                        dt.minute,
                                        dt.second,
                                    )
                                }
                            }
                            Text(
                                text = "Última sincronização: $lastSyncFormatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { viewModel.synchronizeNow() },
                            enabled = !status.syncing,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (status.syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Sincronizando...")
                            } else {
                                Text("Sincronizar Agora")
                            }
                        }

                        if (status.recoveryKey != null && !showStoredRecoveryKey && freshRecoveryKey == null) {
                            OutlinedButton(
                                onClick = { showStoredRecoveryKey = true },
                            ) {
                                Text("Recovery Key")
                            }
                        }
                    }

                    // Active Devices list
                    if (status.devices.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Dispositivos Conectados (${status.devices.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (device in status.devices) {
                                DeviceRow(
                                    device = device,
                                    onRevoke = { viewModel.revokeDevice(device.id) },
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Sair da Conta")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: SyncDeviceInfo,
    onRevoke: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (device.current) "${device.deviceName} (Este dispositivo)" else device.deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (device.current) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = "Visto em: ${device.lastSeenAt.take(19).replace('T', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!device.current) {
                TextButton(
                    onClick = onRevoke,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Desconectar")
                }
            }
        }
    }
}
