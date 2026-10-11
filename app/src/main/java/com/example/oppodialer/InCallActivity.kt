package com.example.oppodialer

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class InCallActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CallManager.onChange = {
            tick++
            if (CallManager.call == null) finish()
        }
        if (CallManager.call == null) {
            finish()
            return
        }
        setContent { InCallScreen(tick) }
    }

    override fun onDestroy() {
        CallManager.onChange = null
        super.onDestroy()
    }
}

@Composable
fun InCallScreen(tick: Int) {
    val ctx = LocalContext.current
    val mode = remember { Prefs.getMode(ctx) }
    val blur = remember { Prefs.getBlur(ctx) }
    val wallVersion = remember { wallVersionOf(ctx) }
    val p = paletteFor(mode)

    val c = CallManager.call
    val number = c?.details?.handle?.schemeSpecificPart ?: "Unknown"
    val ringing = c?.state == Call.STATE_RINGING
    val status = when (c?.state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling..."
        Call.STATE_ACTIVE -> "In call"
        Call.STATE_HOLDING -> "On hold"
        else -> ""
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground(mode, blur, wallVersion)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(100.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(p.surface)
                    .padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(number, fontSize = 34.sp, color = p.text)
                Spacer(Modifier.height(10.dp))
                Text(status, fontSize = 16.sp, color = p.sub)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (ringing) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1FB863))
                            .clickable { CallManager.answer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .clickable { CallManager.hangup() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}
