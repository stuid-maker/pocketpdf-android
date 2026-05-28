package com.asuka.pocketpdf.ui.chat

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ChatActivity UI 测试：验证 Compose 聊天界面可正常启动。
 *
 * 由于 ChatActivity 使用 Jetpack Compose 构建 UI，标准的 Espresso ViewMatchers
 * 无法直接定位 Compose 元素。本测试验证 Activity 生命周期正常、不崩溃。
 *
 * 如需完整的 Compose UI 测试，请添加依赖：
 *   androidTestImplementation(platform(libs.compose.bom))
 *   androidTestImplementation(libs.compose.ui.test)
 *   debugImplementation(libs.compose.ui.test.manifest)
 */
@RunWith(AndroidJUnit4::class)
class ChatActivityTest {

    @Test
    fun `activity launches with valid document id`() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            ChatActivity::class.java,
        ).apply {
            putExtra("com.asuka.pocketpdf.extra.DOCUMENT_ID", 1L)
        }
        ActivityScenario.launch<ChatActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("Activity should not be finishing", activity.isFinishing)
                assertFalse("Activity should not be destroyed", activity.isDestroyed)
            }
        }
    }

    @Test
    fun `activity launches with default document id when extra missing`() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            ChatActivity::class.java,
        )
        ActivityScenario.launch<ChatActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("Activity should handle missing extra", activity.isFinishing)
            }
        }
    }

    @Test
    fun `activity launches with document id zero`() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            ChatActivity::class.java,
        ).apply {
            putExtra("com.asuka.pocketpdf.extra.DOCUMENT_ID", 0L)
        }
        ActivityScenario.launch<ChatActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("Activity should handle id=0", activity.isFinishing)
            }
        }
    }
}
