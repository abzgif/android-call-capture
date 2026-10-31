package com.callcapture.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Phone
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
import com.callcapture.app.data.model.CallType
import com.callcapture.app.ui.theme.CellularPurple
import com.callcapture.app.ui.theme.MessengerBlue
import com.callcapture.app.ui.theme.TelegramBlue
import com.callcapture.app.ui.theme.WhatsAppGreen

@Composable
fun CallTypeBadge(callType: CallType, modifier: Modifier = Modifier) {
    val (badgeColor, icon, label) = when (callType) {
        CallType.CELLULAR -> Triple(CellularPurple, Icons.Default.Phone, "Cellular")
        CallType.WHATSAPP, CallType.WHATSAPP_BUSINESS -> Triple(WhatsAppGreen, Icons.AutoMirrored.Filled.Chat, "WhatsApp")
        CallType.MESSENGER -> Triple(MessengerBlue, Icons.Default.Forum, "Messenger")
        CallType.TELEGRAM -> Triple(TelegramBlue, Icons.Default.Call, "Telegram")
        else -> Triple(Color(0xFF757575), Icons.Default.Call, callType.displayName)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = badgeColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
