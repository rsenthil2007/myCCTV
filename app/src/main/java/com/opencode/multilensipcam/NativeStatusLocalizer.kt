package com.opencode.multilensipcam

object NativeStatusLocalizer {
    fun localize(message: String, isChineseUi: Boolean): String {
        if (!isChineseUi) return message
        return when {
            message.equals("Idle", ignoreCase = true) -> "\u7a7a\u95f2"
            message.startsWith("Ready on port ", ignoreCase = true) -> {
                val portText = message.substringAfter("Ready on port ")
                "\u5df2\u5c31\u7eea\uff0c\u7aef\u53e3 $portText"
            }
            message.equals("Streaming", ignoreCase = true) -> "\u6b63\u5728\u63a8\u6d41"
            message.equals("Stopped", ignoreCase = true) -> "\u5df2\u505c\u6b62"
            message.equals("Camera scan in progress", ignoreCase = true) -> "\u6444\u50cf\u5934\u626b\u63cf\u4e2d"
            message.equals("Camera scan already in progress.", ignoreCase = true) -> "\u6444\u50cf\u5934\u626b\u63cf\u5df2\u5728\u8fdb\u884c"
            message.equals("Stop streaming before scanning cameras.", ignoreCase = true) -> "\u8bf7\u5148\u505c\u6b62\u76f4\u64ad\uff0c\u518d\u626b\u63cf\u6444\u50cf\u5934"
            message.equals("Camera scan complete.", ignoreCase = true) -> "\u6444\u50cf\u5934\u626b\u63cf\u5b8c\u6210"
            message.equals("Camera scan failed.", ignoreCase = true) -> "\u6444\u50cf\u5934\u626b\u63cf\u5931\u8d25"
            message.equals("Camera scan cache changed", ignoreCase = true) -> "\u6444\u50cf\u5934\u7f13\u5b58\u5df2\u66f4\u65b0"
            message.startsWith("Loaded ", ignoreCase = true) && message.contains(" camera entries") -> {
                val count = message.substringAfter("Loaded ").substringBefore(" camera entries")
                "\u5df2\u52a0\u8f7d $count \u4e2a\u6444\u50cf\u5934\u6761\u76ee"
            }
            message.equals("Camera changed", ignoreCase = true) -> "\u5df2\u5207\u6362\u6444\u50cf\u5934"
            message.equals("Resolution changed", ignoreCase = true) -> "\u5df2\u5207\u6362\u5206\u8fa8\u7387"
            message.equals("Frame rate changed", ignoreCase = true) -> "\u5df2\u5207\u6362\u5e27\u7387"
            message.equals("Manual resolution applied", ignoreCase = true) -> "\u5df2\u5e94\u7528\u81ea\u5b9a\u4e49\u5206\u8fa8\u7387"
            message.equals("Preview surface not ready", ignoreCase = true) -> "\u9884\u89c8\u753b\u9762\u5c1a\u672a\u5c31\u7eea"
            message.equals("Camera permission denied", ignoreCase = true) -> "\u6444\u50cf\u5934\u6743\u9650\u88ab\u62d2\u7edd"
            message.equals("Enter a valid manual resolution", ignoreCase = true) -> "\u8bf7\u8f93\u5165\u6709\u6548\u7684\u81ea\u5b9a\u4e49\u5206\u8fa8\u7387"
            message.equals("No supported sizes available for this camera", ignoreCase = true) -> "\u5f53\u524d\u6444\u50cf\u5934\u6ca1\u6709\u53ef\u7528\u5c3a\u5bf8"
            message.equals("Updated from dashboard", ignoreCase = true) -> "\u5df2\u4ece\u7f51\u9875\u63a7\u5236\u53f0\u66f4\u65b0"
            message.startsWith("Applied ") && message.endsWith(" preset") -> {
                "\u5df2\u5e94\u7528 ${message.removePrefix("Applied ").removeSuffix(" preset")} \u9884\u8bbe"
            }
            message.endsWith(" fps support") && message.contains(" requires ") -> {
                val preset = message.substringBefore(" requires ")
                val fps = message.substringAfter(" requires ").substringBefore(" fps support")
                "$preset \u9700\u8981\u652f\u6301 $fps fps"
            }
            message.equals("Applied warm color skin", ignoreCase = true) -> "\u5df2\u5207\u6362\u4e3a\u6696\u8272\u914d\u8272"
            message.equals("Applied default color skin", ignoreCase = true) -> "\u5df2\u5207\u6362\u4e3a\u9ed8\u8ba4\u914d\u8272"
            message.equals("Applied Green color skin", ignoreCase = true) -> "\u5df2\u5207\u6362\u4e3a\u7eff\u8272\u914d\u8272"
            message.equals("Applied Orange Sunrise color skin", ignoreCase = true) -> "\u5df2\u5207\u6362\u4e3a\u6a59\u8272\u914d\u8272"
            message.equals("Applied Blue Mineral color skin", ignoreCase = true) -> "\u5df2\u5207\u6362\u4e3a\u84dd\u8272\u914d\u8272"
            message.equals("Switched to Chinese UI", ignoreCase = true) -> "\u5df2\u5207\u6362\u4e3a\u4e2d\u6587\u754c\u9762"
            message.equals("Switched to English UI", ignoreCase = true) -> "\u5df2\u5207\u6362\u4e3a\u82f1\u6587\u754c\u9762"
            message.startsWith("Camera report: ", ignoreCase = true) -> {
                "\u6444\u50cf\u5934\u62a5\u544a\uff1a${message.substringAfter("Camera report: ")}"
            }
            else -> message
        }
    }
}
