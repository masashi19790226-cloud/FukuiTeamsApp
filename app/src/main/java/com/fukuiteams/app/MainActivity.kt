package com.fukuiteams.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fukuiteams.app.navigation.AppNavHost
import com.fukuiteams.app.ui.components.PinchZoomContainer
import com.fukuiteams.app.notifications.EXTRA_OPEN_ROUTE
import com.fukuiteams.app.notifications.ensureAlertsNotificationChannel
import com.fukuiteams.app.notifications.rescheduleGameStartNotifications
import com.fukuiteams.app.notifications.schedulePeriodicAlertsCheck
import com.fukuiteams.app.notifications.scheduleBbsKeywordCheck
import com.fukuiteams.app.data.BbsPrefs
import com.fukuiteams.app.ui.theme.FukuiTeamsAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 結果は問わない */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureAlertsNotificationChannel(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        schedulePeriodicAlertsCheck(applicationContext)
        lifecycleScope.launch {
            rescheduleGameStartNotifications(applicationContext)
        }
        // 掲示板のキーワード通知(設定でオンのときだけ、定期確認を予約しておく)
        lifecycleScope.launch {
            runCatching {
                scheduleBbsKeywordCheck(applicationContext, BbsPrefs.load(applicationContext).notifyEnabled)
            }
        }
        // 通知から開いたときは、その通知の画面を開く(画面の回転などで作り直したときは開き直さない)
        val openRoute = if (savedInstanceState == null) intent?.getStringExtra(EXTRA_OPEN_ROUTE) else null
        setContent {
            FukuiTeamsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 二本指で広げる・つまむと、アプリ全体の表示の大きさが変わる
                    PinchZoomContainer {
                        AppNavHost(initialRoute = openRoute)
                    }
                }
            }
        }
    }
}
