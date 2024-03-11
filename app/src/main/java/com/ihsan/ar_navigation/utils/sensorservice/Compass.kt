package com.ihsan.ar_navigation.utils.sensorservice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.abs

class Compass(application: Application, val listener: CompassListener): SensorEventListener {

    private val TAG = "Compass"

    // Location
    private var fusedLocationClient: FusedLocationProviderClient

    // Sensor
    private var sensorManager: SensorManager
    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

//    private val lastAccelerometer = FloatArray(3)
//    private val lastMagnetometer = FloatArray(3)
//    private var lastAccelerometerSet = false
//    private var lastMagnetometerSet = false

    private var currentLocation: Location? = null
    private var azimuthInDegrees = 0.0

    private val mutableRotation = MutableLiveData<Float>()

    //testing
    private var locationManager: LocationManager

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private var magneticDeclination: Float = 0f

    private val alpha = 0.1f // Smoothing constant

    private val windowSize = 2 // Size of the moving average window
    private val azimuthQueue = ArrayDeque<Float>(windowSize)
    private var lastAzimuthDisplayed = 0f
    private var applicationCompass: Application
    private var locationListener: LocationListener

    init {
        this.applicationCompass=application

        //testing
        sensorManager = applicationCompass.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = applicationCompass.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationCompass)

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val geomagneticField = GeomagneticField(
                    location.latitude.toFloat(),
                    location.longitude.toFloat(),
                    location.altitude.toFloat(),
                    System.currentTimeMillis()
                )
                magneticDeclination = geomagneticField.declination
                checkPermission()
                listener.getCurrentLocation(location)
                fusedLocationClient.lastLocation.addOnSuccessListener { fusedLocation ->
                    currentLocation = fusedLocation
                    //passing location through listener
                    listener.getCurrentLocation(fusedLocation)
                    Log.d(TAG, "getlocation: CurrentLocation ${fusedLocation.latitude} ${fusedLocation.longitude}")
                }.addOnFailureListener {
                    Log.e(TAG, "Could not get location")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        getCurrentLocation {
            Log.d(TAG, "init: getCurrentLocation: $it")
            currentLocation = it
            listener.getCurrentLocation(it)
        }

        registerListeners()
        requestLocationUpdates()
    }

    private fun checkPermission(){
        if (ActivityCompat.checkSelfPermission(
                applicationCompass, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationCompass, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
    }

    fun registerListeners() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun unregisterListeners() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //Log.d(TAG, "onAccuracyChanged: $sensor, $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelerometerReading = lowPass(event.values.clone(), accelerometerReading)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetometerReading = lowPass(event.values.clone(), magnetometerReading)
        }
        updateOrientationAngles()
    }

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] * (1 - alpha) + alpha * (input[i] - output[i])
        }
        return output
    }

    @SuppressLint("SetTextI18n")
    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuthInRadians = orientationAngles[0]
        azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble())
        val trueNorthAzimuth = (azimuthInDegrees+magneticDeclination).toFloat()

        enqueueAzimuth(trueNorthAzimuth)
        var averagedAzimuth = azimuthQueue.average().toFloat()

        if (abs(averagedAzimuth - lastAzimuthDisplayed) > 1) {
            lastAzimuthDisplayed = averagedAzimuth

            if (averagedAzimuth<0){
                averagedAzimuth+=360
            }

            averagedAzimuth= averagedAzimuth*100/100

            //passing azimuth through azimuth
            listener.onNewAzimuth(averagedAzimuth, magneticDeclination, orientationAngles)
        }
    }

    private fun enqueueAzimuth(azimuth: Float) {
        if (azimuthQueue.size >= windowSize) {
            azimuthQueue.removeFirst()
        }
        azimuthQueue.addLast(azimuth)
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                applicationCompass, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationCompass, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "getCurrentLocation: Permission not granted")
            return
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 0L, 0f, locationListener, Looper.getMainLooper()
        )
    }



//    override fun onSensorChanged(event: SensorEvent?) {
//        if (event == null) {
//            return
//        }
//        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
//            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
//            //Log.d(TAG, "Accelerometer: ${event.values[0]}, ${event.values[1]}, ${event.values[2]}")
//        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
//            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
//            //Log.d(TAG, "Magnetometer: ${event.values[0]}, ${event.values[1]}, ${event.values[2]}")
//        }
//
//        // Update rotation matrix, which is needed to update orientation angles.
//        SensorManager.getRotationMatrix(
//            rotationMatrix, null, accelerometerReading, magnetometerReading
//        )
//        SensorManager.getOrientation(rotationMatrix, orientationAngles)
//
////        azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble())*1000/1000.0
////        compassView.rotation = -azimuthInDegrees.toFloat()
////        if (azimuthInDegrees < 0) {
////            azimuthInDegrees += 360.0
////        }
////
////        azimuthView.text = "azimuth: ${orientationAngles[0]}°"
//        calibrateSensor3Axis()
//    }

