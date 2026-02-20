package com.shaun.sydia

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shaun.sydia.ui.chat.ChatScreen
import com.shaun.sydia.ui.chat.ChatViewModel
import com.shaun.sydia.ui.chat.ChatViewModelFactory
import com.shaun.sydia.ui.theme.MyApplicationTheme

/**
 * 助手 Chat Activity
 * 启动时自动通过助手权限获取屏幕信息，然后弹出 Chat UI
 */
class AssistantChatActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AssistantChatActivity"
        private const val EXTRA_SCREENSHOT = "screenshot"

        /**
         * 启动 AssistantChatActivity
         * 需要从 VoiceInteractionSession 中调用
         */
        fun startFromSession(context: Context, screenshot: Bitmap? = null) {
            val intent = Intent(context, AssistantChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                screenshot?.let {
                    // 注意：Bitmap 不能直接通过 Intent 传递，这里仅作示例
                    // 实际应用中应该使用 File 或其他方式传递
                }
            }
            context.startActivity(intent)
        }
    }

    private var capturedScreenshot: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否是从 VoiceInteractionSession 启动的
        if (isVoiceInteraction()) {
            // 通过助手权限获取屏幕信息
            captureScreenUsingAssistant()
        } else {
            // 普通启动，直接显示 Chat UI
            showChatUI()
        }
    }

    /**
     * 使用助手权限捕获屏幕
     */
    private fun captureScreenUsingAssistant() {
        try {
            // 使用 VoiceInteractionSession 的屏幕捕获 API
            // 注意：这个方法需要在 VoiceInteractionSession 上下文中调用
            // 这里我们使用 MediaProjection API 作为备选方案
            requestScreenCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screen using assistant", e)
            // 即使捕获失败，也继续显示 Chat UI
            showChatUI()
        }
    }

    /**
     * 请求屏幕捕获权限
     */
    private fun requestScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 用户同意屏幕捕获
            Log.d(TAG, "Screen capture permission granted")
            // 这里可以启动 MediaProjection 来获取屏幕内容
            // 为简化，我们直接显示 Chat UI
            showChatUI()
        } else {
            // 用户拒绝屏幕捕获
            Log.d(TAG, "Screen capture permission denied")
            // 仍然显示 Chat UI，只是没有屏幕信息
            showChatUI()
        }
    }

    /**
     * 显示 Chat UI
     */
    private fun showChatUI() {
        val app = application as SydiaApplication

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val chatViewModel: ChatViewModel = viewModel(
                        factory = ChatViewModelFactory(
                            app.chatRepository,
                            app.memoryRepository,
                            app.settingsRepository,
                            app.aiService
                        )
                    )

                    ChatScreen(
                        viewModel = chatViewModel,
                        onNavigateToSettings = {
                            // 在 Assistant 模式下，跳转到 MainActivity 的设置页面
                            val intent = Intent(this, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        },
                        onNavigateToMemories = {
                            // 在 Assistant 模式下，跳转到 MainActivity 的记忆页面
                            val intent = Intent(this, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        },
                        isAssistantInterface = true,
                        onClose = {
                            // 关闭 Activity
                            finish()
                        }
                    )
                }
            }
        }
    }
}
