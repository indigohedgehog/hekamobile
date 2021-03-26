package com.triaggle.hekamobile

import android.content.Context
import java.io.File

class FileHandler {
    private var filesDir: File? = null

    fun FileHandler(applicationContext: Context) {
        filesDir = applicationContext.getFilesDir()
    }

    fun getImageStoragePathStr(): String? {
        return filesDir?.getAbsolutePath()
    }

    fun getImageStoragePath(): File? {
        return filesDir
    }
}