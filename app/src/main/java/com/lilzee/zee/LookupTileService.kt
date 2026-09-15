package com.lilzee.zee

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(24)
class LookupTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.lookup_tile_label)
            state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = "翻译剪贴板"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openLookup) else openLookup()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated") // API 24–33 只能使用 Intent 重载。
    private fun openLookup() {
        // 独立的查词任务：重复点磁贴会替换旧弹窗，关闭后回到原来的应用。
        val intent = Intent(this, ClipboardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
