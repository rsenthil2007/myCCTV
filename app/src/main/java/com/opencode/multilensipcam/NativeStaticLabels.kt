package com.opencode.multilensipcam

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object NativeStaticLabels {
    fun apply(
        root: View,
        bindings: List<StaticTextBinding>,
        isChinese: Boolean,
        stringProvider: (Int) -> String
    ) {
        bindings.forEach { binding ->
            replaceTextInTree(
                root = root,
                english = stringProvider(binding.englishRes),
                chinese = stringProvider(binding.chineseRes),
                target = stringProvider(if (isChinese) binding.chineseRes else binding.englishRes)
            )
        }
    }

    private fun replaceTextInTree(
        root: View,
        english: String,
        chinese: String,
        target: String
    ) {
        if (root is TextView && (root.text.toString() == english || root.text.toString() == chinese)) {
            root.text = target
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                replaceTextInTree(root.getChildAt(index), english, chinese, target)
            }
        }
    }
}
