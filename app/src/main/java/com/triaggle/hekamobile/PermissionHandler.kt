package com.triaggle.hekamobile

import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import com.flir.thermalsdk.log.ThermalLog
import android.Manifest;
import android.os.Process;


class PermissionHandler {

    private val TAG = "PermissionHandler"
    private var mainActivity: MainActivity? = null

    var showMessage: MainActivity.ShowMessage? = null

    @VisibleForTesting
    var PERMISSIONS_FOR_NW_DISCOVERY = arrayOf<String>(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @VisibleForTesting
    var PERMISSIONS_FOR_STORAGE_DISCOVERY = arrayOf<String>(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    )

    @VisibleForTesting
    var PERMISSIONS_FOR_BLUETOOTH = arrayOf<String>(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION)

    constructor (showMessage: MainActivity.ShowMessage, mainActivity: MainActivity) {
        this.showMessage = showMessage
        this.mainActivity = mainActivity
    }

    /**
     * Check if we have Network permission, if not it shows a dialog requesting the permission and "onRequestPermissionsResult(..)" is called with the result
     *
     * @return TRUE if all permission was given else FALSE
     */
    fun checkForNetworkPermission(): Boolean {
        return checkForPermission(PERMISSIONS_FOR_NW_DISCOVERY)
    }

    /**
     * Check if we have Storage permission, if not it shows a dialog requesting the permission and "onRequestPermissionsResult(..)" is called with the result
     *
     * @return TRUE if all permission was given else FALSE
     */
    fun checkForStoragePermission(): Boolean {
        return checkForPermission(PERMISSIONS_FOR_STORAGE_DISCOVERY)
    }

    /**
     * Check if we have Bluetooth permission, if not it shows a dialog requesting the permission and "onRequestPermissionsResult(..)" is called with the result
     *
     * @return TRUE if all permission was given else FALSE
     */
    fun checkForBluetoothPermission(): Boolean {
        return checkForPermission(PERMISSIONS_FOR_BLUETOOTH)
    }

    /**
     * Handles the information from a request Permission dialog, has to be called by the associated PermissionHandler Activity eg:
     *
     * Activity:onRequestPermissionsResult(...) { permissionHandler.onRequestPermissionsResult(...); }
     */
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>?, grantResults: IntArray) {
        if (grantResults.size <= 0) {
            showMessage?.show("Permission request was canceled")
            return
        }
        val permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        val requestPermissionName = getRequestPermissionName(requestCode)

        // If request is cancelled, the result arrays are empty.
        if (permissionGranted) {
            // permission was granted, jippie!
            showMessage?.show("$requestPermissionName permission was granted")
        } else {
            // permission denied,
            showMessage?.show("$requestPermissionName permission was denied")
        }
        return
    }

    /**
     * Check if we have permissions, if not it shows a dialog requesting the permission and "onRequestPermissionsResult(..)" is called with the result
     *
     * @return TRUE if all permission was given else FALSE
     */
    private fun checkForPermission(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (!checkPermission(permission)) {
                requestPermission(permission)
                return false
            }
        }
        return true
    }

    /**
     * Request permission show a dialog of the permission was not already granted
     */
    private fun requestPermission(permission: String) {
        ThermalLog.d(TAG, "requestPermission(), permission:$permission")
        val permissionRationale = ActivityCompat.shouldShowRequestPermissionRationale(mainActivity!!, permission)
        if (permissionRationale) {
            showMessage?.show("Please provide permission:$permission")
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
        } else {
            // No explanation needed; request the permission
            val requestCode = getRequestCode(permission)
            val permissions = arrayOf(permission)
            ActivityCompat.requestPermissions(mainActivity!!, permissions, requestCode)
        }
    }

    /**
     * Get the permission request code for a permission
     *
     * @return -1 if permission can't be found otherwise a unique nr for the permission
     */
    @VisibleForTesting
    fun getRequestCode(permission: String): Int {
        val nwLength = PERMISSIONS_FOR_NW_DISCOVERY.size
        val storageLength = PERMISSIONS_FOR_STORAGE_DISCOVERY.size
        val btLength = PERMISSIONS_FOR_BLUETOOTH.size

        //Network
        for (i in 0 until nwLength) {
            if (permission == PERMISSIONS_FOR_NW_DISCOVERY[i]) {
                return i
            }
        }

        //Network + Storage
        for (i in 0 until storageLength) {
            if (permission == PERMISSIONS_FOR_STORAGE_DISCOVERY[i]) {
                return i + nwLength
            }
        }

        //Network + Storage + BT
        for (i in 0 until btLength) {
            if (permission == PERMISSIONS_FOR_BLUETOOTH[i]) {
                return i + nwLength + storageLength
            }
        }
        return -1
    }

    /**
     * Get the permission name matching the request permissionCode
     *
     * @return null if permission can't be found otherwise the name of the permission is returned
     */
    @VisibleForTesting
    fun getRequestPermissionName(permissionCode: Int): String? {
        val nwLength = PERMISSIONS_FOR_NW_DISCOVERY.size
        val storageLength = PERMISSIONS_FOR_STORAGE_DISCOVERY.size
        val btLength = PERMISSIONS_FOR_BLUETOOTH.size
        if (permissionCode < nwLength) {
            return PERMISSIONS_FOR_NW_DISCOVERY[permissionCode]
        } else if (permissionCode < nwLength + storageLength) {
            return PERMISSIONS_FOR_STORAGE_DISCOVERY[permissionCode - nwLength]
        } else if (permissionCode <= nwLength + storageLength + btLength) {
            return PERMISSIONS_FOR_BLUETOOTH[permissionCode - nwLength - storageLength]
        }
        return null
    }

    private fun checkPermission(permission: String): Boolean {
        ThermalLog.d(TAG, "checkPermission(), permission: $permission")
        val checkPermission = mainActivity!!.checkPermission(permission, Process.myPid(), Process.myUid())
        if (checkPermission == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        ThermalLog.d(TAG, "checkPermission Not granted $permission")
        return false
    }


}