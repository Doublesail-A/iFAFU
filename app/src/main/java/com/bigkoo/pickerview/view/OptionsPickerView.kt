package com.bigkoo.pickerview.view

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.bigkoo.pickerview.listener.OnOptionsSelectListener

/**
 * Native replacement for the small subset of Android-PickerView used by
 * iFAFU.  It deliberately keeps the old source API so all existing flows
 * (semester, time and electricity selectors) continue to work.
 */
class OptionsPickerView<T>(
    private val context: Context,
    private val listener: OnOptionsSelectListener,
    private val cancelText: String,
    private val submitText: String,
    private val titleText: String?,
    private val titleColor: Int?,
    private val titleSize: Float?,
    private val submitColor: Int?,
    private val dialogMode: Boolean,
    private val outsideCancelable: Boolean,
    initialOptions: IntArray,
) {
    private var options1: List<T> = emptyList()
    private var options2: List<List<T>> = emptyList()
    private var options3: List<List<List<T>>> = emptyList()
    private var selected = initialOptions.copyOf()
    private var dialog: AlertDialog? = null

    fun setNPicker(first: List<T>, second: List<T>, third: List<T>?) {
        options1 = first
        options2 = if (second.isEmpty()) emptyList() else listOf(second)
        options3 = if (third == null) emptyList() else listOf(listOf(third))
    }

    fun setPicker(first: List<T>, second: List<List<T>>) {
        options1 = first
        options2 = second
        options3 = emptyList()
    }

    fun setPicker(first: List<T>, second: List<List<T>>, third: List<List<List<T>>>) {
        options1 = first
        options2 = second
        options3 = third
    }

    fun setSelectOptions(vararg values: Int) {
        selected = values.copyOf()
    }

    fun show() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }
        val title = titleText?.takeIf { it.isNotBlank() }?.let {
            TextView(context).apply {
                text = it
                setTypeface(typeface, Typeface.BOLD)
                titleColor?.let(::setTextColor)
                titleSize?.let { size -> textSize = size }
                setPadding(0, dp(8), 0, dp(12))
            }
        }
        title?.let(root::addView)

        val spinners = ArrayList<Spinner>(3)
        fun addSpinner(values: List<T>): Spinner {
            val spinner = Spinner(context)
            spinner.adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                values.map { it.toString() },
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            root.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            spinners += spinner
            return spinner
        }

        val firstSpinner = addSpinner(options1)
        val secondSpinner = if (options2.isNotEmpty()) addSpinner(children(options2, 0)) else null
        val thirdSpinner = if (options3.isNotEmpty()) addSpinner(children3(options3, 0, 0)) else null

        val normalized = normalizeSelected(spinners.size)
        spinners.forEachIndexed { index, spinner ->
            spinner.setSelection(normalized[index].coerceAtLeast(0), false)
        }
        firstSpinner.onItemSelectedListener = SimpleSelectionListener { index ->
            if (secondSpinner != null) {
                updateSpinner(secondSpinner, children(options2, index))
                if (thirdSpinner != null) {
                    updateSpinner(thirdSpinner, children3(options3, index, secondSpinner.selectedItemPosition))
                }
            }
        }
        secondSpinner?.onItemSelectedListener = SimpleSelectionListener { index ->
            if (thirdSpinner != null) {
                updateSpinner(thirdSpinner, children3(options3, firstSpinner.selectedItemPosition, index))
            }
        }

        val builder = AlertDialog.Builder(context)
            .setView(root)
            .setNegativeButton(cancelText, null)
            .setPositiveButton(submitText, null)
            .setCancelable(outsideCancelable)
        dialog = builder.create().also { alert ->
            alert.setOnShowListener {
                submitColor?.let { color -> alert.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color) }
                alert.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    listener.onOptionsSelect(
                        firstSpinner.selectedItemPosition,
                        secondSpinner?.selectedItemPosition ?: 0,
                        thirdSpinner?.selectedItemPosition ?: 0,
                        null,
                    )
                    alert.dismiss()
                }
            }
            alert.setCanceledOnTouchOutside(outsideCancelable)
            alert.show()
        }
    }

    private fun normalizeSelected(count: Int): IntArray = IntArray(count) { index ->
        selected.getOrNull(index) ?: 0
    }

    private fun children(values: List<List<T>>, index: Int): List<T> =
        values.getOrNull(index).orEmpty()

    private fun children3(values: List<List<List<T>>>, first: Int, second: Int): List<T> =
        values.getOrNull(first)?.getOrNull(second).orEmpty()

    private fun updateSpinner(spinner: Spinner, values: List<T>) {
        spinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            values.map { it.toString() },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.setSelection(0, false)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private class SimpleSelectionListener(private val callback: (Int) -> Unit) :
        android.widget.AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) = callback(position)
    }
}
