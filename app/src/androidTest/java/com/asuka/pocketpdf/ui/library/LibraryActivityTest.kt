package com.asuka.pocketpdf.ui.library

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asuka.pocketpdf.R
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
    fun `activity launches and shows FAB`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // Activity 已创建
                assert(!activity.isFinishing)
            }
            onView(withId(R.id.fab_library_import)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun `FAB click triggers SAF picker (no crash)`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // 验证 Activity 处于可交互状态
                assert(!activity.isFinishing)
            }
            // 点击 FAB — ActivityResultRegistry 会静默处理
            onView(withId(R.id.fab_library_import)).perform(click())
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
