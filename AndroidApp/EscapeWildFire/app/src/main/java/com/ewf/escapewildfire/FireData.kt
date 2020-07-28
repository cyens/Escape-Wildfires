package com.ewf.escapewildfire

import android.graphics.Color
import com.here.android.mpa.common.GeoBoundingBox
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.GeoPolygon
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.MapPolygon

class FireData(private val id:String, private val coordinates: ArrayList<ArrayList<Array<Double>>>) {
    private var polygons: Array<GeoPolygon?> = Array(5) {
        null
    }

    /*
     * setters/generators
     */
    /**
     * Generate the polygons. Is done separately because this can only be done after the map engine
     * has been initialized. The API however is run before the map is used, so storing the polygon
     * data and the generation of the polygons are done separately.
     */
    fun generateFirePolygon() {
        var counter = 0
        //add a GeoPolygon to the array of polygons described by the array of coordinates
        for (list in coordinates) {
            polygons[counter] = getGeoPolygon(list)
            counter += 1
        }
        //set the polygons after the latest contained in the coordinates array to null, since these
        //are not contained in the data
        for (i in counter until polygons.size) {
            polygons[i] = null
        }
    }

    /*
     * getters
     */
    /**
     * get the id of the fire
     *
     * @return ID of the fire
     */
    fun getId(): String {
        return id
    }

    /**
     * get the polygons of the fire
     *
     * @return the polygons that describe this fire
     */
    fun getPolygons(): Array<GeoPolygon?> {
        return polygons
    }

    /**
     * get the polygon for a given time
     *
     * @param timeSlot a time frame to return the polygon for
     * @return the polygon associated with this timeslot/timeframe
     */
    fun getPolygon(timeSlot: TimeSlots): GeoPolygon? {
        return polygons[getPolygonIndex(timeSlot)]
    }

    /**
     * get the latest (non-null) polygon, the polygon that describes the fire the furthest into the future
     *
     * @return last polygon contained in this firedata
     */
    private fun getLatestPolygon(): GeoPolygon? {
        //initialize temporary variable
        var polygon: GeoPolygon? = null
        //cycle through all the polygons in the list, if it is not null set the temporary variable
        //to the polygon from the list. Eventually setting polygon to the latest non-null polygon
        //in the list
        for (poly in polygons){
            if (poly != null) {
                polygon = poly
            }
        }
        return polygon
    }

    /**
     * get a list of polygons before a certain time frame
     *
     * @param timeSlot the time frame before which the polygons will be returned
     * @return the list of polygons that occur before the given timeframe
     */
    fun getFirePolygonsBefore(timeSlot:TimeSlots): Array<GeoPolygon> {
        //initialize the list of polygons
        val polygonsList = ArrayList<GeoPolygon>()
        //get the index of the given timeSlot
        val index = getPolygonIndex(timeSlot)
        //add all the polygons before the given timeSlot to the polygonsList
        for (i in 0 until index) {
            val poly = polygons[i]
            if (poly != null) {
                polygonsList.add(poly)
            }
        }
        return geoListToArray(polygonsList)
    }

    /**
     * get the index of a polygon depending on the time frame
     *
     * @param timeSlot the timeslot/timeframe for which the index is being requested
     * @return the index in the array of polygons associated with the timeslot/timeframe
     */
    private fun getPolygonIndex(timeSlot: TimeSlots): Int {
        return when (timeSlot){
            TimeSlots.NOW -> 0
            TimeSlots.FIFTEEN -> 1
            TimeSlots.THIRTY -> 2
            TimeSlots.FOURTYFIVE -> 3
            TimeSlots.SIXTY -> 4
        }
    }

    /**
     * get the time frame for a given polygon index
     *
     * @param index the index of the polygon in the array of polygons in  this firedata
     * @return the timeslot associated with this index
     */
    private fun getPolygonTimeSlot(index: Int): TimeSlots {
        return when (index) {
            0 -> TimeSlots.NOW
            1 -> TimeSlots.FIFTEEN
            2 -> TimeSlots.THIRTY
            3 -> TimeSlots.FOURTYFIVE
            else -> TimeSlots.SIXTY
        }
    }

