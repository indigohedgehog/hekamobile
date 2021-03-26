package com.triaggle.hekamobile

import android.graphics.Bitmap

class FrameDataHolder {

    var msxBitmap: Bitmap? = null
    var dcBitmap: Bitmap? = null

    constructor (msxBitmap: Bitmap?, dcBitmap: Bitmap?) {
        this.msxBitmap = msxBitmap
        this.dcBitmap = dcBitmap
    }
}