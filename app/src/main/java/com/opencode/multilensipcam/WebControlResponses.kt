package com.opencode.multilensipcam

import java.io.OutputStream

class WebControlResponses(
    private val stateProvider: () -> WebControlState,
    private val controlHandler: (WebControlCommand) -> Unit
) {
    fun writeState(output: OutputStream) {
        WebHttpResponses.writeText(output, WebApiJson.state(stateProvider()).toString(), JSON_CONTENT_TYPE)
    }

    fun handleControl(output: OutputStream, query: Map<String, String>) {
        controlHandler(WebApiQueryParser.controlCommand(query))
        WebHttpResponses.writeText(output, WebApiJson.ok().toString(), JSON_CONTENT_TYPE)
    }

    private companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}