    /**
     * get the bounding box for the whole fire (including the predicted fire) assuming that the later
     * predictions of the fire encompass a larger area than the earlier predictions.
     *
     * @return the boundingbox of this firedata
     */
    fun getBoundingBox(): GeoBoundingBox? {
        return getLatestPolygon()?.boundingBox
    }

    /**
     * get the coordinate of the edge of a polygon nearest to the given coordinate
     *
     * @param coordinate the coordinate for which the nearest coordinate needs to be retrieved
     * @return the nearest coordinated along the nearest edge if one is found, else null
     */
    fun getNearestCoordinates(coordinate: GeoCoordinate): GeoCoordinate? {
        //create variable to hold the nearest coordinate
        var nearest: GeoCoordinate? = null
        //cycle through all the polygons
        for (poly in polygons) {
            //get the nearest coordinate of the edge of the polygon
            var tmp = poly?.getNearest(coordinate)
            //store the coordinate nearest to the given coordinate
            if (tmp != null) {
                if (nearest != null) {
                    if (coordinate.distanceTo(nearest) > coordinate.distanceTo(tmp)) {
                        nearest = tmp
                    }
                } else {
                    nearest = tmp
                }
            }
        }
        return nearest
    }

    /**
     * get a GeoPolygon for a list of coordinates
     *
     * @param points list of coordinates in the form of a list containing an array of the individual coordinates
     * @return the GeoPolygon drawn by the coordinates
     */
    private fun getGeoPolygon(points: List<Array<Double>>):GeoPolygon {
        //create list of GeoCoordinates
        val polygonPoints = ArrayList<GeoCoordinate>()
        //convert the Array<Double> containing longitude, latitude and altitude to GeoCoordinate
        for (coordsArray in points) {
            polygonPoints.add(GeoCoordinate(coordsArray[1],coordsArray[0],coordsArray[2]))
        }
        //return GeoPolygon object described by polygonPoints
        return GeoPolygon(polygonPoints)
    }


    /*
     * other
     */
    /**
     * draw the fire on a given map
     *
     * @param map the map the fire will be drawn on
     * @return true if successful false if unsuccessful
     */
    fun drawOnMap(map: Map): Boolean {
        //boolean to later be returned
        var fireDrawn = false

        //convert the array into a list of nonNull GeoPolygon entries
        var nonNullPolygons = ArrayList<GeoPolygon>()
        for (poly in polygons) {
            if (poly != null){
                nonNullPolygons.add(poly)
            }
        }

        //cycle through the polygons list
        for (poly in nonNullPolygons) {
            //generate a mapObject from the polygon
            val mapPolygon = MapPolygon(poly)

            //set the opacity for the colour of the fire. The outermost polygon will have the
            //base opacity, which is set depending on
            var alpha = if (polygons.indexOf(poly) == nonNullPolygons.size) {
                when (nonNullPolygons.size){
                    0 -> 200
                    1 -> 165
                    2 -> 130
                    3 -> 95
                    4 -> 50
                    else -> 45
                }
            } else {
                45
            }

            //WIP trying to get the color to start of with RED in the center and the subsequent fires
            //to go through ORANGE to eventually YELLOW instead of different shades of RED.
//            when (polygons.indexOf(poly)) {
//                0 -> {
//                    mapPolygon.fillColor = Color.argb(alpha, 255, 0, 0)
//                }
//                1 -> {
//                    mapPolygon.fillColor = Color.argb(alpha, 255,90,0)
//                }
//                2 -> {
//                    mapPolygon.fillColor = Color.argb(alpha, 255,154,0)
//                }
//                3 -> {
//                    mapPolygon.fillColor = Color.argb(alpha, 255,206,0)
//                }
//                4 -> {
//                    mapPolygon.fillColor = Color.argb(alpha, 255,232,8)
//                }
//            }

            //set the fill and line color of the polygon
            mapPolygon.fillColor = Color.argb(alpha, 255, 0, 0)
            mapPolygon.lineColor = Color.argb(0,0,0,0)
            //add the polygon to the map
            map.addMapObject(mapPolygon)

            //adding polygons to the map was successful
            fireDrawn = true
        }
        return fireDrawn
    }

