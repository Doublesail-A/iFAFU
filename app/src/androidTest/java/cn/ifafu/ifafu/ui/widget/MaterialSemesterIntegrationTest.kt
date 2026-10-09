package cn.ifafu.ifafu.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.bo.Semester
import cn.ifafu.ifafu.ui.login.LoginActivity
import cn.ifafu.ifafu.ui.view.SemesterOptionPicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MaterialSemesterIntegrationTest {
    @Test fun materialMenusKeepYearAndTermSelectionAndCancellation() {
        val selected = AtomicReference<Pair<Int, Int>>()
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            fun open() = scenario.onActivity { activity ->
                SemesterOptionPicker(activity) { year, term -> selected.set(year to term) }.apply {
                    setSemester(Semester(mutableListOf("2025-2026", "2024-2025", "全部"), mutableListOf("1", "2", "全部")))
                    show()
                }
            }
            open()
            onView(withId(R.id.semester_year)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                assertTrue(view is MaterialAutoCompleteTextView)
                val root = view.rootView
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "semester-md3.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            onView(withId(R.id.semester_year)).perform(click())
            onView(withText("2024-2025")).inRoot(isPlatformPopup()).perform(click())
            onView(withId(R.id.semester_term)).perform(click())
            onView(withText("2")).inRoot(isPlatformPopup()).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
            assertEquals(1 to 1, selected.get())
            selected.set(null)
            open()
            onView(withId(R.id.semester_year)).check(matches(withText("2025-2026")))
            onView(withId(android.R.id.button2)).perform(click())
            assertNull(selected.get())
        }
    }
}
