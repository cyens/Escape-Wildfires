package com.ewf.escapewildfire

class EmergencyNumber(private val police: String, private val ambulance: String, private val fire: String) {
    fun getPolice(): String {
        return police
    }

    fun getAmbulance(): String {
        return ambulance
    }

    fun getFire(): String {
        return fire
    }
}