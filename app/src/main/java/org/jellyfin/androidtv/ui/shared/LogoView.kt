package org.jellyfin.androidtv.ui.shared

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.util.isImagePrimarilyDark

/**
 * Displays a logo image with an adaptive shadow effect.
 * The shadow color adjusts based on whether the logo is primarily dark or light.
 * 
 * The logo is shown immediately with a default black shadow (works for most logos),
 * and the shadow color updates once the image brightness is analyzed.
 */
@Composable
fun LogoView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
    )
    LogoView(painter = painter, modifier = modifier)
}

@Composable
fun LogoView(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    val painter = rememberAsyncImagePainter(model = bitmap)
    LogoView(painter = painter, modifier = modifier)
}

@Composable
fun LogoView(
    painter: AsyncImagePainter,
    modifier: Modifier = Modifier
) {
    // Default to black shadow (color works for most logos)
    var shadowColor by remember { mutableStateOf(Color.Black) }
    val painterState = painter.state.collectAsState().value

    // Analyze brightness when image loads successfully
    LaunchedEffect(painterState) {
        if (painterState is AsyncImagePainter.State.Success) {
            try {
                val bitmap = painterState.result.image.toBitmap()
                val isDark = withContext(Dispatchers.Default) {
                    isImagePrimarilyDark(bitmap)
                }
                shadowColor = if (isDark) Color.White else Color.Black
            } catch (_: Exception) {
                // Keep default black shadow on error
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Show logo as soon as it's loaded (don't wait for analysis)
        if (painterState is AsyncImagePainter.State.Success) {
            // Draw shadow behind with blur effect
            val shadowModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blurShadow()
            } else {
                Modifier
            }
            
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(shadowColor, BlendMode.SrcIn),
                modifier = Modifier
                    .fillMaxSize()
                    .then(shadowModifier),
                contentScale = ContentScale.Fit,
                alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 1f else 0.7f
            )
            
            // Draw the actual logo on top
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Modifier.blurShadow(): Modifier {
    return graphicsLayer {
        renderEffect = android.graphics.RenderEffect
            .createBlurEffect(8f, 8f, android.graphics.Shader.TileMode.DECAL)
            .asComposeRenderEffect()
    }
}
