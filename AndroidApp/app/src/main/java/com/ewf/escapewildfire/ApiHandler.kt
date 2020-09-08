package com.ewf.escapewildfire

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.AuthFailureError
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject


class ApiHandler(appContext: Context, workerParams: WorkerParameters):  Worker(appContext, workerParams){
    private val gson = GsonBuilder().create()

    val authToken = "{YOUR_BEARER_TOKEN}"

    /**
     * Listens to the result from the spaceRequest
     */
    private val spacesRequestListener:  Response.Listener<JSONArray> =
        Response.Listener<JSONArray> { response ->
            //convert the data to a string
            val body = response.toString()
//            Log.d("body",body)

            //specify the type to which the string will be converted
            val listType =
                object : TypeToken<List<Space?>>() {}.type

            //convert the string to a Space using GSon library
            val spacesList =
                gson.fromJson<List<Space?>>(body, listType)

//            Log.d("body",spacesList.toString())
            val queue = Volley.newRequestQueue(applicationContext)

            //create a feature request for each space that has been received
            for (space in spacesList) {
                if (space != null) {
                    //url for the request
                    val url = "https://xyz.api.here.com/hub/spaces/${space.id}/iterate"
//                    Log.d("url", url)

                    //request with bearer authentication
                    val featureRequest: JsonObjectRequest = object: JsonObjectRequest(Method.GET,url,null, featuresRequestListener, requestErrorListener ) {
                        @Throws(AuthFailureError::class)
                        override fun getHeaders(): Map<String, String> {
                            val headers: MutableMap<String, String> =
                                HashMap()

                            // Put access token in HTTP request.
                            headers["Authorization"] = "Bearer $authToken"
                            return headers
                        }
                    }
                    //add request to the queue of requests to be executed
                    queue.add(featureRequest)
                }
            }
        }

    /**
     * Listens to the responses received from a feature request
     */
    private val featuresRequestListener:  Response.Listener<JSONObject> =
        Response.Listener<JSONObject> { response ->
            //list element for the coordinates of the features to be placed in
            var list = ArrayList<ArrayList<Array<Double>>>()
            //convert the response to a string
            val body = response.toString()
//            Log.d("body",body)

            //conver the string to a featureCollection using the GSon library
            val featureCollection = gson.fromJson(body,FeatureCollection::class.java)
            //cycle through all the features and add their coordinates to the list
            for (feature in featureCollection.features) {
                when (feature.geometry.type){
                    "MultiPolygon" -> {
                        val nestedArrayCoordinates = feature.geometry.coordinates as ArrayList<ArrayList<ArrayList<ArrayList<Double>>>>
                        val coordinatesList = nestedArrayCoordinates[0][0]

                        list.add(generateArray(coordinatesList))
                    }
                    "Polygon" -> {
                        val nestedArrayCoordinates = feature.geometry.coordinates as ArrayList<ArrayList<ArrayList<Double>>>
                        val coordinatesList = nestedArrayCoordinates[0]

                        list.add(generateArray(coordinatesList))
                    }
                }
            }
            //add the data to the firedatahandler
            FireDataHandler.instance.addFireData(FireData(featureCollection.etag,list))
        }

    /**
     * Listens for the request errors
     */
    private val requestErrorListener: Response.ErrorListener =
        Response.ErrorListener { error ->
             //Callback method that an error has been occurred with the provided error code and optional
             //user-readable message.
            Log.e("XYZ_API", "That didn't work! Error $error" )
        }

    /**
     * the space request used to retrieve all the spaces contained in the HERE XYZ service
     */
    private val spacesRequest: JsonArrayRequest = object : JsonArrayRequest(Method.GET, "https://xyz.api.here.com/hub/spaces",null, spacesRequestListener, requestErrorListener) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val headers: MutableMap<String, String> =
                    HashMap()

                // Put access token in HTTP request.
                headers["Authorization"] = "Bearer $authToken"
                return headers
            }
        }


    /**
     * Override this method to do your actual background processing.  This method is called on a
     * background thread - you are required to **synchronously** do your work and return the
     * [androidx.work.ListenableWorker.Result] from this method.  Once you return from this
     * method, the Worker is considered to have finished what its doing and will be destroyed.  If
     * you need to do your work asynchronously on a thread of your own choice, see
     * [ListenableWorker].
     *
     *
     * A Worker is given a maximum of ten minutes to finish its execution and return a
     * [androidx.work.ListenableWorker.Result].  After this time has expired, the Worker will
     * be signalled to stop.
     *
     * @return The [androidx.work.ListenableWorker.Result] of the computation; note that
     * dependent work will not execute if you use
     * [androidx.work.ListenableWorker.Result.failure] or
     * [androidx.work.ListenableWorker.Result.failure]
     */
    override fun doWork(): Result {
        val queue = Volley.newRequestQueue(applicationContext)
        queue.add(spacesRequest)

        return Result.success()
    }

    /**
     * Converts a nested ArrayList to an ArrayList that contains an Array of doubles
     *
     * @param coordinatesList the nested ArrayList to be converted
     * @return the converted ArrayList containing an Array of doubles
     */
    private fun generateArray(coordinatesList:ArrayList<ArrayList<Double>>): ArrayList<Array<Double>> {
        var tmpList = ArrayList<Array<Double>>()
        for (list in coordinatesList) {
            var arr = Array<Double>(3){ 0.0 }
            arr[0] = list[0]
            arr[1] = list[1]
            arr[2] = list[2]
            tmpList.add(arr)
        }
        return tmpList
    }

    /**
     * Data template for the Spaces send by the HERE XYZ api
     *
     * @property id the id of the space
     * @property title the title of the space
     * @property description the description of the space
     */
    class Space(val id: String, val title: String, val description: String)

    /**
     * Data template for the FeatureCollections send by the HERE XYZ api
     *
     * @property etag the unique tag identifying the featurecollection
     * @property features the features contanied within the featureCollection
     */
    class FeatureCollection (val etag: String, val features: List<Feature>)

    /**
     * Data template for the Features send by the HERE XYZ api
     *
     * @property id the unique id of the feature
     * @property type the type of the feature (often just Feature)
     * @property geometry the coordinates that describe the feature
     */
    class Feature (val id: String, val type: String, val geometry: Geometry)

    /**
     * Data template for the Geometry send by the HERE XYZ api
     *
     * @property type the type of feature (e.g. polygon, multipolygon, line, etc)
     * @property coordinates the coordinates that describe the geometry
     */
    class Geometry(val type:String, val coordinates:List<Any>)
}