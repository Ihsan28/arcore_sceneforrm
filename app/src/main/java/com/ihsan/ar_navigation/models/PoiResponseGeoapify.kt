package com.ihsan.ar_navigation.models

import com.google.gson.annotations.SerializedName

data class PoiDirectionResponse(
    @SerializedName("features") val features: List<Feature>
)

data class Feature(
    @SerializedName("type") val type: String,
    @SerializedName("properties") val properties: Properties,
    @SerializedName("geometry") val geometry: Geometry
)

data class Properties(
    @SerializedName("mode") val mode: String,
    @SerializedName("waypoints") val waypoints: List<Waypoint>,
    @SerializedName("units") val units: String,
    @SerializedName("distance") val distance: Int,
    @SerializedName("distance_units") val distanceUnits: String,
    @SerializedName("time") val time: Double,
    @SerializedName("legs") val legs: List<Leg>
)

data class Waypoint(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double
)

data class Leg(
    @SerializedName("distance") val distance: Int,
    @SerializedName("time") val time: Double,
    @SerializedName("steps") val steps: List<Step>
)

data class Step(
    @SerializedName("from_index") val fromIndex: Int,
    @SerializedName("to_index") val toIndex: Int,
    @SerializedName("distance") val distance: Int,
    @SerializedName("time") val time: Double,
    @SerializedName("instruction") val instruction: Instruction
)

data class Instruction(
    @SerializedName("text") val text: String
)

data class Geometry(
    @SerializedName("type") val type: String,
    @SerializedName("coordinates") val coordinates: List<List<List<Double>>>
)

data class Coordinate(
    val lat: Double,
    val lon: Double
)
