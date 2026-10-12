package com.example.oppodialer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

data class AppPalette(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val sub: Color,
    val nav: Color,
    val glass: Boolean
)

fun paletteFor(mode: AppMode): AppPalette = when (mode) {
    AppMode.LIGHT -> AppPalette(
        Color(0xFFF7F7F7), Color.White, Color.Black, Color.Gray, Color.White, false
    )
    AppMode.DARK -> AppPalette(
        Color(0xFF111111), Color(0xFF242424), Color.White, Color(0xFFAAAAAA), Color(0xFF1A1A1A), false
    )
    else -> AppPalette(
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
