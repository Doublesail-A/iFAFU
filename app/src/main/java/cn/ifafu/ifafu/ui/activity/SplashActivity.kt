package cn.ifafu.ifafu.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.db.dao.UserDao
import cn.ifafu.ifafu.ui.common.BaseActivity
import cn.ifafu.ifafu.ui.examlist.ExamListActivity
import cn.ifafu.ifafu.ui.login.LoginActivity
import cn.ifafu.ifafu.ui.main.MainActivity
import cn.ifafu.ifafu.ui.timetable.TimetableActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Routing stays behind Android's launch screen; there is no second splash layout. */
@AndroidEntryPoint
class SplashActivity : BaseActivity() {
    @Inject lateinit var userDao: UserDao

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { true }
        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) { userDao.getUsingUser() }
            val destination = if (user == null) LoginActivity::class.java else when (intent.getIntExtra("from", -1)) {
                Constants.ACTIVITY_SYLLABUS, Constants.SYLLABUS_WIDGET -> TimetableActivity::class.java
                Constants.ACTIVITY_EXAM -> ExamListActivity::class.java
                else -> MainActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, destination).apply {
                putExtra(Constants.EXTRA_ORIGIN, Constants.ACTIVITY_SPLASH)
            })
            overridePendingTransition(0, 0)
            finish()
        }
    }
}
