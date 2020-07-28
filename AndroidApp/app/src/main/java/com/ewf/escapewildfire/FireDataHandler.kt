package com.ewf.escapewildfire

import com.here.android.mpa.common.GeoBoundingBox
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.GeoPolygon
import com.here.android.mpa.mapping.Map

class FireDataHandler {
    private var fireData = HashMap<String,FireData>()
    private var nearestId: String = ""
    private var map:Map? = null

    /*
     * setters/adders/generators
     */
    /**
     * adds a FireData object to the fireData map, if one with the same id already is present it is
     * replaced. If the map is available, they are also (re)drawn on the map.
     *
     * @param data the firedata that needs to be added/updated in the firedatahandler
     */
    fun addFireData(data: FireData) {
        //add the data to the map of data, if it is already contained within the map the entry is
        //replaced by the newer version
        fireData[data.getId()] = data
        //set the polygons on the map
        setFirePolygons()
    }

    /**
     * set the id of the fire nearest to the given coordinate
     *
     * @param coordinate coordinates for which to check which fire is the nearest
     * @return this instance of the firedatahandler
     */
    fun setNearest(coordinate: GeoCoordinate): FireDataHandler {
        //initialize temporary coordinate and string
        var nearest: GeoCoordinate? = null
        var id:String = ""

        //cycle through entries in the firedata list
        for (entry in fireData.values) {
            //get the fire's nearest coordinate
            var tmp = entry.getNearestCoordinates(coordinate)
            if (tmp != null) {
                if (nearest != null) {
                    //check if it is nearer the given coordinate than the previously found coordinates
                    if (coordinate.distanceTo(nearest) > coordinate.distanceTo(tmp)) {
                        //set the new nearest coordinate and id
                        nearest = tmp
                        id = entry.getId()
                    }
                } else {
                    //set the nearest coordinate and id in case one has not been set yet
                    nearest = tmp
                    id = entry.getId()
                }
            }
        }
        //set the nearestId variable to the id of the fire that was found to be nearest to the given coordinate
        nearestId = id
        //return the instance of this firedatahandler enabling to string function calls when using this function
        return this
    }

    /**
     * set the local map variable
     *
     * @param map the map that needs to be set
     * @return this instance of the firedatahandler
     */
    fun setMap(map:Map):FireDataHandler {
        //set the local map variable
        this.map = map
        //return the instance of this firedatahandler enabling to string function calls when using this function
        return this
    }

    /**
     * set the fire polygons on the map contained within this class
     *
     * @return true if successfully set the polygons on the map, false if not
     */
    fun setFirePolygons(): Boolean {
        if (map != null) {
            //set the polygons on the map
            setFirePolygons(map!!)
            return true
        }
        return false
    }

    /**
     * set the polygons on the given map
     *
     * @param map the map on which the polygons need to be drawn
     * @return true if successfully set the polygons on the map, false if not
     */
    private fun setFirePolygons(map:Map): Boolean {
        //get all the firedata entries from the map
        var list = fireData.values
        //temporary boolean
        var fireDrawn = false
        //cycle through the list of polygons
        for (entry in list) {
            //generate the polygons for the firedata
            entry.generateFirePolygon()
            //draw the polygons on the map and set the temporary boolean to true if successful
            if (entry.drawOnMap(map)) {
                fireDrawn = true
            }
        }
        return fireDrawn
    }

    /*
     * getters
     */
    /**
     * return all the fire polygons for the nearest fire
     *
     * @return the array of GeoPolygons of the nearest fire
     */
    fun getFirePolygons(): Array<GeoPolygon?>? {
        return getFirePolygons(nearestId)
    }

