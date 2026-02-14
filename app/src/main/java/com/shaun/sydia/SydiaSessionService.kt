package com.shaun.sydia


import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class MyAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MyAssistantSession(this)
    }
}

class MyAssistantSession(context: android.content.Context) : VoiceInteractionSession(context) {
    // 当调用助理时，这个方法会被触发
    override fun onHandleAssist(data: AssistState) {
        super.onHandleAssist(data)
        val structure = data.assistStructure
        val screenshot = data.assistScreenshot

        println("Sydia 已经感知到屏幕内容！")
    }
}