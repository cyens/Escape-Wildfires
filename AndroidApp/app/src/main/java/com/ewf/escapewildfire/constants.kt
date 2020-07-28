package com.ewf.escapewildfire

import android.Manifest

/**
 * constants
 */
//permission request code
const val REQUEST_CODE_ASK_PERMISSIONS = 1


//Permissions that need to be explicitly requested from end user.
val REQUIRED_SDK_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.INTERNET,
    Manifest.permission.ACCESS_WIFI_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE)


const val TBT_TEXT = "Turn-by-turn navigation is the standard type of navigation. This is the" +
        "navigation used by traditional navigators such as TomTom, Google Maps, Apple Maps, Waze, etc. " +
        "Selecting this mode will provide you with this traditional type of route navigation."
const val DIRECTIONAL_TEXT = "In comparison to turn-by-turn navigation, this style of navigation " +
        "is rather simple. It shows you your current location, the direction in which your destination " +
        "is and your destination. In some cases this type of navigation can be advantageous."

const val MAIN_SCREEN_BUTTON_TEXT = "Show Map"

const val CENTER_FIRE_TEXT = "Center map on fire"

const val INITIALZE_NAVIGATION_TEXT = "Calculate route"

const val START_NAVIGATION_TEXT = "Start Navigation"

const val PAUSE_NAVIGATION_TEXT = "Pause Navigation"

const val RESUME_NAVIGATION_TEXT = "Resume Navigation"

const val ZOOM_IN_BUTTON_TEXT = "+"

const val ZOOM_OUT_BUTTON_TEXT = "-"



