package com.janadhikar.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
    fun aspect(index: Int): Float = renderer.openPage(index).use { it.height.toFloat() / it.width }

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
 * 100% offline). Opens scrolled straight to [targetPage] — the exact page the
 * cited provision lives on, guaranteed, without relying on an external browser.
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

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
            Text(
                text = "p.$targetPage",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.DimGray,
            )
        }

        val d = doc
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.DirectiveYellow)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
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
private fun PdfPage(doc: PdfDoc, index: Int) {
    val context = LocalContext.current
    val widthPx = remember { context.resources.displayMetrics.widthPixels - 32 }
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    val aspect = remember(index) { runCatching { doc.aspect(index) }.getOrDefault(1.4f) }

    androidx.compose.runtime.LaunchedEffect(index) {
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
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f / aspect).background(Palette.PaperWhite),
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
