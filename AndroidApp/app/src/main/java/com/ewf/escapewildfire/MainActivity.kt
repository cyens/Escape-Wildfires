package com.ewf.escapewildfire

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.here.android.mpa.routing.RouteOptions
import com.savvyapps.togglebuttonlayout.Toggle
import com.savvyapps.togglebuttonlayout.ToggleButtonLayout
import kotlinx.android.synthetic.main.activity_main.*
import java.time.Duration
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    //called when this activity is started
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //check the permissions and, if the permissions have been granted, initialize the app.
        checkPermissions()
    }

    /**
     * Initialize is called to initialize the activity. It sets the view needed for this action
     * (in this case the main view) and requests a cache location for the application to temporarily
     * store some files (which is needed for the map to function). If this cannot be accomplished it
     * will give an error message and shut down. If the cache location has been established it will
     * setup the buttons (using buttonSetup()) and start the ApiHandler through setupPeriodicSycn()
     */
    @SuppressLint("MissingPermission")
    private fun initialize() {
        setContentView(R.layout.activity_main)

        // Set up disk cache path for the map service for this application
        // It is recommended to use a path under your application folder for storing the disk cache
        val success = MapSettingThingy.instance.getStatus(applicationContext)
        if (!success) {
            Toast.makeText(
                applicationContext,
                "Unable to set isolated disk cache path.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        } else {
            buttonSetup()
            setupPeriodicSync()
        }
    }

    /**
     * This runs all the code needed to get the buttons in working order. This includes setting their
     * text and respective listeners (code that is run when the button is pressed). For the
     * toggleButtons it also sets their default state.
     */
    private fun buttonSetup() {
        /**
         * Text setup
         */
        show_map.text = MAIN_SCREEN_BUTTON_TEXT

        transportExplainer.text = TBT_TEXT


        /*
         * listener setup
         */

        var modeOfTransport: RouteOptions.TransportMode = RouteOptions.TransportMode.CAR
        var navigationType = NavigationType.TURN_BY_TURN

        show_map.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("ModeOfTransport", modeOfTransport)
            intent.putExtra("NavigationType", navigationType)
            startActivity(intent)
        }

        transportToggleButton.onToggledListener = {
                toggleButtonLayout: ToggleButtonLayout, toggle: Toggle, selected: Boolean ->
            when (toggle.id) {
                transportToggleButton.toggles[0].id -> modeOfTransport = RouteOptions.TransportMode.CAR
                transportToggleButton.toggles[1].id -> modeOfTransport = RouteOptions.TransportMode.BICYCLE
                transportToggleButton.toggles[2].id -> modeOfTransport = RouteOptions.TransportMode.PEDESTRIAN
            }
            Log.d("modeOfTransport", modeOfTransport.toString())
            transportToggleButton.setToggled(toggle.id, true)
        }

        transportToggleButton.setToggled(transportToggleButton.toggles[0].id, true)

        navigationToggleButton.onToggledListener = {
                toggleButtonLayout: ToggleButtonLayout, toggle: Toggle, selected: Boolean ->
            when (toggle.id) {
                navigationToggleButton.toggles[0].id -> {
                    transportExplainer.text = TBT_TEXT
                    navigationType = NavigationType.TURN_BY_TURN
                }
                navigationToggleButton.toggles[1].id -> {
                    transportExplainer.text = DIRECTIONAL_TEXT
                    navigationType = NavigationType.DIRECTIONAL
                }
            }
            navigationToggleButton.setToggled(toggle.id, true)
        }

        navigationToggleButton.setToggled(navigationToggleButton.toggles[0].id, true)

    }

    /**
     * This sets up the ApiHandler to run every 15 minutes to retrieve the data of the fire from HERE XYZ.
     */
    private fun setupPeriodicSync() {
        //generate a request depending on which build of android the phone is running
        var request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PeriodicWorkRequestBuilder<ApiHandler>(Duration.ofMinutes(15))
        } else {
            PeriodicWorkRequestBuilder<ApiHandler>(15, TimeUnit.MINUTES)
        }.addTag("GetFireDataFromXYZ").build()

        //start the periodic Api request
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "HERE_XYZ_Api_work",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     */
    private fun checkPermissions() {
        val missingPermissions: MutableList<String> = ArrayList()
        // check all required dynamic permissions
        for (permission in REQUIRED_SDK_PERMISSIONS) {
            val result = ContextCompat.checkSelfPermission(this, permission)
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }
        if (missingPermissions.isNotEmpty()) {
            // request all missing permissions
            val permissions = missingPermissions
                .toTypedArray()
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS)
        } else {
            val grantResults = IntArray(REQUIRED_SDK_PERMISSIONS.size)
            grantResults.fill(PackageManager.PERMISSION_GRANTED)
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                grantResults)
        }
    }

    /**
     * This function is called when checkpermission is done and checks if the permissions have been
     * granted, if not all the permissions required for the app to work have been given the app lets
     * the user know and shuts down. If the necessary permissions have been granted initialize()
     * will be called.
     *
     * @param requestCode the code that specifies the type of request and thus the reponse
     * @param permissions an array that contains all the permissions
     * @param grantResults an array that contains whether or not the permission has been granted
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS -> {
                var index = permissions.size - 1
                while (index >= 0) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show()
                        finish()
                        return
                    }
                    --index
                }
                // all permissions were granted
                initialize()
            }
        }
    }
}
