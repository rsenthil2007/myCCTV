package com.opencode.multilensipcam

data class WebCameraSelection(
    val option: CameraLensOption,
    val index: Int
)

object WebCameraSelections {
    fun resolve(
        repository: CameraLensRepository,
        options: List<CameraLensOption>,
        command: WebControlCommand,
        currentOption: CameraLensOption?,
        allowedDebugCameraIds: Set<String>
    ): WebCameraSelection? {
        val newOption = repository.findOptionByControlKey(options, command.cameraKey, allowedDebugCameraIds)
        val currentKey = currentOption?.let(repository::controlKeyFor)
        if (newOption == null || repository.controlKeyFor(newOption) == currentKey) {
            return null
        }
        val cameraIndex = options.indexOfFirst {
            repository.controlKeyFor(it) == repository.controlKeyFor(newOption)
        }
        return WebCameraSelection(option = newOption, index = cameraIndex)
    }
}
