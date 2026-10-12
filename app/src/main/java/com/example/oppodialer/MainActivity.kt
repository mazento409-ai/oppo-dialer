package com.example.oppodialer

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
                                onMode = { newMode ->
                                    mode = newMode
                                    Prefs.setMode(ctx, newMode)
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
