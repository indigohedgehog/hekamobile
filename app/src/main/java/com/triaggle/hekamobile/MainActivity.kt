package com.triaggle.hekamobile

import android.graphics.Bitmap
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.flir.thermalsdk.ErrorCode
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler.UsbPermissionListener
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.log.ThermalLog.LogLevel
import com.intel.realsense.librealsense.*
import com.triaggle.hekamobile.CameraHandler.DiscoveryStatus
import com.triaggle.hekamobile.CameraHandler.StreamDataListener
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue


class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_REQUEST_CAMERA = 0

    private var mPermissionsGranted = false

    private var mAppContext: Context? = null
    private var mBackGroundText: TextView? = null
    private var mGLSurfaceView: GLRsSurfaceView? = null
    private var mIsStreaming = false

    private var mPipeline: Pipeline? = null
    private var mColorizer: Colorizer? = null
    private var mRsContext: RsContext? = null
    private val TAG = "librs capture example"

    private val mHandler: Handler = Handler()

    //private val TAG = "MainActivity"

    //Handles Android permission for eg Network
    private var permissionHandler: PermissionHandler? = null

    //Handles network camera operations
    private var cameraHandler: CameraHandler? = null

    private var connectedIdentity: Identity? = null
    private var connectionStatus: TextView? = null
    private var discoveryStatus: TextView? = null

    private var msxImage: ImageView? = null
    private var photoImage: ImageView? = null

    private val framesBuffer: LinkedBlockingQueue<FrameDataHolder?> = LinkedBlockingQueue<FrameDataHolder?>(21)
    private val usbPermissionHandler = UsbPermissionHandler()

    interface ShowMessage {
        fun show(message: String?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mAppContext = applicationContext
        mBackGroundText = findViewById(R.id.connectCameraText)
        mGLSurfaceView = findViewById(R.id.glSurfaceView)
        mGLSurfaceView?.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

        // Android 9 also requires camera permissions

        // Android 9 also requires camera permissions
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSIONS_REQUEST_CAMERA)
            return
        }

        mPermissionsGranted = true

        val enableLoggingInDebug = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NONE

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework
        ThermalSdkAndroid.init(applicationContext, enableLoggingInDebug)

        permissionHandler = PermissionHandler(showMessage, this@MainActivity)

        cameraHandler = CameraHandler()

        setupViews()

        showSDKversion(ThermalSdkAndroid.getVersion())
    }

    override fun onDestroy() {
        super.onDestroy()
        mGLSurfaceView?.close()
    }


    override fun onResume() {
        super.onResume()
        if(mPermissionsGranted)
            init()
        else
            Log.e(TAG, "missing permissions")
    }

    override fun onPause() {
        super.onPause()
        if(mRsContext != null)
            mRsContext?.close()
        stop()
        mColorizer?.close()
        mPipeline?.close()
    }


    private fun showConnectLabel(state: Boolean) {
        runOnUiThread { mBackGroundText?.visibility = if (state) View.VISIBLE else View.GONE }
    }


    private val mListener: DeviceListener = object : DeviceListener {
        override fun onDeviceAttach() {
            showConnectLabel(false)
        }

        override fun onDeviceDetach() {
            showConnectLabel(true)
            stop()
        }
    }

    private fun init() {
        //RsContext.init must be called once in the application lifetime before any interaction with physical RealSense devices.
        //For multi activities applications use the application context instead of the activity context
        RsContext.init(mAppContext)

        //Register to notifications regarding RealSense devices attach/detach events via the DeviceListener.
        mRsContext = RsContext()
        mRsContext!!.setDevicesChangedCallback(mListener)
        mPipeline = Pipeline()
        mColorizer = Colorizer()
        mRsContext!!.queryDevices().use { dl ->
            if (dl.deviceCount > 0) {
                showConnectLabel(false)
                start()
            }
        }
    }

    var mStreaming: Runnable = object : Runnable {
        override fun run() {
            try {
                mPipeline!!.waitForFrames().use { frames -> frames.applyFilter(mColorizer).use { processed -> mGLSurfaceView?.upload(processed) } }
                mHandler.post(this)
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "streaming, error: " + e.message)
            }
        }
    }

    @Throws(Exception::class)
    private fun configAndStart() {
        Config().use { config ->
            config.enableStream(StreamType.DEPTH, 640, 480)
            config.enableStream(StreamType.COLOR, 640, 480)
            mPipeline!!.start(config).use { pp -> }
        }
    }

    @Synchronized
    private fun start() {
        if (mIsStreaming) return
        try {
            Log.d(TAG, "try start streaming")
            mGLSurfaceView!!.clear()
            configAndStart()
            mIsStreaming = true
            mHandler.post(mStreaming)
            Log.d(TAG, "streaming started successfully")
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "failed to start streaming")
        }
    }
    @Synchronized
    private fun stop() {
        if (!mIsStreaming) return
        try {
            Log.d(TAG, "try stop streaming")
            mIsStreaming = false
            mHandler.removeCallbacks(mStreaming)
            mPipeline!!.stop()
            mGLSurfaceView!!.clear()
            Log.d(TAG, "streaming stopped successfully")
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "failed to stop streaming")
        }
    }

