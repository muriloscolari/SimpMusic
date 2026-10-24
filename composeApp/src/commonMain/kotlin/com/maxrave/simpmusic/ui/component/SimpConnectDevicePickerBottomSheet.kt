package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.simpconnect.DiscoveredDeviceDomain
import com.maxrave.domain.data.model.simpconnect.SimpConnectStatusDomain
import com.maxrave.simpmusic.ui.icon.Computer
import com.maxrave.simpmusic.ui.icon.Devices
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.Smartphone
import com.maxrave.simpmusic.ui.icon.Sync
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel

private val SpotifyGreen = Color(0xFF1DB954)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpConnectDevicePickerBottomSheet(
    onDismiss: () -> Unit,
    sharedViewModel: SharedViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val localDevice by sharedViewModel.simpConnectLocalDevice.collectAsStateWithLifecycle()
    val discoveredDevices by sharedViewModel.simpConnectDiscoveredDevices.collectAsStateWithLifecycle()
    val pendingDevice by sharedViewModel.simpConnectPendingDevice.collectAsStateWithLifecycle()
    val activeRemoteDevice by sharedViewModel.simpConnectActiveRemoteDevice.collectAsStateWithLifecycle()
    val status by sharedViewModel.simpConnectStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        sharedViewModel.refreshSimpConnectDevices()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Conectar a um dispositivo",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "SimpConnect P2P",
                        style = typo().bodySmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                IconButton(
                    onClick = { sharedViewModel.refreshSimpConnectDevices() },
                ) {
                    Icon(
                        imageVector = SimpIcons.Sync,
                        contentDescription = "Atualizar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Handoff Banner if remote is active
            AnimatedVisibility(visible = activeRemoteDevice != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SpotifyGreen.copy(alpha = 0.15f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = SimpIcons.Devices,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Controlando: ${activeRemoteDevice?.deviceName}",
                                    style = typo().bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "O som está saindo no outro aparelho",
                                    style = typo().bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                sharedViewModel.requestSimpConnectHandoff()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpotifyGreen,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Continuar tocando neste aparelho",
                                style = typo().labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Current Device Row
            Text(
                text = "ESTE DISPOSITIVO",
                style = typo().labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            DeviceRow(
                name = localDevice.deviceName,
                subtitle = when {
                    activeRemoteDevice != null -> "Toque para trazer a música de volta para este aparelho"
                    pendingDevice != null -> "Conectando a ${pendingDevice?.deviceName}…"
                    else -> "Tocando neste aparelho"
                },
                isDesktop = localDevice.platform == "desktop",
                isActive = activeRemoteDevice == null && pendingDevice == null,
                isLoading = pendingDevice != null,
                onClick = {
                    when {
                        activeRemoteDevice != null -> {
                            // Pull playback instead of just dropping the connection; disconnecting
                            // left the song playing on the other device with no way to continue.
                            sharedViewModel.requestSimpConnectHandoff()
                            onDismiss()
                        }
                        // Nothing to connect to yet: let the user abort a stuck handshake instead
                        // of waiting for the approval timeout.
                        pendingDevice != null -> sharedViewModel.disconnectSimpConnect()
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Discovered Devices
            Text(
                text = "DISPOSITIVOS NA REDE (WI-FI)",
                style = typo().labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (discoveredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = SimpIcons.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nenhum outro aparelho encontrado na rede",
                            style = typo().bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Abra o SimpMusic no PC ou outro celular na mesma Wi-Fi",
                            style = typo().bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(discoveredDevices, key = { it.deviceId }) { device ->
                        val isConnectedToThis = activeRemoteDevice?.deviceId == device.deviceId &&
                            status == SimpConnectStatusDomain.CONNECTED
                        val isPendingThis = pendingDevice?.deviceId == device.deviceId &&
                            (status == SimpConnectStatusDomain.CONNECTING || status == SimpConnectStatusDomain.WAITING_FOR_APPROVAL)
                        val isWaitingApproval = isPendingThis && status == SimpConnectStatusDomain.WAITING_FOR_APPROVAL
                        val isConnectingToThis = isPendingThis && status == SimpConnectStatusDomain.CONNECTING

                        // Order matters: a device is only "connected" once the handshake finished.
                        // Checking isConnectedToThis first made a still-connecting device look active.
                        val subText = when {
                            isWaitingApproval -> "Aguardando confirmação... (toque para cancelar)"
                            isConnectingToThis -> "Conectando... (toque para cancelar)"
                            isConnectedToThis -> "Conectado como controle remoto"
                            activeRemoteDevice != null -> "Toque para mover a reprodução para este aparelho"
                            else -> "Disponível na rede local (${device.hostAddress})"
                        }

                        DeviceRow(
                            name = device.deviceName,
                            subtitle = subText,
                            isDesktop = device.platform == "desktop",
                            isActive = isConnectedToThis,
                            isLoading = isConnectingToThis || isWaitingApproval,
                            onClick = {
                                when {
                                    isConnectedToThis -> sharedViewModel.disconnectSimpConnect()
                                    isPendingThis -> sharedViewModel.disconnectSimpConnect()
                                    else -> sharedViewModel.connectSimpConnectDevice(device)
                                }
                            },
                        )
                    }
                }
            }

            if (status == SimpConnectStatusDomain.FAILED || status == SimpConnectStatusDomain.REJECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (status == SimpConnectStatusDomain.REJECTED) {
                        "O outro aparelho recusou a conexão."
                    } else {
                        "Não foi possível conectar. Verifique se os dois aparelhos estão na mesma rede Wi-Fi."
                    },
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Conexão direta P2P local instantânea. Nenhum dado sai da sua rede.",
                style = typo().bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    subtitle: String,
    isDesktop: Boolean,
    isActive: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    val icon = if (isDesktop) SimpIcons.Computer else SimpIcons.Smartphone
    val tint = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isActive) SpotifyGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = typo().bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = typo().bodySmall.copy(fontSize = 12.sp),
                color = if (isActive) SpotifyGreen.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = SpotifyGreen,
            )
        } else if (isActive) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(SpotifyGreen, CircleShape),
            )
        }
    }
}

@Composable
fun SimpConnectAuthDialog(
    sharedViewModel: SharedViewModel,
) {
    val request by sharedViewModel.simpConnectIncomingAuthRequest.collectAsStateWithLifecycle()

    if (request != null) {
        AlertDialog(
            onDismissRequest = { request?.onResponse?.invoke(false) },
            title = {
                Text(
                    text = "Permitir controle remoto?",
                    style = typo().titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "O aparelho \"${request?.deviceName}\" deseja se conectar para controlar a música do SimpMusic nesta sessão.",
                    style = typo().bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { request?.onResponse?.invoke(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                ) {
                    Text("Permitir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { request?.onResponse?.invoke(false) }) {
                    Text("Recusar")
                }
            },
        )
    }
}
