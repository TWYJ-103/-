package com.Johnny.wcx.features.items.payment

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.reflekt.reflekt
import com.Johnny.wcx.features.core.ClickableFeature
import com.Johnny.wcx.features.core.Feature
import com.Johnny.wcx.preferences.WePrefs.Companion.prefOption
import com.Johnny.wcx.ui.content.AlertDialogContent
import com.Johnny.wcx.ui.content.Button
import com.Johnny.wcx.ui.content.TextButton
import com.Johnny.wcx.ui.utils.showComposeDialog
import java.util.concurrent.ConcurrentHashMap

@Feature(
    name = "修改转账金额显示", categories = ["红包与支付"],
    description = "转账相关页面金额显示伪装（仅本地显示，不影响真实金额）"
)
object FakeTransferAmount : ClickableFeature() {

    private const val KEY_AMOUNT = "fake_transfer_amount"
    private const val DEFAULT_AMOUNT = "8888.88"

    /** 生效页面的 Activity 类名关键字（均为未混淆的保留类名） */
    private val ACTIVITY_WHITELIST = listOf("WalletPayUI", "WalletRemitteeUI", "LuckyMoneyDetailUI")

    private val AMOUNT_REGEX = Regex("^\\s*[¥￥]\\s*[\\d,]+(\\.\\d{1,2})?\\s*$")

    private var amount by prefOption(KEY_AMOUNT, DEFAULT_AMOUNT)

    /** 每个 Activity 类是否命中白名单的缓存，避免每次 setText 都重复做字符串匹配 */
    private val whitelistCache = ConcurrentHashMap<Class<*>, Boolean>()

    override fun onEnable() {
        // setText(CharSequence, BufferType) 是所有公开 setText 入口的汇聚点
        TextView::class.reflekt().firstMethod {
            name = "setText"
            parameters(CharSequence::class, TextView.BufferType::class)
        }.hookBefore {
            val view = thisObject as? TextView ?: return@hookBefore
            val text = args[0] as? CharSequence ?: return@hookBefore
            // 廉价预过滤：不含货币符号的文本直接放行
            if (!text.contains('¥') && !text.contains('￥')) return@hookBefore
            // FakeRedPacketAmount 已接管的控件跳过，两个功能不互相覆盖
            if (view.getTag(FakeRedPacketAmount.FAKE_AMOUNT_TAG_KEY) == true) return@hookBefore

            val activity = activityOf(view.context) ?: return@hookBefore
            if (!isWhitelisted(activity.javaClass)) return@hookBefore
            if (!AMOUNT_REGEX.matches(text)) return@hookBefore

            args[0] = "¥$amount"
        }
    }

    private fun isWhitelisted(clazz: Class<*>): Boolean =
        whitelistCache.getOrPut(clazz) { ACTIVITY_WHITELIST.any(clazz.name::contains) }

    private fun activityOf(ctx: Context): Activity? {
        var c = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    override fun onClick(context: ComponentActivity) {
        showComposeDialog(context) {
            var amountInput by remember { mutableStateOf(amount) }

            AlertDialogContent(
                title = { Text("修改转账金额显示") },
                text = {
                    TextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("转账金额 (留空恢复默认 $DEFAULT_AMOUNT)") })
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
