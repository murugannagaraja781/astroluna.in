package com.astroluna.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * Shows an error alert dialog.
 * @param message The error message to display.
 * @param title The title of the alert (default: "Error").
 */
fun Context.showErrorAlert(message: String, title: String = "Error") {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        .setCancelable(true)
        .show()
}
