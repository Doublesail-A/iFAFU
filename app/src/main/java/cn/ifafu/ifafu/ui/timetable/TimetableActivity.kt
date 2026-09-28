package cn.ifafu.ifafu.ui.timetable

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.vo.Resource
import cn.ifafu.ifafu.bean.vo.TimetableVO
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.constant.WHAT_WRONG_WITH_IFAFU
import cn.ifafu.ifafu.databinding.TimetableActivityBinding
import cn.ifafu.ifafu.databinding.TimetableBottomDrawerBinding
import cn.ifafu.ifafu.databinding.TimetableContentBinding
import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.ui.common.BaseActivity
import cn.ifafu.ifafu.ui.main.MainActivity
import cn.ifafu.ifafu.ui.timetable_item.TimetableItemActivity
import cn.ifafu.ifafu.ui.timetable_setting.TimetableSettingActivity
import cn.ifafu.ifafu.ui.view.LoadingDialog
import cn.ifafu.ifafu.util.ChineseNumbers
import cn.ifafu.ifafu.util.TimetableWallpaper
import cn.ifafu.ifafu.util.ThemePreferences
import cn.ifafu.ifafu.util.ThemeManager
import cn.ifafu.ifafu.entity.GlobalSetting
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class TimetableActivity : BaseActivity(), View.OnClickListener {

    private val mViewModel: TimetableViewModel by viewModels()

    private var mCurrentWeek = 1
    private var restoredWeek: Int? = null
    private var themeRefreshPending = false
    private val mTimetablePageAdapter: TimetablePageAdapter by lazy {
        TimetablePageAdapter(
            onItemClickListener = { _, item ->
                val intent = TimetableItemActivity
                    .intentForCheck(this, item.tag as NewCourse)
                startActivityForResult(intent, Constants.ACTIVITY_SYLLABUS_ITEM)
            },
            onItemLongClickListener = { _, _ ->

            })
    }
    private val loadingDialog = LoadingDialog(this)
    private val mPreviewAdapter by lazy {
        TimetablePreviewAdapter { year, term ->
            mViewModel.switchOption(year, term)
        }
    }

    private lateinit var binding: TimetableActivityBinding
    private lateinit var contentBinding: TimetableContentBinding
    private lateinit var drawerBinding: TimetableBottomDrawerBinding
    private val minuteRefresh = object : Runnable {
        override fun run() {
            mTimetablePageAdapter.refreshUrgency()
            mViewModel.refreshCourseThemeSeed()
            contentBinding.root.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredWeek = savedInstanceState?.getInt("visible_week")
        binding = TimetableActivityBinding.inflate(layoutInflater)
        contentBinding = binding.content
        drawerBinding = binding.drawer
        setContentView(binding.root)
        setLightUiBar()

        initView()
        initViewModel()
    }

    override fun onResume() {
        super.onResume()
        contentBinding.root.removeCallbacks(minuteRefresh)
        mTimetablePageAdapter.refreshUrgency()
        contentBinding.root.postDelayed(minuteRefresh, 60_000L)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("visible_week", contentBinding.viewPager.currentItem)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        contentBinding.root.removeCallbacks(minuteRefresh)
        super.onPause()
    }

    private fun initViewModel() {
        mViewModel.message.observe(this, { snackbar(it) })
        mViewModel.timetableSetting.observe(this, { setSyllabusSetting(it) })
        mViewModel.nextCourseSeed.observe(this) { seed ->
            if (!themeRefreshPending && ThemePreferences.getTheme(this) == GlobalSetting.THEME_COURSE &&
                ThemePreferences.setCourseSeed(this, seed)) {
                themeRefreshPending = true
                contentBinding.root.post { recreate() }
            }
        }
        mViewModel.background.observe(this, { uri ->
            mTimetablePageAdapter.updateWallpaper(uri != null)
            contentBinding.ivBackground.alpha = if (ThemeManager.isNight(this)) 0.16f else 0.24f
            if (uri == null) {
                contentBinding.ivBackground.setImageBitmap(null)
            } else {
                Glide.with(this)
                    .load(uri)
                    .skipMemoryCache(true) // 不使用内存缓存
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // 不使用磁盘缓存
                    .into(contentBinding.ivBackground)
            }
        })
        mViewModel.timetableVO.observe(this, { res ->
            when (res) {
                is Resource.Success -> {
                    mTimetablePageAdapter.updateTimetable(res.data)
                    mViewModel.refreshCourseThemeSeed()
                    res.handleMessage { message ->
                        snackbar(message)
                    }
                    loadingDialog.cancel()
                }
                is Resource.Loading -> {
                    loadingDialog.show("刷新中")
                }
                is Resource.Failure -> {
                    snackbar(res.message)
                    mTimetablePageAdapter.updateTimetable(TimetableVO.create(emptyList()))
                    loadingDialog.cancel()
                }
            }
        })
        mViewModel.openingDay.observe(this, { openingDay ->
            mTimetablePageAdapter.updateOpeningDay(openingDay)
            mCurrentWeek = openingDay.getCurrentWeek()
            val week = if (mCurrentWeek <= 0) 1 else mCurrentWeek
            val position = restoredWeek ?: (week - 1)
            restoredWeek = null
            contentBinding.viewPager.setCurrentItem(position, false)
            showWeekString(position + 1)
            mViewModel.refreshCourseThemeSeed()
        })
        mViewModel.timetablePreviews.observe(this, { res ->
            when (res) {
                is Resource.Success -> {
                    mPreviewAdapter.data = res.data
                    mPreviewAdapter.notifyDataSetChanged()
                    loadingDialog.cancel()
                }
                is Resource.Loading -> {
                    loadingDialog.show(res.message)
                }
                is Resource.Failure -> {
                    snackbar(res.message)
                    loadingDialog.cancel()
                }
            }
        })
//        mViewModel.askCheckTerm.observe(this, EventObserver { op ->
//            showCheckTermDialog(op)
//        })
    }

    private fun initView() {
        contentBinding.tbSyllabus.setNavigationOnClickListener { onFinishActivity() }
        contentBinding.tbSyllabus.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_add -> addCourse()
                R.id.menu_refresh -> mViewModel.updateSyllabusFromNet()
                R.id.menu_more -> binding.drawerLayout.openDrawer(Gravity.BOTTOM)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        contentBinding.tbSyllabus.setOnLongClickListener {
            rollbackToCurrent()
            true
        }

        drawerBinding.settingMenu.setOnClickListener(this)
        drawerBinding.timeMenu.setOnClickListener(this)
        drawerBinding.backgroundMenu.setOnClickListener(this)
        drawerBinding.backgroundMenu.setOnLongClickListener {
            resetBackground()
            true
        }

        drawerBinding.weekSeekBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) contentBinding.viewPager.setCurrentItem(value.toInt(), true)
        }

        drawerBinding.timetablePreviewRv.adapter = mPreviewAdapter

        contentBinding.viewPager.adapter = mTimetablePageAdapter
        contentBinding.viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                drawerBinding.weekSeekBar.value = position.toFloat()
                showWeekString(position + 1)
            }
        })
    }

    private fun showWeekString(week: Int) {
        val weekInChinese = "第${ChineseNumbers.englishNumberToChinese((week.toString()))}周"
        val openingDay = mViewModel.openingDay.value
        if (openingDay == null) {
            contentBinding.tbSyllabus.subtitle = weekInChinese
            return
        }
        val currentWeek = openingDay.getCurrentWeek()
        val weekStr = when {
            currentWeek <= 0 && week == 1 && openingDay.isCurrentTerm -> "$weekInChinese · 假期中"
            currentWeek > 0 && week == currentWeek && openingDay.isCurrentTerm -> "$weekInChinese · 本周"
            !openingDay.isCurrentTerm -> "$weekInChinese · 其他学期"
            else -> weekInChinese
        }
        contentBinding.tbSyllabus.subtitle = weekStr
    }

    private fun setSyllabusSetting(setting: SyllabusSetting) {
        mTimetablePageAdapter.updateSetting(setting)
        mViewModel.refreshCourseThemeSeed()
        val maximum = (setting.weekCnt - 1).coerceAtLeast(1).toFloat()
        drawerBinding.weekSeekBar.valueTo = maximum
        drawerBinding.weekSeekBar.isEnabled = setting.weekCnt > 1
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.settingMenu -> {
                val intent = Intent(this, TimetableSettingActivity::class.java)
                startActivityForResult(intent, Constants.ACTIVITY_SYLLABUS_SETTING)
            }
            R.id.backgroundMenu -> {
                if (TimetableWallpaper.file(this).exists()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("课表背景")
                        .setItems(arrayOf("更换图片并取色", "移除背景")) { _, which ->
                            if (which == 0) pickBackground() else resetBackground()
                        }.show()
                } else pickBackground()
            }
            R.id.timeMenu -> {
                rollbackToCurrent()
                binding.drawerLayout.closeDrawers()
            }
        }
    }

    private fun addCourse() {
        val option = mPreviewAdapter.getSelected()
        if (option == null) {
            snackbar(WHAT_WRONG_WITH_IFAFU)
            return
        }
        val intent = TimetableItemActivity.intentForAdd(this, option.year, option.term)
        startActivityForResult(intent, Constants.ACTIVITY_SYLLABUS_ITEM)
    }

    /**
     * 返回当前周
     */
    private fun rollbackToCurrent() {
        contentBinding.viewPager.setCurrentItem((mCurrentWeek - 1).coerceAtLeast(0), true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
            if (binding.drawerLayout.isDrawerVisible(binding.drawer.root)) {
                binding.drawerLayout.closeDrawers()
            } else {
                onFinishActivity()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onFinishActivity() {
        when (intent.getIntExtra("from", -1)) {
            Constants.SYLLABUS_WIDGET, Constants.ACTIVITY_SPLASH -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }
        }
        finish()
    }

    private fun pickBackground() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, CODE_PICK)
    }

    private fun resetBackground() {
        TimetableWallpaper.clear(this)
        mViewModel.updateBackground()
        recreate()
    }

    private fun importBackground(uri: Uri) {
        lifecycleScope.launch {
            showLoading("正在为壁纸配色…")
            val result = withContext(Dispatchers.IO) {
                runCatching { TimetableWallpaper.import(this@TimetableActivity, uri) }
            }
            hideLoading()
              result.onSuccess {
                  binding.drawerLayout.closeDrawers()
                  // Activity recreation retains the ViewModel; reload its cached
                  // image URI so the newly selected wallpaper appears immediately.
                  mViewModel.updateBackground()
                  recreate()
            }.onFailure {
                Timber.e(it, "import timetable background failed")
                showToast("背景图片获取出错，请选择其他图片")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == CODE_PICK) {
                val uri = data?.data
                if (uri == null) {
                    showToast("背景图片获取出错")
                } else {
                    importBackground(uri)
                }
            } else if (requestCode == Constants.ACTIVITY_SYLLABUS_ITEM) {
                mViewModel.updateTimetableLocal()
            } else if (requestCode == Constants.ACTIVITY_SYLLABUS_SETTING) {
                mViewModel.updateTimetableSetting()
            } else {
                super.onActivityResult(requestCode, resultCode, data)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val CODE_PICK = 1001
    }

}
