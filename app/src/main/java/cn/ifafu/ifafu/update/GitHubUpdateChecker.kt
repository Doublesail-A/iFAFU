package cn.ifafu.ifafu.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import cn.ifafu.ifafu.BuildConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

/** Manual checks only; no account information or GitHub credential is sent. */
object GitHubUpdateChecker {
    private val running = WeakHashMap<FragmentActivity, Job>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    fun open(activity: FragmentActivity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show()
        }
    }

    fun check(activity: FragmentActivity) {
        if (running[activity]?.isActive == true) return
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
            addView(CircularProgressIndicator(activity).apply {
                isIndeterminate = true
                indicatorSize = dp(28)
                trackThickness = dp(3)
            }, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(TextView(activity).apply {
                text = "正在检查项目的 GitHub Release"
                setPadding(dp(16), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        var task: Job? = null
        val loading = MaterialAlertDialogBuilder(activity)
            .setTitle("检查更新").setView(content)
            .setNegativeButton("取消") { _, _ -> task?.cancel() }
            .setOnCancelListener { task?.cancel() }.show()
        task = activity.lifecycleScope.launch {
            try {
                val latest = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(GitHubReleases.API)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "iFAFU/" + BuildConfig.VERSION_NAME)
                        .header("X-GitHub-Api-Version", "2022-11-28").build()
                    client.newCall(request).execute().use {
                        check(it.isSuccessful) { "GitHub HTTP " + it.code }
                        GitHubReleases.parse(it.body?.string().orEmpty(), BuildConfig.DEBUG)
                    }
                }
                loading.dismiss()
                val current = AppVersion.parse(BuildConfig.VERSION_NAME)!!
                if (latest == null || latest.version <= current) {
                    Toast.makeText(activity, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("发现新版本")
                        .setMessage("当前版本 " + current + "\n新版本 " + latest.version)
                        .setPositiveButton(if (latest.downloadUrl == null) "打开发布页" else "下载更新") { _, _ ->
                            open(activity, latest.downloadUrl ?: latest.pageUrl)
                        }
                        .setNeutralButton("更新说明") { _, _ -> open(activity, latest.pageUrl) }
                        .setNegativeButton("稍后", null).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!activity.isFinishing) {
                    Toast.makeText(activity, "检查更新失败，请检查网络或稍后重试", Toast.LENGTH_LONG).show()
                }
            } finally {
                loading.dismiss()
                running.remove(activity)
            }
        }
        running[activity] = task!!
    }
}
