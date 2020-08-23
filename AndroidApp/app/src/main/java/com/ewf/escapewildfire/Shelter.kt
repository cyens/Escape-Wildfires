package com.ewf.escapewildfire

import com.here.android.mpa.common.GeoCoordinate

class Shelter(private val lat:Double, private val long:Double, private val name:String) {
    fun getName(): String {
        return name
    }

    fun getCoords(): GeoCoordinate {
        return GeoCoordinate(lat,long)
    }
}