package com.Johnny.wcx.features.items.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.reflekt.reflekt
import com.Johnny.wcx.constants.PackageNames
import com.Johnny.wcx.features.core.ClickableFeature
import com.Johnny.wcx.features.core.Feature
import com.Johnny.wcx.preferences.WePrefs.Companion.prefOption
import com.Johnny.wcx.ui.content.AlertDialogContent
import com.Johnny.wcx.ui.content.Button
import com.Johnny.wcx.ui.content.TextButton
import com.Johnny.wcx.ui.utils.showComposeDialog
import com.Johnny.wcx.utils.WeLogger

@Feature(
    name = "修改红包显示金额", categories = ["红包与支付"],
    description = "红包详情页显示自定义金额（仅本地显示，不影响真实金额）"
)
object FakeRedPacketAmount : ClickableFeature() {

    private const val TAG = "FakeRedPacketAmount"

    private const val KEY_AMOUNT = "fake_rp_amount"
    private const val DEFAULT_AMOUNT = "888.88"

    /** 金额控件资源名，新版在前旧版在后；运行时通过 getIdentifier 反查，不硬编码 int id */
    private val AMOUNT_VIEW_ID_NAMES = listOf("ah9", "eyq")

    private val AMOUNT_REGEX = Regex("^[¥￥]\\s*[\\d,]+(\\.\\d{1,2})?$")

    /** 标记已被本功能替换过的金额控件，供 FakeTransferAmount 跳过，避免两个功能互相覆盖 */
    internal const val FAKE_AMOUNT_TAG_KEY = 0x55020002

    private var amount by prefOption(KEY_AMOUNT, DEFAULT_AMOUNT)

    override fun onEnable() {
        Activity::class.reflekt().firstMethod {
            name = "onResume"
            parameterCount = 0
        }.hookAfter {
            val activity = thisObject as? Activity ?: return@hookAfter
            if (activity.javaClass.name.substringAfterLast('.') != "LuckyMoneyDetailUI") return@hookAfter

            // 金额异步从服务器加载，延迟一拍再替换
            activity.window?.decorView?.post {
                runCatching { applyFakeAmount(activity) }
                    .onFailure { WeLogger.e(TAG, "failed to apply fake amount", it) }
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun applyFakeAmount(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        val fakeText = "¥$amount"

        for (idName in AMOUNT_VIEW_ID_NAMES) {
            val id = activity.resources.getIdentifier(idName, "id", PackageNames.WECHAT)
            if (id == 0) continue
            val view = decor.findViewById<View>(id) as? TextView ?: continue
            view.setTag(FAKE_AMOUNT_TAG_KEY, true)
            view.text = fakeText
            WeLogger.d(TAG, "replaced amount via id $idName")
            return
        }

        // 兜底：遍历 View 树，取文本匹配金额格式且字号最大的 TextView
        val target = findAmountTextView(decor)
        if (target != null) {
            target.setTag(FAKE_AMOUNT_TAG_KEY, true)
            target.text = fakeText
            WeLogger.d(TAG, "replaced amount via view-tree fallback")
        } else {
            WeLogger.w(TAG, "amount TextView not found on LuckyMoneyDetailUI")
        }
    }

    private fun findAmountTextView(root: View): TextView? {
        var best: TextView? = null
        val stack = ArrayDeque<View>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val view = stack.removeLast()
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) stack.add(view.getChildAt(i))
            }
            if (view is TextView && AMOUNT_REGEX.matches(view.text)) {
                if (best == null || view.textSize > best.textSize) best = view
            }
        }
        return best
    }

    override fun onClick(context: ComponentActivity) {
        showComposeDialog(context) {
            var amountInput by remember { mutableStateOf(amount) }

            AlertDialogContent(
                title = { Text("修改红包显示金额") },
                text = {
                    TextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("红包金额 (留空恢复默认 $DEFAULT_AMOUNT)") })
                },
                confirmButton = {
                    Button(onClick = {
                        amount = amountInput.ifBlank { DEFAULT_AMOUNT }
                        onDismiss()
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } }
            )
        }
    }
}
