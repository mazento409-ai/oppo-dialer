package com.example.oppodialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun placeCall(ctx: Context, number: String) {
    try {
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (e: SecurityException) {
    }
}

data class CallItem(val name: String, val number: String, val type: Int)
data class ContactItem(val name: String, val number: String)

fun loadCalls(ctx: Context): List<CallItem> {
    if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val list = mutableListOf<CallItem>()
    try {
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
            null, null, CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext() && list.size < 100) {
                list.add(CallItem(c.getString(0) ?: "", c.getString(1) ?: "", c.getInt(2)))
            }
        }
    } catch (e: Exception) {
    }
    return list
}

fun loadContacts(ctx: Context): List<ContactItem> {
    if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val list = mutableListOf<ContactItem>()
    try {
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                list.add(ContactItem(c.getString(0) ?: "", c.getString(1) ?: ""))
            }
        }
    } catch (e: Exception) {
    }
    return list
}

val keySubs = mapOf(
    "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
    "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+"
)

@Composable
fun KeyButton(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(p.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 28.sp, color = p.text)
            Text(keySubs[label] ?: "", fontSize = 9.sp, color = p.sub)
        }
    }
}

@Composable
fun KeypadScreen(initial: String) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var number by remember { mutableStateOf(initial) }
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            number,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            color = p.text,
            modifier = Modifier.padding(16.dp)
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { k -> KeyButton(k) { number += k } }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(76.dp))
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(OppoGreen)
                    .clickable { if (number.isNotEmpty()) placeCall(ctx, number) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White)
            }
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable { if (number.isNotEmpty()) number = number.dropLast(1) },
                contentAlignment = Alignment.Center
            ) {
                Text("⌫", fontSize = 26.sp, color = p.sub)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RecentsScreen(refreshKey: Int) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val calls = remember(refreshKey) { loadCalls(ctx) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Recents",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = p.text,
            modifier = Modifier.padding(20.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(calls) { c ->
                val missed = c.type == CallLog.Calls.MISSED_TYPE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.surface)
                        .clickable { placeCall(ctx, c.number) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (c.name.isNotEmpty()) c.name else c.number,
                            fontSize = 17.sp,
                            color = if (missed) Color(0xFFE53935) else p.text
                        )
                        if (c.name.isNotEmpty()) {
                            Text(c.number, fontSize = 13.sp, color = p.sub)
                        }
                    }
                    Icon(Icons.Filled.Call, contentDescription = null, tint = OppoGreen)
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(refreshKey: Int) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val contacts = remember(refreshKey) { loadContacts(ctx) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Contacts",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = p.text,
            modifier = Modifier.padding(20.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(contacts) { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.surface)
                        .clickable { placeCall(ctx, c.number) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 17.sp, color = p.text)
                        Text(c.number, fontSize = 13.sp, color = p.sub)
                    }
                    Icon(Icons.Filled.Call, contentDescription = null, tint = OppoGreen)
                }
            }
        }
    }
}

@Composable
fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) OppoGreen else p.surface)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else p.text, fontSize = 16.sp)
    }
}

@Composable
fun StyleScreen(
    mode: AppMode,
    onMode: (AppMode) -> Unit,
    blur: Float,
    onBlurLive: (Float) -> Unit,
    onBlurDone: () -> Unit,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Style", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = p.text)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeChip("Light", mode == AppMode.LIGHT, Modifier.weight(1f)) { onMode(AppMode.LIGHT) }
            ModeChip("Dark", mode == AppMode.DARK, Modifier.weight(1f)) { onMode(AppMode.DARK) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeChip("Glass", mode == AppMode.GLASS, Modifier.weight(1f)) { onMode(AppMode.GLASS) }
            ModeChip("Blur", mode == AppMode.BLUR, Modifier.weight(1f)) { onMode(AppMode.BLUR) }
        }

        Spacer(Modifier.height(28.dp))
        Text("Wallpaper", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = p.text)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = OppoGreen),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose wallpaper", color = Color.White) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Remove wallpaper", color = p.text) }

        Spacer(Modifier.height(28.dp))
        Text(
            "Blur strength: " + (blur * 100).toInt() + "%",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = p.text
        )
        Slider(
            value = blur,
            onValueChange = onBlurLive,
            onValueChangeFinished = onBlurDone,
            valueRange = 0f..0.5f,
            colors = SliderDefaults.colors(
                thumbColor = OppoGreen,
                activeTrackColor = OppoGreen
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The wallpaper shows in Glass and Blur modes. Blur strength applies in Blur mode.",
            fontSize = 13.sp,
            color = p.sub
        )
    }
}
