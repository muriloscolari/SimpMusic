package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxrave.domain.data.model.simpconnect.DiscoveredDeviceDomain
import com.maxrave.simpmusic.ui.icon.Devices
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun SimpConnectRemoteBadge(
    activeRemoteDevice: DiscoveredDeviceDomain?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = activeRemoteDevice != null,
        modifier = modifier,
    ) {
        if (activeRemoteDevice != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpotifyGreen.copy(alpha = 0.2f))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = SimpIcons.Devices,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Controlando: ${activeRemoteDevice.deviceName}",
                    style = typo().labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = SpotifyGreen,
                    maxLines = 1,
                )
            }
        }
    }
}
