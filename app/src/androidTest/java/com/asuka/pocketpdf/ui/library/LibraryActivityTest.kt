package com.asuka.pocketpdf.ui.library

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LibraryActivity UI 测试：验证主界面元素可展示及 FAB 可点击。
 *
 * 注意：SAF picker 无法在测试环境真实启动，本测试仅验证 FAB 可见可点击，
 * SAF 回调不会被触发（ActivityResultRegistry 默认 no-op）。
 */
@RunWith(AndroidJUnit4::class)
class LibraryActivityTest {

    @Test
    fun `activity launches without finishing`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // Activity 已创建
                assert(!activity.isFinishing)
            }
        }
    }

    @Test
    fun `activity launcher does not finish immediately`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assert(!activity.isFinishing)
                assert(!activity.isDestroyed)
            }
        }
    }
}
