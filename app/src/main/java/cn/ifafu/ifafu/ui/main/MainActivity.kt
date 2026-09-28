package cn.ifafu.ifafu.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.vo.MenuVO
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.databinding.MainActivityBinding
import cn.ifafu.ifafu.entity.GlobalSetting
import cn.ifafu.ifafu.ui.common.BaseActivity
import cn.ifafu.ifafu.ui.common.dialog.mutiluser.MultiUserDialog
import cn.ifafu.ifafu.ui.login.LoginActivity
import cn.ifafu.ifafu.ui.main.vo.CheckoutResult
import cn.ifafu.ifafu.ui.main.vo.DeleteResult
import cn.ifafu.ifafu.ui.score.ScoreViewModel
import cn.ifafu.ifafu.ui.setting.SettingActivity
import cn.ifafu.ifafu.ui.view.LoadingDialog
import cn.ifafu.ifafu.util.ButtonUtils
import cn.ifafu.ifafu.util.ThemeManager
import cn.ifafu.ifafu.util.ThemePreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: MainActivityBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val viewModel: MainViewModel by viewModels()
    private val scoreViewModel: ScoreViewModel by viewModels()
    private val menuHandler by lazy { MainMenuHandler(this) }
    private val loadingDialog by lazy { LoadingDialog(this) }
    private var courseThemeRefreshPending = false

    fun updateCourseThemeSeed(seed: Int) {
        if (courseThemeRefreshPending ||
            ThemePreferences.getTheme(this) != GlobalSetting.THEME_COURSE ||
            !ThemePreferences.setCourseSeed(this, seed)
        ) return
        courseThemeRefreshPending = true
        binding.root.post { recreate() }
    }

    private val multiUserDialog by lazy {
        MultiUserDialog(
            context = this,
            onAddClick = {
                startLoginActivityForResult()
            },
            onItemClick = { user ->
                val content = layoutInflater.inflate(R.layout.dialog_user_detail, null)
                content.findViewById<EditText>(R.id.et_password).setText(user.password)
                MaterialAlertDialogBuilder(this)
                    .setTitle(user.name)
                    .setMessage("学号 ${user.account}")
                    .setView(content)
                    .setNegativeButton("删除账号") { _, _ -> viewModel.deleteUser(user) }
                    .setPositiveButton("切换账号") { _, _ -> viewModel.checkoutTo(user) }
                    .show()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLightUiBar()
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment
        navController = host.navController
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.fragment_home, R.id.nav_score, R.id.nav_elective),
            binding.drawerMain,
        )
        binding.topAppBar.setupWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)
        binding.navigationDrawer.setNavigationItemSelectedListener(::onDrawerItemSelected)
        binding.topAppBar.setOnMenuItemClickListener(::onToolbarMenuItemSelected)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevel = destination.id == R.id.fragment_home ||
                destination.id == R.id.fragment_score_list ||
                destination.id == R.id.fragment_elective
            binding.bottomNavigation.isVisible = topLevel
            binding.topAppBar.title = when (destination.id) {
                R.id.fragment_score_detail -> "成绩详情"
                R.id.fragment_score_filter -> "智育分筛选"
                else -> "iFAFU"
            }
            binding.topAppBar.menu.clear()
            if (destination.id == R.id.fragment_score_list) {
                binding.topAppBar.inflateMenu(R.menu.score_list)
            }
        }

        bindUserState()
        bindAccountDialogs()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        if (binding.drawerMain.isDrawerOpen(GravityCompat.START)) {
            binding.drawerMain.closeDrawer(GravityCompat.START)
            return
        }
        val destination = navController.currentDestination?.id
        val isTopLevel = destination == R.id.fragment_home ||
            destination == R.id.fragment_score_list ||
            destination == R.id.fragment_elective
        if (!isTopLevel && navController.navigateUp()) return
        if (ButtonUtils.isFastDoubleClick()) {
            finish()
        } else {
            Toast.makeText(this, R.string.back_again, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.hideMultiUserDialog()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            Constants.REQUEST_LOGIN -> if (resultCode == RESULT_OK) {
                viewModel.addAccountSuccess()
            }
            Constants.ACTIVITY_SETTING -> if (resultCode == Activity.RESULT_OK) {
                ThemeManager.apply(this)
                recreate()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun bindUserState() {
        val header = binding.navigationDrawer.getHeaderView(0)
        val name = header.findViewById<android.widget.TextView>(R.id.tv_drawer_name)
        val account = header.findViewById<android.widget.TextView>(R.id.tv_drawer_account)
        viewModel.user.observe(this) { user ->
            if (user == null) return@observe
            name.text = user.name.ifBlank { "iFAFU" }
            account.text = user.account
        }
        viewModel.isShowComment.observe(this) { show ->
            binding.navigationDrawer.menu.findItem(R.id.menu_comment)?.isVisible = show == true
        }
    }

    private fun bindAccountDialogs() {
        viewModel.showMultiUserDialog.observe(this) { show ->
            if (show == true) {
                viewModel.getUsersLiveData().observe(this) { users ->
                    multiUserDialog.setUsers(users)
                }
                multiUserDialog.show()
            } else {
                multiUserDialog.cancel()
            }
        }
        viewModel.deleteResult.observe(this) { result ->
            when (result) {
                is DeleteResult.NeedLogin -> {
                    loadingDialog.cancel()
                    startLoginActivityForResult()
                    finish()
                }
                is DeleteResult.Success -> {
                    showToast("删除成功")
                    loadingDialog.cancel()
                }
                is DeleteResult.Ing -> loadingDialog.show("删除中")
                is DeleteResult.CheckTo -> {
                    showToast("成功切换到${result.user.account}")
                    multiUserDialog.cancel()
                    loadingDialog.cancel()
                }
            }
        }
        viewModel.checkoutResult.observe(this) { result ->
            when (result) {
                is CheckoutResult.Ing -> loadingDialog.show("切换中")
                is CheckoutResult.Success -> {
                    showToast("成功切换到${result.user.account}")
                    multiUserDialog.cancel()
                    loadingDialog.cancel()
                }
                is CheckoutResult.Failure -> {
                    showToast(result.message)
                    loadingDialog.cancel()
                }
            }
        }
    }

    private fun onDrawerItemSelected(item: MenuItem): Boolean {
        binding.drawerMain.closeDrawer(GravityCompat.START)
        when (item.itemId) {
            R.id.menu_user_management -> viewModel.showMultiUserDialog()
            R.id.menu_setting -> startActivityForResult(
                Intent(this, SettingActivity::class.java),
                Constants.ACTIVITY_SETTING,
            )
            R.id.menu_upgrade -> viewModel.upgradeApp()
            else -> menuHandler.handle(MenuVO(item.itemId, 0, item.title.toString()))
        }
        return true
    }

    private fun onToolbarMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_refresh -> {
                scoreViewModel.refreshScoreList()
                true
            }
            R.id.menu_filter -> {
                val semester = scoreViewModel.semester.value
                if (semester == null) {
                    showToast("未找到学期信息")
                } else {
                    navController.navigate(
                        R.id.action_fragment_score_list_to_fragment_score_filter,
                        bundleOf("year" to semester.yearStr, "term" to semester.termStr),
                    )
                }
                true
            }
            else -> false
        }
    }

    private fun startLoginActivityForResult() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra("from", Constants.ACTIVITY_MAIN)
        startActivityForResult(intent, Constants.REQUEST_LOGIN)
    }
}