//FLIR

    fun startDiscovery(view: View?) {
        startDiscovery()
    }

    fun stopDiscovery(view: View?) {
        stopDiscovery()
    }


    fun connectFlirOne(view: View?) {
        connect(cameraHandler!!.getFlirOne())
    }

    fun connectSimulatorOne(view: View?) {
        connect(cameraHandler!!.getCppEmulator())
    }

    fun connectSimulatorTwo(view: View?) {
        connect(cameraHandler!!.getFlirOneEmulator())
    }

    fun disconnect(view: View?) {
        disconnect()
    }


    /**
     * Connect to a Camera
     */
    private fun connect(identity: Identity?) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        cameraHandler!!.stopDiscovery(discoveryStatusListener)
        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time")
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time")
            return
        }
        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available")
            showMessage.show("connect(), can't connect, no camera available")
            return
        }
        connectedIdentity = identity
        updateConnectionText(identity, "CONNECTING")
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener)
        } else {
            doConnect(identity)
        }
    }

    private val permissionListener: UsbPermissionListener = object : UsbPermissionListener {
        override fun permissionGranted(identity: Identity) {
            doConnect(identity)
        }

        override fun permissionDenied(identity: Identity) {
            showMessage.show("Permission was denied for identity ")
        }

        override fun error(errorType: UsbPermissionListener.ErrorType, identity: Identity) {
            showMessage.show("Error when asking for permission for FLIR ONE, error:$errorType identity:$identity")
        }
    }

    private fun doConnect(identity: Identity) {
        Thread {
            try {
                cameraHandler!!.connect(identity, connectionStatusListener)
                runOnUiThread {
                    updateConnectionText(identity, "CONNECTED")
                    cameraHandler!!.startStream(streamDataListener)
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Log.d(TAG, "Could not connect: $e")
                    updateConnectionText(identity, "DISCONNECTED")
                }
            }
        }.start()
    }

    /**
     * Disconnect to a camera
     */
    private fun disconnect() {
        updateConnectionText(connectedIdentity, "DISCONNECTING")
        connectedIdentity = null
        Log.d(TAG, "disconnect() called with: connectedIdentity = [$connectedIdentity]")
        Thread {
            cameraHandler!!.disconnect()
            runOnUiThread { updateConnectionText(null, "DISCONNECTED") }
        }.start()
    }

    /**
     * Update the UI text for connection status
     */
    private fun updateConnectionText(identity: Identity?, status: String) {
        val deviceId = identity?.deviceId ?: ""
        connectionStatus!!.text = getString(R.string.connection_status_text, "$deviceId $status")
    }

    /**
     * Start camera discovery
     */
    private fun startDiscovery() {
        cameraHandler!!.startDiscovery(cameraDiscoveryListener, discoveryStatusListener)
    }

    /**
     * Stop camera discovery
     */
    private fun stopDiscovery() {
        cameraHandler!!.stopDiscovery(discoveryStatusListener)
    }


    /**
     * Callback for discovery status, using it to update UI
     */
    private val discoveryStatusListener: DiscoveryStatus = object : DiscoveryStatus {
        override fun started() {
            discoveryStatus!!.text = getString(R.string.connection_status_text, "discovering")
        }

        override fun stopped() {
            discoveryStatus!!.text = getString(R.string.connection_status_text, "not discovering")
        }
    }

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     *
     *
     * Note that callbacks are received on a non-ui thread so have to eg use [.runOnUiThread] to interact view UI components
     */
    private val connectionStatusListener = ConnectionStatusListener { errorCode ->
        Log.d(TAG, "onDisconnected errorCode:$errorCode")
        runOnUiThread { updateConnectionText(connectedIdentity, "DISCONNECTED") }
    }

    private val streamDataListener: StreamDataListener = object : StreamDataListener {
        override fun images(dataHolder: FrameDataHolder?) {
            runOnUiThread {
                msxImage!!.setImageBitmap(dataHolder!!.msxBitmap)
                photoImage!!.setImageBitmap(dataHolder.dcBitmap)
            }
        }

        override fun images(msxBitmap: Bitmap?, dcBitmap: Bitmap?) {
            try {
                framesBuffer.put(FrameDataHolder(msxBitmap, dcBitmap))
            } catch (e: InterruptedException) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:$e")
            }
            runOnUiThread {
                Log.d(TAG, "framebuffer size:" + framesBuffer.size)
                val poll = framesBuffer.poll()!!
                msxImage!!.setImageBitmap(poll.msxBitmap)
                photoImage!!.setImageBitmap(poll.dcBitmap)
            }
        }
    }

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     *
     *
     * Note that callbacks are received on a non-ui thread so have to eg use [.runOnUiThread] to interact view UI components
     */
    private val cameraDiscoveryListener: DiscoveryEventListener = object : DiscoveryEventListener {
        override fun onCameraFound(identity: Identity) {
            Log.d(TAG, "onCameraFound identity:$identity")
            runOnUiThread { cameraHandler!!.add(identity) }
        }

        override fun onDiscoveryError(communicationInterface: CommunicationInterface, errorCode: ErrorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:$communicationInterface errorCode:$errorCode")
            runOnUiThread {
                stopDiscovery()
                showMessage.show("onDiscoveryError communicationInterface:$communicationInterface errorCode:$errorCode")
            }
        }
    }

    private val showMessage: ShowMessage = object : ShowMessage {
        override fun show(message: String?) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSDKversion(version: String) {
        val sdkVersionTextView = findViewById<TextView>(R.id.sdk_version)
        val sdkVersionText = getString(R.string.sdk_version_text, version)
        sdkVersionTextView.text = sdkVersionText
    }

    private fun setupViews() {
        connectionStatus = findViewById(R.id.connection_status_text)
        discoveryStatus = findViewById(R.id.discovery_status)
        msxImage = findViewById(R.id.msx_image)
        photoImage = findViewById(R.id.photo_image)
    }

}


