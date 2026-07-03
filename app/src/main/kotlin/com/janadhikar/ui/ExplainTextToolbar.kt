package com.janadhikar.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * A text-selection toolbar that adds a "✦ Meaning" action next to Copy. When
 * tapped, it captures the selected text (by routing it through the clipboard,
 * the only handle Compose gives a custom toolbar) and calls [onExplain] so the
 * app can define that word in plain language.
 */
private class ExplainTextToolbar(
    private val view: View,
    private val onExplain: (String) -> Unit,
) : TextToolbar {

    private var actionMode: ActionMode? = null
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, ITEM_MEANING, 0, "✦ Meaning")
                onCopyRequested?.let { menu.add(0, ITEM_COPY, 1, android.R.string.copy) }
                onSelectAllRequested?.let { menu.add(0, ITEM_SELECT_ALL, 2, android.R.string.selectAll) }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    ITEM_COPY -> onCopyRequested?.invoke()
                    ITEM_SELECT_ALL -> onSelectAllRequested?.invoke()
                    ITEM_MEANING -> {
                        // Copy the selection so we can read what was selected.
                        onCopyRequested?.invoke()
                        val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(view.context)?.toString().orEmpty()
                        if (text.isNotBlank()) onExplain(text)
                    }
                }
                mode.finish()
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                status = TextToolbarStatus.Hidden
            }

            override fun onGetContentRect(mode: ActionMode, v: View, outRect: android.graphics.Rect) {
                outRect.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            }
        }
        actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        actionMode?.finish()
        actionMode = null
        status = TextToolbarStatus.Hidden
    }

    private companion object {
        const val ITEM_MEANING = 1
        const val ITEM_COPY = 2
        const val ITEM_SELECT_ALL = 3
    }
}

/** Provides the "Meaning" selection toolbar to everything in [content]. */
@Composable
fun ProvideMeaningToolbar(onExplain: (String) -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    val latest by rememberUpdatedState(onExplain)
    val toolbar = remember(view) { ExplainTextToolbar(view) { latest(it) } }
    CompositionLocalProvider(LocalTextToolbar provides toolbar, content = content)
}
