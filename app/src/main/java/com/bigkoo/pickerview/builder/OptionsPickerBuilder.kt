package com.bigkoo.pickerview.builder

import android.content.Context
import com.bigkoo.pickerview.listener.OnOptionsSelectListener
import com.bigkoo.pickerview.view.OptionsPickerView

/** Builder-compatible facade replacing the unmaintained picker library. */
class OptionsPickerBuilder(
    private val context: Context,
    private val listener: OnOptionsSelectListener,
) {
    internal var cancelText: String = "取消"
    internal var submitText: String = "确定"
    internal var titleText: String? = null
    internal var titleColor: Int? = null
    internal var titleSize: Float? = null
    internal var submitColor: Int? = null
    internal var dialogMode: Boolean = false
    internal var outsideCancelable: Boolean = true
    internal var initialOptions: IntArray = intArrayOf()

    fun setCancelText(value: String) = apply { cancelText = value }
    fun setSubmitText(value: String) = apply { submitText = value }
    fun setTitleText(value: String) = apply { titleText = value }
    fun setTitleColor(value: Int) = apply { titleColor = value }
    fun setTitleSize(value: Int) = apply { titleSize = value.toFloat() }
    fun setSubmitColor(value: Int) = apply { submitColor = value }
    fun isDialog(value: Boolean) = apply { dialogMode = value }
    fun setOutSideCancelable(value: Boolean) = apply { outsideCancelable = value }
    fun setSelectOptions(vararg values: Int) = apply { initialOptions = values }

    fun build(): OptionsPickerView<String> = OptionsPickerView(
        context = context,
        listener = listener,
        cancelText = cancelText,
        submitText = submitText,
        titleText = titleText,
        titleColor = titleColor,
        titleSize = titleSize,
        submitColor = submitColor,
        dialogMode = dialogMode,
        outsideCancelable = outsideCancelable,
        initialOptions = initialOptions,
    )
}
