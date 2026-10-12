package com.example.oppodialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

val OppoGreen = Color(0xFF1FB863)

enum class AppMode { LIGHT, DARK, GLASS, BLUR }

object Prefs {
    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)

    fun getMode(ctx: Context): AppMode {
        return try {
            AppMode.valueOf(sp(ctx).getString("mode", "LIGHT") ?: "LIGHT")
        } catch (e: Exception) {
            AppMode.LIGHT
        }
    }

    fun setMode(ctx: Context, m: AppMode) {
        sp(ctx).edit().putString("mode", m.name).apply()
    }

    fun getBlur(ctx: Context): Float = sp(ctx).getFloat("blur", 0.3f)

    fun setBlur(ctx: Context, v: Float) {
        sp(ctx).edit().putFloat("blur", v).apply()
    }
}

data class Palette(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val sub: Color,
    val nav: Color,
    val glass: Boolean
)

fun paletteFor(mode: AppMode): Palette = when (mode) {
    AppMode.LIGHT -> Palette(
        Color(0xFFF7F7F7), Color.White, Color.Black, Color.Gray, Color.White, false
    )
    AppMode.DARK -> Palette(
        Color(0xFF111111), Color(0xFF242424), Color.White, Color(0xFFAAAAAA), Color(0xFF1A1A1A), false
    )
    else -> Palette(
        Color.Transparent, Color(0x40FFFFFF), Color.White, Color(0xCCFFFFFF), Color(0x33FFFFFF), true
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(AppMode.LIGHT) }

fun wallpaperFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

fun wallVersionOf(ctx: Context): Long {
    val f = wallpaperFile(ctx)
    return if (f.exists()) f.lastModified() else 0L
}

fun saveWallpaper(ctx: Context, uri: Uri): Boolean {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return false
        val scale = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
            )
        } else bmp
        wallpaperFile(ctx).outputStream().use {
            out.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        true
    } catch (e: Throwable) {
        false
    }
}

fun blurBitmap(src: Bitmap, strength: Float): Bitmap {
    if (strength <= 0.01f) return src
    val factor = 1f / (1f + strength * 40f)
    val w = maxOf(8, (src.width * factor).toInt())
    val h = maxOf(8, (src.height * factor).toInt())
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val mw = minOf(src.width, w * 4)
    val mh = minOf(src.height, h * 4)
    val mid = Bitmap.createScaledBitmap(small, mw, mh, true)
    return Bitmap.createScaledBitmap(mid, src.width, src.height, true)
}

fun loadWallpaper(ctx: Context, strength: Float): Bitmap? {
    val f = wallpaperFile(ctx)
    if (!f.exists()) return null
    return try {
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        blurBitmap(bmp, strength)
    } catch (e: Throwable) {
        null
    }
}

@Composable
fun AppBackground(mode: AppMode, blurStrength: Float, wallVersion: Long) {
    val ctx = LocalContext.current
    val glass = mode == AppMode.GLASS || mode == AppMode.BLUR
    val strength = if (mode == AppMode.BLUR) blurStrength else 0f
    val bmp = remember(wallVersion, strength, glass) {
        if (glass) loadWallpaper(ctx, strength) else null
    }
    Box(Modifier.fillMaxSize()) {
        if (!glass) {
            Box(Modifier.fillMaxSize().background(paletteFor(mode).bg))
        } else {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF5B86E5), Color(0xFF36D1DC))
                        )
                    )
                )
            }
            Box(Modifier.fillMaxSize().background(Color(0x33000000)))
        }
    }
}

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)
    private var wallVersion by mutableLongStateOf(0L)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh++
        requestDefaultDialer()
    }

    private val imageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            saveWallpaper(this, uri)
            wallVersion = wallVersionOf(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent?.data?.schemeSpecificPart ?: ""
        wallVersion = wallVersionOf(this)
        setContent {
            DialerApp(
                initial = initial,
                refreshKey = refresh,
                wallVersion = wallVersion,
                onPick = { imageLauncher.launch("image/*") },
                onRemove = {
                    wallpaperFile(this).delete()
                    wallVersion = wallVersionOf(this)
                }
            )
        }
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
fun DialerApp(
    initial: String,
    refreshKey: Int,
    wallVersion: Long,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(Prefs.getMode(ctx)) }
    var blur by remember { mutableFloatStateOf(Prefs.getBlur(ctx)) }
    var appliedBlur by remember { mutableFloatStateOf(Prefs.getBlur(ctx)) }
    val p = paletteFor(mode)

    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = OppoGreen,
        selectedTextColor = OppoGreen,
        unselectedIconColor = p.sub,
        unselectedTextColor = p.sub,
        indicatorColor = OppoGreen.copy(alpha = 0.18f)
    )

    MaterialTheme {
        CompositionLocalProvider(LocalPalette provides p) {
            Box(Modifier.fillMaxSize()) {
                AppBackground(mode, appliedBlur, wallVersion)
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(containerColor = p.nav) {
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
                            NavigationBarItem(
                                selected = tab == 3, onClick = { tab = 3 }, colors = itemColors,
                                icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                label = { Text("Style") }
                            )
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (tab) {
                            0 -> KeypadScreen(initial)
                            1 -> RecentsScreen(refreshKey)
                            2 -> ContactsScreen(refreshKey)
                            else -> StyleScreen(
                                mode = mode,
                                onMode = {
                                    mode = it
                                    Prefs.setMode(ctx, it)
                                },
                                blur = blur,
                                onBlurLive = { blur = it },
                                onBlurDone = {
                                    appliedBlur = blur
                                    Prefs.setBlur(ctx, blur)
                                },
                                onPick = onPick,
                                onRemove = onRemove
                            )
                        }
                    }
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
