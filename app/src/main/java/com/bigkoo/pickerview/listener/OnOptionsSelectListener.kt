package com.bigkoo.pickerview.listener

import android.view.View

/**
 * Small compatibility API for the original picker dependency.  The app only
 * needs the selected indexes, so the implementation is backed by a native
 * Material themed dialog in [com.bigkoo.pickerview.view.OptionsPickerView].
 */
fun interface OnOptionsSelectListener {
    fun onOptionsSelect(options1: Int, options2: Int, options3: Int, v: View?)
}
