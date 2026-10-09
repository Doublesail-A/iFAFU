package cn.ifafu.ifafu.ui.view

import android.app.Activity
import android.view.LayoutInflater
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.bo.Semester
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView

/** Material exposed menus retain independent year/term indices, including “全部”. */
class SemesterOptionPicker(private val context: Activity,
    private val onOptionSelectListener: (year: Int, term: Int) -> Unit) {
    private var semester: Semester? = null
    fun setSemester(value: Semester) { semester = value.copy() }
    fun show() {
        val options = semester ?: return
        if (options.yearList.isEmpty() || options.termList.isEmpty()) return
        var year = options.yearIndex.coerceIn(options.yearList.indices)
        var term = options.termIndex.coerceIn(options.termList.indices)
        val builder = MaterialAlertDialogBuilder(context)
        val view = LayoutInflater.from(builder.context).inflate(R.layout.dialog_semester_material, null)
        view.findViewById<MaterialAutoCompleteTextView>(R.id.semester_year).apply {
            setSimpleItems(options.yearList.toTypedArray())
            setText(options.yearList[year], false)
            setOnItemClickListener { _, _, index, _ -> year = index }
        }
        view.findViewById<MaterialAutoCompleteTextView>(R.id.semester_term).apply {
            setSimpleItems(options.termList.toTypedArray())
            setText(options.termList[term], false)
            setOnItemClickListener { _, _, index, _ -> term = index }
        }
        builder.setTitle("选择学年与学期").setView(view)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ -> onOptionSelectListener(year, term) }.show()
    }
}
