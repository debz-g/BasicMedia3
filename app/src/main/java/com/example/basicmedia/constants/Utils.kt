package com.example.basicmedia.constants

import android.view.View
import androidx.core.view.isVisible

class Utils {
    companion object {
        fun View.hide() {
            isVisible = false
        }

        fun View.show() {
            isVisible = true
        }

        const val CURRENT_POSITION = "CURRENT_POSITION"
    }
}