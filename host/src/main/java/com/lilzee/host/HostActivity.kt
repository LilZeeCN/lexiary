package com.lilzee.host

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/** 独立宿主 App：模拟 X 的原生只读 TextView，验证 Zee 出现在系统选择菜单 */
class HostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        val handlers = packageManager.queryIntentActivities(intent, 0)
            .joinToString { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
        Log.d("HostQuery", "PROCESS_TEXT handlers visible to host: [$handlers]")
        val tv = TextView(this).apply {
            id = android.view.View.generateViewId()
            text = "The quick brown fox jumps over the lazy dog. " +
                "After months of setbacks, she stayed remarkably resilient " +
                "and refused to give up on the project."
            textSize = 18f
            setTextIsSelectable(true)
            setPadding(48, 160, 48, 48)
        }
        setContentView(tv)
    }
}
