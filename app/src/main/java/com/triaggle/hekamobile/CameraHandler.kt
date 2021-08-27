package com.triaggle.hekamobile

import android.graphics.Bitmap
import android.util.Log
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid
import com.flir.thermalsdk.image.ThermalImage
import com.flir.thermalsdk.image.fusion.FusionMode
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.ConnectParameters
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.live.discovery.DiscoveryFactory
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener
import com.flir.thermalsdk.live.Camera
import com.flir.thermalsdk.live.Identity


import org.jetbrains.annotations.Nullable

import java.io.IOException
import java.util.Collections
import java.util.LinkedList



class CameraHandler {
    private val TAG = "CameraHandler"

    private var streamDataListener: StreamDataListener? = null

    interface StreamDataListener {
        fun images(dataHolder: FrameDataHolder?)
        fun images(msxBitmap: Bitmap?, dcBitmap: Bitmap?)
    }

    var foundCameraIdentities: LinkedList<Identity> = LinkedList()
    private var camera: Camera? = null


    interface DiscoveryStatus {
        fun started()
        fun stopped()
    }

    fun startDiscovery(cameraDiscoveryListener: DiscoveryEventListener?, discoveryStatus: DiscoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener!!, CommunicationInterface.EMULATOR, CommunicationInterface.USB)
        discoveryStatus.started()
    }

    fun stopDiscovery(discoveryStatus: DiscoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB)
        discoveryStatus.stopped()
    }

    @Throws(IOException::class)
    fun connect(identity: Identity, connectionStatusListener: ConnectionStatusListener) {
        camera = Camera()
        camera!!.connect(identity, connectionStatusListener, ConnectParameters())
    }

    fun disconnect() {
        if (camera == null) {
            return
        }
        if (camera?.isGrabbing == true) {
            camera?.unsubscribeAllStreams()
        }
        camera?.disconnect()
    }

    fun startStream(listener: StreamDataListener?) {
        streamDataListener = listener
        camera?.subscribeStream(thermalImageStreamListener)
    }

    fun stopStream(listener: ThermalImageStreamListener?) {
        camera?.unsubscribeStream(listener)
    }

    fun add(identity: Identity) {
        foundCameraIdentities.add(identity)
    }

    @Nullable
    operator fun get(i: Int): Identity? {
        return foundCameraIdentities.get(i)
    }

    @Nullable
    fun getCameraList(): MutableList<Identity> {
        return Collections.unmodifiableList(foundCameraIdentities)
    }

    /**
     * Clear all known network cameras
     */
    fun clear() {
        foundCameraIdentities.clear()
    }

    @Nullable
    fun getCppEmulator(): Identity? {
        for (foundCameraIdentity in foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity
            }
        }
        return null
    }

    @Nullable
    fun getFlirOneEmulator(): Identity? {
        for (foundCameraIdentity in foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity
            }
        }
        return null
    }

    @Nullable
    fun getFlirOne(): Identity? {
        for (foundCameraIdentity in foundCameraIdentities) {
            val isFlirOneEmulator: Boolean = foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")
            val isCppEmulator: Boolean = foundCameraIdentity.deviceId.contains("C++ Emulator")
            if (!isFlirOneEmulator && !isCppEmulator) {
                return foundCameraIdentity
            }
        }
        return null
    }

    private fun withImage(listener: ThermalImageStreamListener, functionToRun: Camera.Consumer<ThermalImage>) {
        camera?.withImage(listener, functionToRun)
    }

    private val thermalImageStreamListener: ThermalImageStreamListener = object : ThermalImageStreamListener {
        override fun onImageReceived() {
            //Will be called on a non-ui thread
            Log.d(TAG, "onImageReceived(), we got another ThermalImage")
            withImage(this, handleIncomingImage)
        }
    }


    /**
     * Function to process a Thermal Image and update UI
     */
    private val handleIncomingImage: Camera.Consumer<ThermalImage> = object : Camera.Consumer<ThermalImage> {
        override fun accept(thermalImage: ThermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + thermalImage.description + "]")
            //Will be called on a non-ui thread,
            // extract information on the background thread and send the specific information to the UI thread

            //Get a bitmap with only IR data
            var msxBitmap: Bitmap?
            run {
                thermalImage.fusion!!.setFusionMode(FusionMode.THERMAL_ONLY)
                msxBitmap = BitmapAndroid.createBitmap(thermalImage.image).bitMap
            }

            //Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
            val dcBitmap = BitmapAndroid.createBitmap(thermalImage.fusion!!.photo!!).bitMap
            Log.d(TAG, "adding images to cache")
            streamDataListener!!.images(msxBitmap, dcBitmap)
        }
    }

}