//    private fun calibrateSensor3Axis(){
//        SensorManager.getOrientation(rotationMatrix, orientationAngles)
//        val azimuth = orientationAngles[0]
//        val pitch = orientationAngles[1]
//        val roll = orientationAngles[2]
//        val rollMatrix= arrayOf(
//            arrayOf(1f, 0f, 0f),
//            arrayOf(0f, cos(roll), -sin(roll)),
//            arrayOf(0f, sin(roll), cos(roll))
//        )
//        val pitchMatrix = arrayOf(
//            arrayOf(cos(pitch), 0f, sin(pitch)),
//            arrayOf(0f, 1f, 0f),
//            arrayOf(-sin(pitch), 0f, cos(pitch))
//        )
//        val x = arrayOf(arrayOf(azimuth), arrayOf(pitch), arrayOf(roll))
//        val y = multiplyMatrices(pitchMatrix, rollMatrix)
//        val z = multiplyMatrices(y, x)
//
//        val finalValueInRadians =-((Math.toDegrees(z[0][0].toDouble()) + 360) % 360).toFloat()
//        mutableRotation.postValue(finalValueInRadians)
//
//        compassView.rotation = finalValueInRadians
//
//        azimuthView.text = "azimuth: ${finalValueInRadians}°"
//    }
//
//    fun multiplyMatrices(matrix1: Array<Array<Float>>, matrix2: Array<Array<Float>>): Array<Array<Float>> {
//        val row1 = matrix1.size
//        val col1 = matrix1[0].size
//        val col2 = matrix2[0].size
//        val product = Array(row1) { Array(col2) { 0f } }
//
//        for (i in 0 until row1) {
//            for (j in 0 until col2) {
//                for (k in 0 until col1) {
//                    product[i][j] += matrix1[i][k] * matrix2[k][j]
//                }
//            }
//        }
//
//        return product
//    }
//    private fun calibrateCompass3Axis(){
//        val alpha = 0.97f
//        if (lastAccelerometerSet) {
//            lastAccelerometer[0] = alpha * lastAccelerometer[0] + (1 - alpha) * accelerometerReading[0]
//            lastAccelerometer[1] = alpha * lastAccelerometer[1] + (1 - alpha) * accelerometerReading[1]
//            lastAccelerometer[2] = alpha * lastAccelerometer[2] + (1 - alpha) * accelerometerReading[2]
//        } else {
//            lastAccelerometer[0] = accelerometerReading[0]
//            lastAccelerometer[1] = accelerometerReading[1]
//            lastAccelerometer[2] = accelerometerReading[2]
//            lastAccelerometerSet=true
//        }
//
//        if (lastMagnetometerSet) {
//            lastMagnetometer[0] = alpha * lastMagnetometer[0] + (1 - alpha) * magnetometerReading[0]
//            lastMagnetometer[1] = alpha * lastMagnetometer[1] + (1 - alpha) * magnetometerReading[1]
//            lastMagnetometer[2] = alpha * lastMagnetometer[2] + (1 - alpha) * magnetometerReading[2]
//        } else {
//            lastMagnetometer[0] = magnetometerReading[0]
//            lastMagnetometer[1] = magnetometerReading[1]
//            lastMagnetometer[2] = magnetometerReading[2]
//            lastMagnetometerSet=true
//        }
//
//        val rotationMatrix = FloatArray(9)
//        val inclineMatrix = FloatArray(9)
//        val remappedR = FloatArray(9)
//        val success = SensorManager.getRotationMatrix(rotationMatrix, inclineMatrix, lastAccelerometer, lastMagnetometer)
//        if (success) {
//            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedR)
//            val orientation = FloatArray(3)
//            SensorManager.getOrientation(remappedR, orientation)
//
//            var azimuth = Math.toDegrees(orientation[0].toDouble())*1000/1000.0
//            compassView.rotation = -azimuth.toFloat()
//            if (azimuth < 0) {
//                azimuth += 360.0
//            }
//            azimuthView.text = "azimuth: $azimuth°"
//        }
//    }

    private fun getCurrentLocation(onSuccess: (Location) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                applicationCompass, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationCompass, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "getCurrentLocation: Permission not granted")
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            currentLocation = location
            onSuccess(location)
            Log.d(TAG, "getlocation: CurrentLocation ${location.latitude} ${location.longitude}")
        }.addOnFailureListener {
            Log.e(TAG, "Could not get location")
        }
    }
}