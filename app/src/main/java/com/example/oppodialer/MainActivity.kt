package com.example.oppodialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OppoGreen = Color(0xFF1FB863)
val OppoBg = Color(0xFFF7F7F7)

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh++
        requestDefaultDialer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent?.data?.schemeSpecificPart ?: ""
        setContent { DialerApp(initial, refresh) }
        permLauncher.launch(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !rm.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
        }
    }
}

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

@Composable
fun DialerApp(initial: String, refreshKey: Int) {
    var tab by remember { mutableIntStateOf(0) }
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = OppoGreen,
        selectedTextColor = OppoGreen,
        indicatorColor = Color(0xFFE3F6EC)
    )
    MaterialTheme {
        Scaffold(
            containerColor = OppoBg,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 }, colors = itemColors,
                        icon = { Icon(Icons.Filled.Dialpad, contentDescription = null) },
                        label = { Text("Keypad") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 }, colors = itemColors,
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("Recents") }
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 }, colors = itemColors,
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("Contacts") }
                    )
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> KeypadScreen(initial)
                    1 -> RecentsScreen(refreshKey)
                    else -> ContactsScreen(refreshKey)
                }
            }
        }
    }
}

val keySubs = mapOf(
    "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
    "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+"
)

@Composable
fun KeyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 28.sp, color = Color.Black)
            Text(keySubs[label] ?: "", fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
fun KeypadScreen(initial: String) {
    val ctx = LocalContext.current
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
            color = Color.Black,
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
                Text("⌫", fontSize = 26.sp, color = Color.DarkGray)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RecentsScreen(refreshKey: Int) {
    val ctx = LocalContext.current
    val calls = remember(refreshKey) { loadCalls(ctx) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Recents",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(20.dp)
        )
        LazyColumn {
            items(calls) { c ->
                val missed = c.type == CallLog.Calls.MISSED_TYPE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { placeCall(ctx, c.number) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (c.name.isNotEmpty()) c.name else c.number,
                            fontSize = 17.sp,
                            color = if (missed) Color(0xFFE53935) else Color.Black
                        )
                        if (c.name.isNotEmpty()) {
                            Text(c.number, fontSize = 13.sp, color = Color.Gray)
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
    val contacts = remember(refreshKey) { loadContacts(ctx) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Contacts",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(20.dp)
        )
        LazyColumn {
            items(contacts) { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { placeCall(ctx, c.number) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 17.sp, color = Color.Black)
                        Text(c.number, fontSize = 13.sp, color = Color.Gray)
                    }
                    Icon(Icons.Filled.Call, contentDescription = null, tint = OppoGreen)
                }
            }
        }
    }
}
