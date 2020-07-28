package com.ewf.escapewildfire

import android.content.Context
import com.here.android.mpa.common.MapSettings
import java.io.File

class MapSettingThingy {
    private var success: Boolean = false

    private fun checkSettings(applicationContext: Context) {
        success = MapSettings.setIsolatedDiskCacheRootPath("${applicationContext.getExternalFilesDir(null)}${File.separator}.here-maps")
    }

    fun getStatus(applicationContext: Context): Boolean {
        if (!success){
            checkSettings(applicationContext)
        }
        return success
    }

    companion object {
        val instance = MapSettingThingy()
    }
}