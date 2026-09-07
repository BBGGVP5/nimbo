package com.danila.nimbo.utils

import android.content.Context
import android.content.ClipboardManager

fun getClipboardText(context: Context): String? {

    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val item = clipboard.primaryClip?.getItemAt(0)

    return item?.text?.toString()

}
