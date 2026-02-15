package com.shaun.sydia

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shaun.sydia.ui.chat.ChatScreen
import com.shaun.sydia.ui.chat.ChatViewModel
import com.shaun.sydia.ui.chat.ChatViewModelFactory
import com.shaun.sydia.ui.theme.MyApplicationTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class SydiaSession(context: Context) :
    VoiceInteractionSession(context),
    androidx.lifecycle.LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val win = window ?: return

        // ---- 全屏透明窗口 ----
        win.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            w.setDimAmount(0f) // 不要系统默认的半透明遮罩
            val lp = w.attributes
            lp.gravity = Gravity.FILL
            lp.width  = WindowManager.LayoutParams.MATCH_PARENT
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            w.attributes = lp
        }

        val app = context.applicationContext as SydiaApplication
        val chatRepo   = app.chatRepository
        val memoryRepo = app.memoryRepository

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@SydiaSession)
            setViewTreeViewModelStoreOwner(this@SydiaSession)
            setViewTreeSavedStateRegistryOwner(this@SydiaSession)

            setContent {
                MyApplicationTheme {
                    // 全屏 Box：上半部分点击退出，下半部分是 Chat
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 上半：透明可点击区域，点击退出助理
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .align(Alignment.TopCenter)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { hide() }
                        )

                        // 下半：Chat 面板
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            shadowElevation = 16.dp
                        ) {
                            val vm: ChatViewModel = viewModel(
                                factory = ChatViewModelFactory(chatRepo, memoryRepo)
                            )
                            ChatScreen(
                                viewModel = vm,
                                onNavigateToSettings = {
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startAssistantActivity(intent)
                                },
                                onNavigateToMemories = {
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startAssistantActivity(intent)
                                },
                                isAssistantInterface = true,
                                onClose = { hide() }
                            )
                        }
                    }
                }
            }
        }

        setContentView(composeView)
    }

    override fun onHide() {
        super.onHide()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