    /**
     * check if a set of coordinates is within the fire
     *
     * @param coordinate the coordinate that needs to be checked to see if it is inside the fire
     * @return the timeslot the coordinates are contained within, null if not in the fire
     */
    fun checkInsideFire(coordinate:GeoCoordinate): TimeSlots? {
        //cycle through all the polygons that describe the fire
        for (i in polygons.indices) {
            //reverse the order in which the polygons are accessed, starting with the outermost
            //polygon (assuming that predictions further into the future, the polygons with greater
            //index, cover a bigger area than the previous polygons and that these previous polygons
            //are contained in the outer polygons)
            val index = polygons.size - 1 - i
            val poly = polygons[index]

            //check if the coordinates are contained within the polygon. return the timeslot
            //associated with this polygon if the coordinates are indeed contained within the
            //polygon
            if (poly != null) {
                if (poly.contains(coordinate)) {
                    return getPolygonTimeSlot(index)
                }
            }
        }
        return null
    }


    /**
     * split polygons with size over 40 into smaller polygons. maximum of 4 sub polygons. if the
     * fire is greater than can be fit in these 4 only the first 156 points will be used
     *
     * @param poly the polygon that needs to be split
     * @return list of smaller polygons
     */
    fun splitPolygon(poly: GeoPolygon): ArrayList<GeoPolygon> {
        //create a list for the split polygons
        val splitPolys = ArrayList<GeoPolygon>()
        //grab the index of the polygon to be able to access the raw data
        val index = polygons.indexOf(poly)
        //get the number of points of the polygon
        val size = poly.numberOfPoints
        //get the center coordinate of the polygon
        val center = poly.boundingBox!!.center
//        Log.d("center", poly.boundingBox!!.center.toString())
        //splitting up the polygon depending on the polygon size and making sure there is one point
        //overlap between polygons.
        //first the polygon is split into sublists after which the center coordinate is added to the
        //polygon.
        when (size) {
            in 40..78 -> {
                val geoPolyFirstHalf = getGeoPolygon(coordinates[index].subList(0,size/2+1))
                val geoPolySecondHalf = getGeoPolygon(coordinates[index].subList(size/2,size))
                geoPolyFirstHalf.add(center)
                geoPolySecondHalf.add(center)
                splitPolys.add(geoPolyFirstHalf)
                splitPolys.add(geoPolySecondHalf)
            }
            in 78..156 -> {
                val geoPolyFirstHalf = getGeoPolygon(coordinates[index].subList(0,size/4+1))
                val geoPolySecondHalf = getGeoPolygon(coordinates[index].subList(size/4,size/2+1))
                val geoPolyThirdHalf = getGeoPolygon(coordinates[index].subList(size/2,3*size/4+1))
                val geoPolyFourthHalf = getGeoPolygon(coordinates[index].subList(3*size/4,size))
                geoPolyFirstHalf.add(center)
                geoPolySecondHalf.add(center)
                geoPolyThirdHalf.add(center)
                geoPolyFourthHalf.add(center)
                splitPolys.add(geoPolyFirstHalf)
                splitPolys.add(geoPolySecondHalf)
                splitPolys.add(geoPolyThirdHalf)
                splitPolys.add(geoPolyFourthHalf)
            }
            else -> {
                val geoPolyFirstHalf = getGeoPolygon(coordinates[index].subList(0,39))
                val geoPolySecondHalf = getGeoPolygon(coordinates[index].subList(38,78))
                val geoPolyThirdHalf = getGeoPolygon(coordinates[index].subList(77,117))
                val geoPolyFourthHalf = getGeoPolygon(coordinates[index].subList(116,156))
                geoPolyFirstHalf.add(center)
                geoPolySecondHalf.add(center)
                geoPolyThirdHalf.add(center)
                geoPolyFourthHalf.add(center)
                splitPolys.add(geoPolyFirstHalf)
                splitPolys.add(geoPolySecondHalf)
                splitPolys.add(geoPolyThirdHalf)
                splitPolys.add(geoPolyFourthHalf)
            }
        }
        return splitPolys
    }

    /**
     * convert a list of GeoPolygons to an array of GeoPolygons
     *
     * @param list the list of polygons
     * @return the array of polygons
     */
    private fun geoListToArray(list: List<GeoPolygon>): Array<GeoPolygon> {
        //initialize empty array
        val arr = Array(list.size) {
            GeoPolygon()
        }
        //copy elements from the list to the array
        for (i in list.indices) {
            arr[i] = list[i]
        }
        return arr
    }
}