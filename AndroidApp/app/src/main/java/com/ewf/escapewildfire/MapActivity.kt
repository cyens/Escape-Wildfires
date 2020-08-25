package com.ewf.escapewildfire

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.here.android.mpa.common.*
import com.here.android.mpa.guidance.NavigationManager
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.*
import com.here.android.mpa.search.Location
import kotlinx.android.synthetic.main.map_view.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.*


class MapActivity : AppCompatActivity() {
    private val layoutId = R.layout.map_view


    /*
     * Variables
     */
    private var map : Map? = null
    private var mapFragment : AndroidXMapFragment? = null
    private var positioningManager: PositioningManager? = null
    private var navManager: NavigationManager? = null
    private var currentRoute: MapRoute? = null
    private var zoomLevel: Double = -1.1
    private var gbb: GeoBoundingBox? = null

    private var modeOfTransport:RouteOptions.TransportMode = RouteOptions.TransportMode.CAR
    private var navigationType = NavigationType.TURN_BY_TURN
    private var routeType = RouteOptions.Type.FASTEST

    private var locationState:LocationState = LocationState.LOCATION_CENTERED
    private var directionalNavState = DirectionalNavState.NONE
    private var paused = false

    private var img: Image? = null
    private var destination: GeoCoordinate? = null
    private var latestPosition:GeoCoordinate? = null

    private var country: String? = null
    private var emergencyMap = HashMap<String, EmergencyNumber>()
    private val shelterList = ArrayList<Shelter>()

    private val blockedRoads = ArrayList<GeoPolygon>()

