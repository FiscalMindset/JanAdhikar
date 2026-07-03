package com.janadhikar.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.janadhikar.R
import com.janadhikar.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/** Thread-safe wrapper over PdfRenderer (only one page may be open at a time). */
private class PdfDoc(file: File) : Closeable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    val pageCount: Int get() = renderer.pageCount

    @Synchronized
    fun render(index: Int, widthPx: Int): Bitmap = renderer.openPage(index).use { page ->
        val h = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bmp
    }

    override fun close() {
        renderer.close(); fd.close()
    }
}

/**
 * In-app viewer for the official bare-act PDFs (bundled in assets, so it works
 * 100% offline). Lands on [targetPage] reliably (scrollToItem after load, not a
 * best-effort initial index), supports pinch-zoom + pan, and shows the live
 * page number — no external browser, guaranteed page.
 */
@Composable
fun PdfViewerScreen(
    assetName: String,
    targetPage: Int,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val doc by produceState<PdfDoc?>(initialValue = null, assetName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val out = File(context.cacheDir, assetName)
                if (!out.exists() || out.length() == 0L) {
                    context.assets.open("pdf/$assetName").use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                PdfDoc(out)
            }.getOrNull()
        }
    }

    val startIndex = (targetPage - 1).coerceAtLeast(0)
    val listState = rememberLazyListState()
    // Land on the cited page once the document is ready — reliable, unlike an
    // initial index that async page-height changes can throw off.
    LaunchedEffect(doc) { if (doc != null) listState.scrollToItem(startIndex) }

    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    // Pinch-zoom + pan state.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f; offsetY = 0f
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Palette.NearBlack)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹ " + stringResource(R.string.back),
                style = MaterialTheme.typography.titleLarge,
                color = Palette.DirectiveYellow,
                modifier = Modifier.clickable(onClick = onBack).testTag("pdf_back"),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.White,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val total = doc?.pageCount ?: 0
            Text(
                text = if (total > 0) "$currentPage / $total" else "p.$targetPage",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DimGray,
            )
            Spacer(Modifier.width(10.dp))
            // Zoom controls: − / reset% / +
            ZoomButton("−") { scale = (scale - 0.5f).coerceAtLeast(1f); if (scale == 1f) { offsetX = 0f; offsetY = 0f } }
            Text(
                text = "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = if (scale > 1f) Palette.DirectiveYellow else Palette.DimGray,
                modifier = Modifier
                    .clickable { scale = 1f; offsetX = 0f; offsetY = 0f }
                    .padding(horizontal = 6.dp),
            )
            ZoomButton("+") { scale = (scale + 0.5f).coerceAtMost(5f) }
        }

        val d = doc
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.DirectiveYellow)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offsetX; translationY = offsetY
                    }
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        // Double-tap toggles between fit-width and 2.5×.
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f
                        })
                    }
                    .padding(horizontal = 8.dp),
            ) {
                items(count = d.pageCount) { index ->
                    Spacer(Modifier.height(8.dp))
                    PdfPage(d, index)
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = Palette.DirectiveYellow,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int) {
    val context = LocalContext.current
    // Render once at ~2× width so text stays crisp when the user zooms in via
    // graphicsLayer, WITHOUT re-rendering on every zoom (that caused flicker).
    val widthPx = remember { (context.resources.displayMetrics.widthPixels - 32) * 2 }
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index) {
        bitmap = withContext(Dispatchers.IO) { runCatching { doc.render(index, widthPx) }.getOrNull() }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        // A4-ish placeholder so the list has stable heights before render.
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Palette.PaperWhite),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Palette.DirectiveYellow, strokeWidth = 2.dp)
        }
    }
}

/** Maps a statute name to its bundled PDF asset, or null if not bundled. */
fun pdfAssetFor(statuteName: String): String? = when {
    statuteName.contains("Constitution") -> "constitution.pdf"
    statuteName.contains("Nyaya") -> "bns.pdf"
    statuteName.contains("Nagarik") -> "bnss.pdf"
    statuteName.contains("Sakshya") -> "bsa.pdf"
    statuteName.contains("Motor") -> "motor_vehicles.pdf"
    else -> null
}
