package com.lilzee.zee

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lilzee.zee.ui.ZeeTheme

/** 仅在用户点磁贴、窗口获得焦点后读取一次剪贴板。 */
class ClipboardActivity : ComponentActivity() {

    private var handled = false
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeeTheme {
                errorMessage?.let { message ->
                    AlertDialog(
                        onDismissRequest = ::finish,
                        title = { Text("先复制英文") },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = ::finish) { Text("知道了") }
                        },
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val text = try {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString()?.trim().orEmpty()
            } else ""
        } catch (_: SecurityException) {
            errorMessage = "暂时无法读取剪贴板。请复制英文后，再打开控制中心点「Zee 查词」。"
            return
        }
        if (text.isEmpty() || (!text.any { it.isWhitespace() } && normalizeToken(text).isEmpty())) {
            errorMessage = "剪贴板里没有可处理的文字。请在 X 或其他应用里复制英文，再点「Zee 查词」。"
            return
        }

        startActivity(Intent(this, ProcessTextActivity::class.java).apply {
            action = Intent.ACTION_PROCESS_TEXT
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        })
        finish()
    }
}