    /*
     * Listeners
     */
    /**
     * Listener to respond to changes in the position and whether or not the map should be centered
     * on the position
     */
    private val positionListener: PositioningManager.OnPositionChangedListener = object :
        PositioningManager.OnPositionChangedListener {
        override fun onPositionUpdated(
            method: PositioningManager.LocationMethod,
            position: GeoPosition?, isMapMatched: Boolean
        ) {
            latestPosition = position?.coordinate
            if (map != null && !map!!.positionIndicator.isVisible) {
                //turn on the position indicator
                map?.positionIndicator?.isVisible = true
            }
            // set the center only when the app is in the foreground
            // to reduce CPU consumption
            if (!paused) {
                when (locationState) {
                    LocationState.LOCATION_CENTERED -> {
                        map!!.setCenter(
                            position!!.coordinate,
                            Map.Animation.NONE
                        )
                    }
                    LocationState.MOVING_TO_CENTERED -> {
                        if (map!!.zoomLevel == zoomLevel) {
                            locationState = LocationState.LOCATION_CENTERED
                        }
                    }
                    else -> {
                        TODO()
                    }
                }
            }

            if (country == null && latestPosition != null) {
                ReverseGeocodeRequest(latestPosition!!).execute(countryCodeReverseGeocodeListener)
            }
        }

        override fun onPositionFixChanged(
            method: PositioningManager.LocationMethod,
            status: PositioningManager.LocationStatus
        ) {
            Log.d("status", status.toString())
            Log.d("method", method.toString())
            if (status != PositioningManager.LocationStatus.AVAILABLE) {
                Toast.makeText(applicationContext, "lost ${method.name} fix", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    applicationContext,
                    "acquired ${method.name} fix",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Listener for the reverse geocode request to retrieve the device's country
     */
    private val countryCodeReverseGeocodeListener: ResultListener<Location> =
        ResultListener<Location> { data, error ->
            if (error == ErrorCode.NONE) {
                country = data?.address?.countryCode
                //Log.d("code", country)
            } else {
                TODO()
            }
        }

    /**
     * listener to listen to the gestures made on the map. some are overridden to add extra functionality
     */
    private val gestureListener: MapGesture.OnGestureListener = object: MapGesture.OnGestureListener.OnGestureListenerAdapter() {
        override fun onPanStart() {
            super.onPanStart()
            cancelLocationCentered()
        }

        override fun onMultiFingerManipulationStart() {
            super.onMultiFingerManipulationStart()
            cancelLocationCentered()
        }
    }

    /**
     * Cancel having the map centered on the phone's location
     */
    private fun cancelLocationCentered() {
        if (navManager != null && navManager!!.runningState == NavigationManager.NavigationState.RUNNING) {
            navManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.NONE
        } else {
            locationState = LocationState.NOT_CENTERED
        }
    }

    /**
     * listener to respond to the route calculation, whether it is finsished or in progress
     */
    private val routeListener: CoreRouter.Listener = object: CoreRouter.Listener {
        override fun onCalculateRouteFinished(
            routeResult: MutableList<RouteResult>?,
            error: RoutingError
        ) {
            // If the route was calculated successfully
            // Display a message indicating route calculation failure
            if (error == RoutingError.NONE){
                if (routeResult != null) {
                    if (currentRoute != null) {
                        map?.removeMapObject(currentRoute!!)
                    }

                    gbb = routeResult[0].route.boundingBox
                    // Render the route on the map
                    currentRoute = MapRoute(routeResult[0].route)
//                    currentRoute!!.isManeuverNumberVisible = true
                    map?.addMapObject(currentRoute!!)

                    //zoom to have the whole rout visible on screen
                    if (gbb != null) {
                        locationState = LocationState.NOT_CENTERED
                        zoomToBoundingBox(gbb!!, map!!.orientation)
                    }

                    //set the nav button text
                    start_nav.text = START_NAVIGATION_TEXT
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Error:route results returned is not valid",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    applicationContext,
                    "Error:route calculation returned error code: $error",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("route calc", error.toString())
            }
        }

        override fun onProgress(p0: Int) {
            // Display a message indicating calculation progress
        }
    }

    /**
     * Listener to respond to route instruction events, e.g. turn instructions
     */
    private val instructionListener: NavigationManager.NewInstructionEventListener = object: NavigationManager.NewInstructionEventListener() {
        override fun onNewInstructionEvent() {
            val nextManeuver: Maneuver? = navManager?.nextManeuver
            val maneuverAfterNext: Maneuver? = navManager?.afterNextManeuver
            if (nextManeuver != null) {
                if (nextManeuver.action === Maneuver.Action.END) {
                    // notify the user that the route is complete
                    Toast.makeText(applicationContext, "Navigation complete", Toast.LENGTH_LONG).show()
                    navManager?.stop()
                    if (currentRoute != null) {
                        map?.removeMapObject(currentRoute!!)
                        currentRoute = null
                    }
                }
            }
            //set the navigation icon
            setManeuverInfo(nextManeuver, maneuverAfterNext)
        }
    }

    /**
     * Listener to respond when a new route has been calculated (e.g. in case of a reroute)
     */
    private val navigationEventListener: NavigationManager.NavigationManagerEventListener = object: NavigationManager.NavigationManagerEventListener() {
        override fun onRouteUpdated(newRoute: Route) {
            if (currentRoute != null) {
                //remove the old route if there was one and set the new one
                map?.removeMapObject(currentRoute!!)
                currentRoute = MapRoute(newRoute)
                map?.addMapObject(currentRoute!!)
            }
        }
    }

    /**
     * Listener to respond to the result of a reverse geocode request. Is used when converting a
     * set of coordinates to an address/town. first this reverse geocode request is used which will
     * receive the address which is then passed to the geocode request to get the coordinates of said
     * town. this way the nearest town to a set of coordinates can be retrieved
     */
    private val reverseGeocodeListener: ResultListener<Location> =
        ResultListener<Location> { data, error ->
            if (error == ErrorCode.NONE) {
                //get the phone's current location
                val position = positioningManager?.position?.coordinate
                //grab the city from the data
                val city = data?.address?.city
                if (position != null && city != null) {
                    //convert the city to a set of coordinates using the geocoding
                    val request = GeocodeRequest(city)
                    request.setSearchArea(position, 20000)
                    request.execute(geocodeListener)
                    //Log.d("nav", "${position}")
                } else {
                    TODO()
                }
            } else {
                TODO()
            }
        }

    /**
     * Listener to respond to the result of a geocode request.
     */
    private val geocodeListener: ResultListener<List<GeocodeResult>> =
        ResultListener<List<GeocodeResult>> { data, error ->
            if (error == ErrorCode.NONE) {
                val position = positioningManager?.position?.coordinate
                val destination = data?.get(0)?.location?.coordinate
                if (position != null && destination != null) {
                    generateRoute(position, destination, modeOfTransport, routeType)
                }
            }
        }

    /**
     * Listener to respond when the map engine has finished initializing.
     */
    private val engineListener: OnEngineInitListener = OnEngineInitListener { error: OnEngineInitListener.Error? ->
        if (error == OnEngineInitListener.Error.NONE) {
            // retrieve a reference of the map from the map fragment
            map = mapFragment!!.map
            positioningManager = PositioningManager.getInstance()
            if (positioningManager != null) {
                map?.setCenter(
                    positioningManager!!.lastKnownPosition.coordinate,
                    Map.Animation.NONE
                )
                //start position manager
                positioningManager!!.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR)

                //add position listener
                positioningManager!!.addListener(WeakReference(positionListener))
            } else {
                TODO()
            }
            setupNavManager()

            //add gesture listener
            mapFragment!!.mapGesture?.addOnGestureListener(
                gestureListener,
                Int.MIN_VALUE,
                true
            )
            //Set the startup zoom level
            map?.zoomLevel = (map!!.maxZoomLevel + map!!.minZoomLevel) / 1.5

            //draw the fire on the map
            val fireGatherer = FireDataHandler.instance
            if (map != null) {
                fireGatherer.setMap(map!!).setFirePolygons()
            }

            //create the image for the destination marker
            img = Image()
            var bitmap = BitmapFactory.decodeResource(
                resources,
                R.mipmap.location_marker_foreground
            )
            bitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, true)
            img?.setBitmap(bitmap)

            //Shelter by Luis Prado from the Noun Project
            val shelterImg = Image()
            var shelterBitmap = BitmapFactory.decodeResource(resources,R.mipmap.ic_shelter_foreground)
            shelterBitmap = Bitmap.createScaledBitmap(shelterBitmap,150,150,true)
            shelterImg.setBitmap(shelterBitmap)

            //add markers for shelter to the map
            //TODO: make them interactive
            //TODO: hide them at certain zoom levels/if they are too far away
            if (shelterList.size > 0) {
                for (shelter in shelterList) {
                    val marker = MapMarker(shelter.getCoords(), shelterImg)
                    marker.isDeclutteringEnabled = true
                    map?.addMapObject(marker)
                }
            }
        } else {
            Log.e(error.toString(), "Cannot initialize Map Fragment")
        }
    }

    /*
     * Code
     */
    /**
     * Called whenever this activity has been (re-)started
     *
     * @param savedInstanceState the bundle containing the information from when this activity had
     * been saved, e.g. when going to the next screen and returning
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //grab the mode of transport and navigation type set in the previous activity and
        //set set the local variables accordingly
        val tmp:RouteOptions.TransportMode = intent.extras?.get("ModeOfTransport") as RouteOptions.TransportMode
        modeOfTransport = tmp
        val tmp2:NavigationType = intent.extras?.get("NavigationType") as NavigationType
        navigationType = tmp2

        loadCSVData()

        //set the view
        setContentView(layoutId)

        //initialize the map
        this.mapFragment = supportFragmentManager.findFragmentById(R.id.navigation_mapfragment) as AndroidXMapFragment?

        //set the onEngineInit listener
        mapFragment!!.init(engineListener)

        //setup the buttons
        buttonSetup()
    }

    /**
     * Setup button listeners
     */
    private fun buttonSetup() {
        /*
         * Text
         */
        center_fire.text = CENTER_FIRE_TEXT

        start_nav.text = INITIALZE_NAVIGATION_TEXT

        navigation_zoom_in.text = ZOOM_IN_BUTTON_TEXT

        navigation_zoom_out.text = ZOOM_OUT_BUTTON_TEXT

        //set these after they have been initialzed (hence the .post, otherwise it will cause errors)
        nextTurnShape.post {
            val width = nextTurnShape.measuredWidth
            val height = nextTurnShape.measuredHeight
            Log.d("dimensions", "$width         $height")
            val painter = NavigateUIPainter(Color.argb(255, 220, 220, 220))
            nextTurnShape.addView(CustomPainter(this, width, height, painter))
        }

        afterNextTurnShape.post {
            val width = afterNextTurnShape.measuredWidth
            val height = afterNextTurnShape.measuredHeight
            Log.d("dimensions", "$width         $height")
            val painter = NavigateUIPainter(Color.argb(255, 192, 192, 192))
            afterNextTurnShape.addView(CustomPainter(this, width, height, painter))
        }

        directionalShape.post {
            val width = directionalShape.measuredWidth
            val height = directionalShape.measuredHeight
            val painter = DirectionalUIPainter(Color.argb(255, 220, 220, 220))
            directionalShape.addView(CustomPainter(this, width, height, painter))
        }

        /*
         * Listeners
         */
        navigation_zoom_in.setOnClickListener {
            if (navManager != null && navManager!!.runningState == NavigationManager.NavigationState.RUNNING) {
                navManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.NONE
             }

            if (zoomLevel == -1.1|| zoomLevel < map!!.zoomLevel){
                zoomLevel = map!!.zoomLevel
            }
            map?.setZoomLevel(map!!.zoomLevel * 1.05, Map.Animation.BOW)
            zoomLevel *= 1.05
//            Log.d("ZOOOOOOM in", "map: ${map?.zoomLevel}           var: $zoomLevel")
            if (map!!.zoomLevel != zoomLevel){
                updateZoomLevel()
            }
        }

        navigation_zoom_out.setOnClickListener {
            if (navManager != null && navManager!!.runningState == NavigationManager.NavigationState.RUNNING) {
                navManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.NONE
            }

            if (zoomLevel == -1.1 || zoomLevel > map!!.zoomLevel){
                zoomLevel = map!!.zoomLevel
            }
            map?.setZoomLevel(map!!.zoomLevel / 1.05, Map.Animation.BOW)
            zoomLevel /= 1.05
//            Log.d("ZOOOOOOM out", "map: ${map?.zoomLevel}           var: $zoomLevel")
            if (map!!.zoomLevel != zoomLevel){
                updateZoomLevel()
            }
        }

        navigation_locationButton.setOnClickListener {
            if (navManager != null && navManager!!.runningState == NavigationManager.NavigationState.RUNNING) {
                navManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.ROADVIEW
            } else {
                val position = positioningManager?.position
                zoomLevel = 15.0
                map!!.setCenter(
                    position!!.coordinate,
                    Map.Animation.BOW,
                    zoomLevel,
                    map!!.orientation,
                    map!!.tilt
                )
                locationState = LocationState.MOVING_TO_CENTERED
            }

        }

        start_nav.setOnClickListener {
            val position = positioningManager?.position?.coordinate
            if (position != null && position.isValid) {
                when (navigationType) {
                    NavigationType.TURN_BY_TURN -> turnByTurnButtonAction(position)
                    NavigationType.DIRECTIONAL -> directionalButtonAction(position)
                }
            } else {
                Toast.makeText(this, "Was unable to grab your current location.", Toast.LENGTH_LONG).show()
            }
        }

        center_fire.setOnClickListener {
            locationState = LocationState.NOT_CENTERED
            if (navManager != null && navManager!!.runningState == NavigationManager.NavigationState.RUNNING) {
                navManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.NONE
            }
            val bounding = if (latestPosition != null) {
                FireDataHandler.instance.setNearest(latestPosition!!).getBoundingBox()
            } else {
                null
            }

            if (bounding != null && map != null) {
                zoomToBoundingBox(bounding, map!!.orientation)
            }
        }

        emergency_call.setOnClickListener {
            startPhoneCall()
        }

        road_blocked.setOnClickListener {
            blockedRoad()
        }
    }

    /**
     * Imports the ISO Alpha-3 country code and their corresponding emergency phone numbers into
     * a hashmap mapping the country code to the phone number. Also imports the coordinates associated
     * with the shelters and puts these in a list (might be moved online)
     */
    private fun loadCSVData() {
        val ccenReader = BufferedReader(InputStreamReader(resources.openRawResource(R.raw.ccen)))
        val ccenData = ccenReader.readLines()
        for (element in ccenData) {
            val numbers = element.split(";")
            if (numbers.size == 4 && numbers[0].toLowerCase(Locale.ROOT) != "country") {
                emergencyMap[numbers[0]] = EmergencyNumber(numbers[1], numbers[2], numbers[3])
            }
        }

        val sheltersReader = BufferedReader(InputStreamReader(resources.openRawResource(R.raw.shelters)))
        val shelterData = sheltersReader.readLines()
        for (element in shelterData) {
            val data = element.split(";")
            if (data.size == 3 && data[0].toLowerCase(Locale.ROOT) != "lat") {
                try {
                    val lat = data[0].toDouble()
                    val long = data[1].toDouble()
                    val name = data[2]
                    shelterList.add(Shelter(lat,long,name))
                } catch (e:NumberFormatException) {
                    Log.e("NumberFormatException", e.message)
                }
            }
        }
    }

    /**
     * Dials a phone call to the emergency number associated with the location the user is at
     */
    private fun startPhoneCall(){
        //check if the country was found
        if (country != null) {
            val number = emergencyMap[country!!]
            //check if a number was found
            if (number != null) {
                //dial a phone call to the fire emergency number
                val uri = Uri.fromParts("tel", number.getFire(), null)
                val intent = Intent(Intent.ACTION_DIAL, uri)
                startActivity(intent)
            } else {
                //place an empty phone call in case no emergency number could be found and notify the user
                Toast.makeText(
                    applicationContext,
                    "unable to retrieve emergency number",
                    Toast.LENGTH_LONG
                ).show()
                emptyPhoneCall()
            }
        } else {
            //place an empty phone call in case no country was found and notify the user
            Toast.makeText(
                applicationContext,
                "Unable to obtain your location for getting the emergency number",
                Toast.LENGTH_LONG
            ).show()
            emptyPhoneCall()
        }
    }

    /**
     * mark the area ahead as blocked and recalculate route
     */
    private fun blockedRoad() {
        val circleCenter = getDestinationCoordinatesFromBearing(
            positioningManager!!.position.coordinate,250.0,
            positioningManager!!.position.heading)


        blockedRoads.add(circleToPolygon(circleCenter,200.0,8))
        if (currentRoute != null) {
            navManager?.stop()
            generateRoute(positioningManager!!.position.coordinate,currentRoute!!.route?.destination!!,modeOfTransport,routeType)
        }
    }

    /**
     * Returns a polygon of the circle described by the given center coordinate and radius. The
     * detail of the circle can be set using the amount parameter
     *
     * @param coord the center coordinate for the circle
     * @param radius the radius of the circle in meters
     * @param amount the amount of points the polygon is going to be made out of, minimum is always set to 3
     * @return
     */
    private fun circleToPolygon(coord: GeoCoordinate, radius:Double, amount:Int): GeoPolygon {
        val points = ArrayList<GeoCoordinate>()
        //ensure the amount of point in the polygon is at least 3
        val tmp = if (amount < 3) {
            3
        } else {
            amount
        }
        //generate the polygon points
        for (i in 0 until tmp) {
            val bearing = 360.0/amount * i.toDouble()
            points.add(getDestinationCoordinatesFromBearing(coord,radius,bearing))
        }
        //generate polygon
        //map?.addMapObject(MapPolygon(poly))
        return GeoPolygon(points)
    }

    /**
     * Dials a blank phone call, to use when no emergency number can be obtained
     */
    private fun emptyPhoneCall() {
        val uri = Uri.fromParts("tel", "", null)
        val intent = Intent(Intent.ACTION_DIAL, uri)
        startActivity(intent)
    }

    /**
     *  handles the action for the start_nav button when the user has selected to use the directional
     *  type navigation
     *
     * @param position the phone's current location should be passed here
     */
    private fun directionalButtonAction(position: GeoCoordinate) {
        //check the state of the directional navigation and handle the button press accordingly
        when (directionalNavState) {
            DirectionalNavState.NONE -> {
                //route will be calculated and the navigation will be ready to start
                destination = getDestination(position)

                overviewBoundingBoxDirectional(position)

                directionalNavState = DirectionalNavState.GENERATED
                start_nav.text = START_NAVIGATION_TEXT
            }
            DirectionalNavState.GENERATED -> {
                //the navigation will be started
                directionalNavState = DirectionalNavState.RUNNING
                start_nav.text = PAUSE_NAVIGATION_TEXT
                topLeftConstraint.visibility = View.VISIBLE
                centerOnLocationDirectional(position)
            }
            DirectionalNavState.RUNNING -> {
                //the navigation will be paused
                directionalNavState = DirectionalNavState.PAUSED
                start_nav.text = RESUME_NAVIGATION_TEXT
                topLeftConstraint.visibility = View.INVISIBLE
                overviewBoundingBoxDirectional(position)
            }
            DirectionalNavState.PAUSED -> {
                //the navigation will be resumed
                directionalNavState = DirectionalNavState.RUNNING
                start_nav.text = PAUSE_NAVIGATION_TEXT
                topLeftConstraint.visibility = View.VISIBLE
                centerOnLocationDirectional(position)
            }
        }
    }

    /**
     * Zoom to the boundingbox that includes the phone's current location and the location of the
     * destination
     *
     * @param position the phone's current location should be passed here
     */
    private fun overviewBoundingBoxDirectional(position: GeoCoordinate) {
        if (destination != null) {
            if (img != null) {
                //set the destination marker on the destination
                setDestinationMarker(destination!!)
                //draw a line from the start to the destination
                val line = GeoPolyline()
                line.add(position)
                line.add(destination!!)
                //grab the boundingbox of the line
                val boundingBox = line.boundingBox

                locationState = LocationState.NOT_CENTERED

                //center the map on the boundingbox and thus on the 'route' as far as it can be called that
                if (boundingBox != null) {
                    zoomToBoundingBox(boundingBox, map!!.orientation)
                }
            }
        }
    }

    /**
     * Zooms to a given bounding box with a padding around it
     *
     * @param boundingBox the boundingbox to zoom to
     * @param orientation the orientation that the map should have after the zoom, default 0 degrees
     */
    private fun zoomToBoundingBox(boundingBox: GeoBoundingBox, orientation: Float = 0f) {
        //generate the width and height for the zoom to zoom to to have some padding around the bounding box
        val height: Int = (resources.displayMetrics.heightPixels * 0.95).roundToInt()
        val width: Int = (resources.displayMetrics.widthPixels * 0.95).roundToInt()
        map?.zoomTo(boundingBox, width, height, Map.Animation.NONE, orientation)
    }

    /**
     * Center the map on the phone's location when in the directional navigation
     *
     * @param position the phone's current location should be passed here
     */
    private fun centerOnLocationDirectional(position: GeoCoordinate) {
        //get the orientation for the map
        val heading = if (destination != null) {
            position.getHeading(destination!!).toFloat()
        } else {
            map!!.orientation
        }

        //zoom to the location
        zoomLevel = 15.0
        map!!.setCenter(
            position,
            Map.Animation.NONE,
            zoomLevel,
            heading,
            map!!.tilt
        )
        //set the locationstate to moving, such that the position listener does not interrupt the animation
        locationState = LocationState.MOVING_TO_CENTERED
    }

    /**
     * handles the action for the start_nav button when the user has selected to use the turn-by-turn
     * type navigation
     *
     * @param position the phone's current location should be passed here
     */
    private fun turnByTurnButtonAction(position: GeoCoordinate) {
        //check the navManager state
        when (navManager!!.runningState) {
            NavigationManager.NavigationState.IDLE -> {
                //nothing has been done yet
                if (currentRoute == null) {
                    //route has to be calculated
                    //generate a destination
                    val destination = getDestination(position)
                    //move the destination to the nearest town and subsequently (in the geocode listener)
                    //have the route be calculated
                    if (destination != null) {
                        val request = ReverseGeocodeRequest(
                            destination,
                            ReverseGeocodeMode.RETRIEVE_ADDRESSES,
                            0F
                        )
                        request.execute(reverseGeocodeListener)
                    } else {
                        Toast.makeText(
                            applicationContext,
                            "Could not generate a destination.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    //the route has been calculated, but navigation not yet started, start the navigation
                    startNavigation()
                    start_nav.text = PAUSE_NAVIGATION_TEXT
                    topRightConstraint.visibility = View.VISIBLE
                    road_blocked.visibility = View.VISIBLE
                }
            }
            NavigationManager.NavigationState.PAUSED -> {
                //resume the navigation
                navManager!!.resume()
                navManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.ROADVIEW
                start_nav.text = PAUSE_NAVIGATION_TEXT
                topRightConstraint.visibility = View.VISIBLE
            }
            else -> {
                //pause the navigation and zoom out to an overview of the route
                navManager?.pause()
                map?.zoomTo(gbb!!, Map.Animation.NONE, 0f)
                topRightConstraint.visibility = View.INVISIBLE
                start_nav.text = RESUME_NAVIGATION_TEXT
            }
        }
    }

    /**
     * Updates the zoom level
     */
    private fun updateZoomLevel(){
        map?.setZoomLevel(zoomLevel, Map.Animation.BOW)
    }

    /**
     * generate a route given a start and end coordinate, the mode of transport an the type of route
     *
     * @param start the starting coordinate for the route
     * @param end the destination coordinate for the route
     * @param mode the mode of transport for which the route needs to be calculated
     * @param type the type of route that needs to be calculated (e.g. short or fast)
     */
    private fun generateRoute(
        start: GeoCoordinate,
        end: GeoCoordinate,
        mode: RouteOptions.TransportMode,
        type: RouteOptions.Type
    ) {
        //set the necessary values
        val router = CoreRouter()
        val routePlan = RoutePlan()
        val fireHandler = FireDataHandler.instance.setNearest(start)
        //check if the phone is currently within a fire
        val timeSlot = fireHandler.checkInsideFire(start)

        //if the timeSlot is not null, the phone is inside the fire and thus an extra waypoint will
        //need to be added
        var possibleWayPoint:GeoCoordinate? = null
        if (timeSlot != null) {
            possibleWayPoint = fireHandler.getNearestEdgeCoordinate(timeSlot, start)
        }

        //add waypoints to the routePlan
        routePlan.addWaypoint(RouteWaypoint(start))
        if (possibleWayPoint != null) {
            routePlan.addWaypoint(RouteWaypoint(possibleWayPoint))
            setDestinationMarker(possibleWayPoint)
        }
        routePlan.addWaypoint(RouteWaypoint(end))

        //set the destination marker
        setDestinationMarker(end)

        //set the options for the route calculations
        val routeOptions = RouteOptions()
        routeOptions.transportMode = mode
        Log.d("transportMode", mode.toString())
        routeOptions.routeType = type

        routePlan.routeOptions = routeOptions

        //add the banned area penalty
        val polygon: ArrayList<GeoPolygon> = fireHandler.getBannedAreaPolygons(timeSlot)

        for (poly in polygon) {
            router.dynamicPenalty.addBannedArea(poly)
        }

        for (poly in blockedRoads) {
            router.dynamicPenalty.addBannedArea(poly)
        }

        //calculate the route
        router.calculateRoute(routePlan, routeListener)
    }

    /**
     * Set the destination marker on the map
     *
     * @param coordinate the coordinates of the destination
     */
    private fun setDestinationMarker(coordinate: GeoCoordinate) {
        //make sure the image is not null
        if (img != null) {
            //set the marker on the map
            val marker = MapMarker(coordinate, img!!)
            //adjust it in such a way that the bottom of the marker is at the coordinates, not the center
            marker.anchorPoint = PointF(
                (img!!.width / 2).toFloat(),
                (4 * img!!.height / 5).toFloat()
            )
            map?.addMapObject(marker)
        }
    }

    /**
     * get the destination for a given position
     *
     * @param position the position for which the destination needs to be calculated
     * @return the destination if successful, null if not
     */
    private fun getDestination(position: GeoCoordinate): GeoCoordinate? {
        //grab the center of the fire
        val center = FireDataHandler.instance.setNearest(position).getBoundingBox()?.center

        //calculate the destination coordinates, if the center of the fire is null, this will also be null
        return if (center != null) {
            getDestinationCoordinatesFromBearing(
                position, 10000.0, getBearing(
                    center,
                    position,
                    getFireBearing()
                )
            )
        } else {
            null
        }
    }

    /**
     * Get the bearing for the destination calculation given the bearing away from the fire and the
     * fire's bearing (the direction in which it is headed)
     *
     * @param centerOfFire coordinates of the center of the fire
     * @param position coordinates of the current position
     * @param fireBearing bearing of the fire
     * @return
     */
    private fun getBearing(
        centerOfFire: GeoCoordinate,
        position: GeoCoordinate,
        fireBearing: Double
    ): Double {
        //get the bearing between the current position and the center of the fire
        val directHeading:Double = centerOfFire.getHeading(position)

        //calculate the difference between the bearing away from the fire and the fire bearing and
        //adjust the bearing in such a way that it is not inline with the bearing of the fire itself
        return when (abs(directHeading - fireBearing)) {
            in 60..300 -> directHeading
            in 0..30 -> directHeading + 45
            in 330..360 -> directHeading - 45
            in 30..60 -> directHeading + 15
            in 300..330 -> directHeading - 15
            else -> 0.0
        }
    }

    /**
     * Get the bearing of the fire, where the fire is headed. the direction in which it is moving.
     *
     * @return the number of degrees that describes the heading of the fire
     */
    private fun getFireBearing(): Double {
        //create the local variable
        val bearing: Double
        //get the polygons describing the fire
        val polygons = FireDataHandler.instance.getFirePolygons()

        //create arrays for holding the coordinates
        val latCoords = ArrayList<Double>()
        val longCoords = ArrayList<Double>()

        //grab the coordinates from the center of the fire and store them in the Lists for all the time
        //frames except the current fire (so excluding NOW, but including the rest)
        if (polygons != null) {
            for (i in 1 until polygons.size - 1) {
                val tmp = polygons[i]?.boundingBox?.center
                if (tmp != null) {
                    latCoords.add(tmp.latitude)
                    longCoords.add(tmp.longitude)
                }
            }
        }

        //generate the average of the longitude and latitude
        val lat = latCoords.average()
        val lng = longCoords.average()

        //get the coordinates for the center of the fire as it is at this moment
        val nowCoords = polygons?.get(0)?.boundingBox?.center

        //calculate a bearing, 0.0 if not possible
        bearing = nowCoords?.getHeading(GeoCoordinate(lat, lng)) ?: 0.0

        //return the bearing
        return bearing
    }

    /**
     * Calculate the coordinates associated with a starting location, bearing and distance. Based on
     * code written by kiedysktos, retrieved from
     * https://stackoverflow.com/questions/10119479/calculating-lat-and-long-from-bearing-and-distance
     *
     * @param start the starting location
     * @param distanceInMetres the distance from the starting location to the destination
     * @param bearing the bearing to the destination in degrees
     * @return
     */
    private fun getDestinationCoordinatesFromBearing(
        start: GeoCoordinate,
        distanceInMetres: Double,
        bearing: Double
    ): GeoCoordinate {
        //set necessary variables
        val brngRad: Double = toRadians(bearing)
        val latRad: Double = toRadians(start.latitude)
        val lonRad: Double = toRadians(start.longitude)
        val earthRadiusInMetres = 6371000
        val distFrac = distanceInMetres / earthRadiusInMetres

        //calculate the longitude and latitude
        val latitudeResult: Double = asin(
            sin(latRad) * cos(distFrac) + cos(latRad) * sin(distFrac) * cos(
                brngRad
            )
        )
        val a: Double = atan2(
            sin(brngRad) * sin(distFrac) * cos(latRad), cos(distFrac) - sin(latRad) * sin(
                latitudeResult
            )
        )
        val longitudeResult: Double = (lonRad + a + 3 * PI) % (2 * PI) - PI

        //convert to GeoCoordinate
        return GeoCoordinate(toDegrees(latitudeResult), toDegrees(longitudeResult))
    }

    /**
     * Sets up the NavigationManager
     */
    private fun setupNavManager() {
        //get navigation manager
        navManager = NavigationManager.getInstance()

        //set map
        navManager!!.setMap(map)
        navManager!!.mapUpdateMode = NavigationManager.MapUpdateMode.ROADVIEW

        //add listeners
        navManager!!.addNewInstructionEventListener(WeakReference(instructionListener))
        navManager!!.addNavigationManagerEventListener(WeakReference(navigationEventListener))
    }

    /**
     * Start the navigation
     */
    private fun startNavigation() {
        val navManError = navManager!!.startNavigation(currentRoute?.route!!)
        //check if successfully started, if so, set the first directions
        if (navManError == NavigationManager.Error.NONE) {
            if (navManager != null) {
                setManeuverInfo(navManager!!.nextManeuver, navManager!!.afterNextManeuver)
            }
        }
    }

    /**
     * Set the icon and distances for the next and after the next maneuvers
     *
     * @param next the next maneuver
     * @param afterNext the maneuver after the next maneuver
     */
    @SuppressLint("SetTextI18n")
    private fun setManeuverInfo(next: Maneuver?, afterNext: Maneuver?) {
        //set the next turn information
        if (next != null) {
            setNavIcon(next, nextTurnView)
            nextTurnDistance.text = "${next.distanceFromPreviousManeuver} m"

            //set the turn after the next turn information
            if (afterNext != null) {
                setNavIcon(afterNext, afterNextTurnView)
                //add the distance to the next turn to the distance from the next turn to the turn after the next turn
                afterNextTurnDistance.text = "${afterNext.distanceFromPreviousManeuver+next.distanceFromPreviousManeuver} m"
            }
        }
    }

    /**
     * Set the icons associated with a given maneuver and view
     *
     * @param maneuver the maneuver that needs to be set
     * @param view the view for which the maneuver needs to be set
     */
    private fun setNavIcon(maneuver: Maneuver, view: ImageView) {
        when (maneuver.icon) {
            Maneuver.Icon.HEAVY_LEFT -> view.setImageResource(R.drawable.ic_sharp_left_turn)
            Maneuver.Icon.HEAVY_RIGHT -> view.setImageResource(R.drawable.ic_sharp_right_turn)
            Maneuver.Icon.LIGHT_LEFT -> view.setImageResource(R.drawable.ic_slight_left_turn)
            Maneuver.Icon.LIGHT_RIGHT -> view.setImageResource(R.drawable.ic_slight_right_turn)
            Maneuver.Icon.QUITE_LEFT -> view.setImageResource(R.drawable.ic_left_turn)
            Maneuver.Icon.QUITE_RIGHT -> view.setImageResource(R.drawable.ic_right_turn)
//            Maneuver.Icon.UNDEFINED -> TODO()
            Maneuver.Icon.KEEP_MIDDLE -> view.setImageResource(R.drawable.ic_keep_straight)
            Maneuver.Icon.KEEP_RIGHT -> view.setImageResource(R.drawable.ic_keep_straight)
            Maneuver.Icon.KEEP_LEFT -> view.setImageResource(R.drawable.ic_keep_straight)
            Maneuver.Icon.UTURN_LEFT -> view.setImageResource(R.drawable.ic_u_turn_left)
            Maneuver.Icon.UTURN_RIGHT -> view.setImageResource(R.drawable.ic_u_turn_right)
            Maneuver.Icon.ROUNDABOUT_1 -> view.setImageResource(R.drawable.ic_roundabout_first_righthand)
            Maneuver.Icon.ROUNDABOUT_2 -> view.setImageResource(R.drawable.ic_roundabout_second_righthand)
            Maneuver.Icon.ROUNDABOUT_3 -> view.setImageResource(R.drawable.ic_roundabout_third_righthand)
            Maneuver.Icon.ROUNDABOUT_4 -> view.setImageResource(R.drawable.ic_roundabout_fourth_righthand)
            Maneuver.Icon.ROUNDABOUT_5 -> view.setImageResource(R.drawable.ic_roundabout_fifth_righthand)
            Maneuver.Icon.ROUNDABOUT_6 -> view.setImageResource(R.drawable.ic_roundabout_sixth_righthand)
            Maneuver.Icon.ROUNDABOUT_7 -> view.setImageResource(R.drawable.ic_roundabout_seventh_righthand)
            Maneuver.Icon.ROUNDABOUT_8 -> view.setImageResource(R.drawable.ic_roundabout_eighth_righthand)
            Maneuver.Icon.ROUNDABOUT_9 -> view.setImageResource(R.drawable.ic_roundabout_ninth_righthand)
            Maneuver.Icon.ROUNDABOUT_10 -> view.setImageResource(R.drawable.ic_roundabout_tenth_righthand)
            Maneuver.Icon.ROUNDABOUT_11 -> view.setImageResource(R.drawable.ic_roundabout_eleventh_righthand)
            Maneuver.Icon.ROUNDABOUT_12 -> view.setImageResource(R.drawable.ic_roundabout_twelfth_righthand)
            Maneuver.Icon.ROUNDABOUT_1_LH -> view.setImageResource(R.drawable.ic_roundabout_first_lefthand)
            Maneuver.Icon.ROUNDABOUT_2_LH -> view.setImageResource(R.drawable.ic_roundabout_second_lefthand)
            Maneuver.Icon.ROUNDABOUT_3_LH -> view.setImageResource(R.drawable.ic_roundabout_third_lefthand)
            Maneuver.Icon.ROUNDABOUT_4_LH -> view.setImageResource(R.drawable.ic_roundabout_fourth_lefthand)
            Maneuver.Icon.ROUNDABOUT_5_LH -> view.setImageResource(R.drawable.ic_roundabout_fifth_lefthand)
            Maneuver.Icon.ROUNDABOUT_6_LH -> view.setImageResource(R.drawable.ic_roundabout_sixth_lefthand)
            Maneuver.Icon.ROUNDABOUT_7_LH -> view.setImageResource(R.drawable.ic_roundabout_seventh_lefthand)
            Maneuver.Icon.ROUNDABOUT_8_LH -> view.setImageResource(R.drawable.ic_roundabout_eighth_lefthand)
            Maneuver.Icon.ROUNDABOUT_9_LH -> view.setImageResource(R.drawable.ic_roundabout_ninth_lefthand)
            Maneuver.Icon.ROUNDABOUT_10_LH -> view.setImageResource(R.drawable.ic_roundabout_tenth_lefthand)
            Maneuver.Icon.ROUNDABOUT_11_LH -> view.setImageResource(R.drawable.ic_roundabout_eleventh_lefthand)
            Maneuver.Icon.ROUNDABOUT_12_LH -> view.setImageResource(R.drawable.ic_roundabout_twelfth_lefthand)
            Maneuver.Icon.GO_STRAIGHT -> view.setImageResource(R.drawable.ic_keep_straight)
//            Maneuver.Icon.ENTER_HIGHWAY_RIGHT_LANE -> TODO()
//            Maneuver.Icon.ENTER_HIGHWAY_LEFT_LANE -> TODO()
//            Maneuver.Icon.LEAVE_HIGHWAY_RIGHT_LANE -> TODO()
//            Maneuver.Icon.LEAVE_HIGHWAY_LEFT_LANE -> TODO()
//            Maneuver.Icon.HIGHWAY_KEEP_RIGHT -> TODO()
//            Maneuver.Icon.HIGHWAY_KEEP_LEFT -> TODO()
//            Maneuver.Icon.START -> TODO()
            Maneuver.Icon.END -> view.setImageResource(R.drawable.ic_location_marker_100)
//            Maneuver.Icon.FERRY -> TODO()
//            Maneuver.Icon.PASS_STATION -> TODO()
//            Maneuver.Icon.HEAD_TO -> TODO()
//            Maneuver.Icon.CHANGE_LINE -> TODO()
            else -> view.setImageResource(R.drawable.location_icon_20db)
        }
    }

    /*
     * Android OnResume/Pause/Stop/etc.
     * function to handle when the activity is paused/stopped or destroyed. To make sure the
     * activity can resume again after it has been paused
     */
    override fun onResume() {
        super.onResume()
        paused = false
        if (positioningManager != null) {
            positioningManager!!.start(
                PositioningManager.LocationMethod.GPS_NETWORK
            )
        }
        if (navManager != null && navManager!!.runningState == NavigationManager.NavigationState.PAUSED) {
            navManager!!.resume()
        }
        if (currentRoute != null) {
            map?.addMapObject(currentRoute!!)
        }
    }

    // To pause positioning listener
    override fun onPause() {
        if (positioningManager != null) {
            positioningManager!!.stop()
        }
        if (navManager != null && navManager!!.runningState == NavigationManager.NavigationState.RUNNING) {
            navManager!!.pause()
        }
        paused = true
        super.onPause()
    }

    // To remove the positioning listener
    override fun onDestroy() {
        if (positioningManager != null) {
            // Cleanup
            positioningManager!!.removeListener(
                positionListener
            )
        }
        if (navManager != null && navManager!!.runningState != NavigationManager.NavigationState.IDLE) {
            //top the navmanager
            navManager!!.stop()
            //delete the route
            currentRoute = null
        }
        map = null
        super.onDestroy()
    }
}