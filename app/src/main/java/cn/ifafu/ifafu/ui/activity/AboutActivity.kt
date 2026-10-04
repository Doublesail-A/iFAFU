package cn.ifafu.ifafu.ui.activity

import android.os.Bundle
import cn.ifafu.ifafu.BuildConfig
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.databinding.AboutActivityBinding
import cn.ifafu.ifafu.ui.common.BaseActivity
import cn.ifafu.ifafu.ui.web.WebActivity
import cn.ifafu.ifafu.update.GitHubReleases
import cn.ifafu.ifafu.update.GitHubUpdateChecker

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = AboutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setLightUiBar()
        binding.tbAbout.setNavigationOnClickListener { finish() }
        binding.tvVersionName.text = BuildConfig.VERSION_NAME
        binding.checkUpdates.setOnClickListener { GitHubUpdateChecker.check(this) }
        binding.projectLink.setOnClickListener { GitHubUpdateChecker.open(this, GitHubReleases.PROJECT) }
        binding.releaseLink.setOnClickListener { GitHubUpdateChecker.open(this, GitHubReleases.RELEASES) }
        binding.tvPrivacyPolicy.setOnClickListener {
            startActivity(WebActivity.intentFor(this, Constants.PRIVACY_POLICY_URL, "隐私政策"))
        }
        binding.credits.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("开源许可")
                .setMessage("iFAFU 的开源重构项目。\n\n感谢原作者 woolsen 与所有贡献者，保留原作者署名，各开源组件遵循各自的许可证。\n\n界面使用 Google Material Components，配色使用 Material Color Utilities，启动屏使用 AndroidX SplashScreen。")
                .setPositiveButton("查看项目") { _, _ ->
                    GitHubUpdateChecker.open(this, GitHubReleases.PROJECT)
                }
                .setNegativeButton("关闭", null).show()
        }
    }
}