    /**
     * get polygons for dynamic penalty
     *
     * @param timeSlot the timeslot the phone is found in, null if outside of the fire
     * @return a list of polygons that can be used for the banned are in the dynamic penalty
     */
    fun getBannedAreaPolygons(timeSlot: TimeSlots?):ArrayList<GeoPolygon> {
        //initialize the list to hold the polygons that describe the area that has to be avoided
        //when the route is generated
        val bannedAreaPolygons = ArrayList<GeoPolygon>()
        //get the list of polygons depending on the timeslot given
        val tmpPolygons = if (timeSlot == null) {
            getFirePolygons(nearestId)
        } else {
            getFirePolygonsBefore(timeSlot)
        }

        if (tmpPolygons != null) {
            //cycle through the list of polygons
            for (poly in tmpPolygons) {
                if (poly != null) {
                    if (poly.numberOfPoints <= 40) {
                        //make it an open ended polygon (making sure that the last coordinate is not
                        //the same as the first)
                        if (poly.getPoint(0) == poly.getPoint(poly.numberOfPoints - 1)) {
                            poly.remove(poly.numberOfPoints - 1)
                        }
                        //add the polygon to the list of banned area polygons
                        bannedAreaPolygons.add(poly)
                    } else {
                        //split the polygon into smaller polygons to circumvent the hardcoded limit
                        //of a maximum of 40 points in a GeoPolygon when it is to be used as a
                        //banned area for the dynamic penalty in the route calculation, otherwise
                        //the CoreRouter will throw an invalid parameter error
                        var tmp = fireData[nearestId]?.splitPolygon(poly)
                        if (tmp != null) {
                            //add the polygon to the list of banned area polygons
                            bannedAreaPolygons.addAll(tmp)
                        }
                    }
                }
            }
        }
        return bannedAreaPolygons
    }

    /**
     * return a polygon from the nearest fire based on a time frame
     *
     * @param timeSlot the time frame for which the polygon needs to be retrieved
     * @return the polygon if there is a polygon for the given timeframe, else null
     */
    private fun getFirePolygon(timeSlot: TimeSlots): GeoPolygon? {
        return fireData[nearestId]?.getPolygon(timeSlot)
    }

    /**
     * return a list of all the polygons of the nearest fire
     *
     * @param id the id of the fire
     * @return the array of polygons describing the given id, if there is a fire associated with this
     * id. else the return is null
     */
    private fun getFirePolygons(id:String): Array<GeoPolygon?>? {
        return fireData[id]?.getPolygons()
    }

    /**
     * get a list of polygons before a certain time frame of the nearest fire
     *
     * @param timeSlot the time frame before which the previous polygons need to be retrieved
     * @return the array containing the polygons before the given time frame.
     */
    private fun getFirePolygonsBefore(timeSlot:TimeSlots): Array<GeoPolygon>? {
        return fireData[nearestId]?.getFirePolygonsBefore(timeSlot)
    }

    /**
     * get the bounding box for the whole fire (including the predicted fire) assuming that the later
     * predictions of the fire encompass a larger area than the earlier predictions. This concerns
     * the fire set as the nearest
     *
     * TODO make it possible to cycle through different fires (using the center fire button, therefore this function would probably also need updating)
     *
     * @return the boundingbox of the nearest fire
     */
    fun getBoundingBox(): GeoBoundingBox? {
        return fireData[nearestId]?.getBoundingBox()
    }

    /**
     * get the coordinates of the nearest edge of a polygon given a certain time frame and location
     *
     * @param timeSlot the time of the polygon for which the nearest coordinate along its edge needs
     * to be found
     * @param coordinate the coordinate for which the nearest edge needs to be found
     * @return if successful the coordinates of the nearest coordinate. null if not
     */
    fun getNearestEdgeCoordinate(timeSlot: TimeSlots, coordinate: GeoCoordinate): GeoCoordinate? {
        return getFirePolygon(timeSlot)?.getNearest(coordinate)
    }


    /*
     * other
     */
    /**
     * check if a set of coordinates is within the nearest fire
     * TODO upgrade this such that other fires are also checked
     *
     * @param coordinate the coordinate that needs to be checked if it is inside the nearest fire
     * @return the timeslot for the polygon of the nearest fire which the coordinate is found in
     */
    fun checkInsideFire(coordinate:GeoCoordinate): TimeSlots? {
        return fireData[nearestId]?.checkInsideFire(coordinate)
    }

    /**
     * giving the class a state of sorts, only having to instantiate it once and allowing other classes
     * the same FireDataGatherer object
     */
    companion object {
        val instance = FireDataHandler()
    }
}