package com.ihsan.arcore_sceneform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.lang.Math.toDegrees

class compassTest : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)

    private lateinit var trueNorthView: TextView

    private var magneticDeclination: Float = 0f

    private val alpha = 0.1f // Smoothing constant

    private val windowSize = 10 // Size of the moving average window
    private val azimuthQueue = ArrayDeque<Float>(windowSize)
    private var lastAzimuthDisplayed = 0f


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        trueNorthView = findViewById(R.id.azimuth)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        registerListeners()
        requestLocationUpdates()
    }

    private fun lowPass(input: FloatArray, output: FloatArray?): FloatArray {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] * (1 - alpha) + alpha * (input[i] - output[i])
        }
        return output
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelerometerReading = lowPass(event.values.clone(), accelerometerReading)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetometerReading = lowPass(event.values.clone(), magnetometerReading)
        }
        updateOrientationAngles()
    }


    @SuppressLint("SetTextI18n")
    private fun updateOrientationAngles() {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuthInRadians = orientationAngles[0]
        val azimuthInDegrees = toDegrees(azimuthInRadians.toDouble())
        val trueNorthAzimuth = (azimuthInDegrees + magneticDeclination).toFloat()

        enqueueAzimuth(trueNorthAzimuth)
        val averagedAzimuth = azimuthQueue.average().toFloat()

        if (Math.abs(averagedAzimuth - lastAzimuthDisplayed) > 1) {
            trueNorthView.text = "True North: ${averagedAzimuth.toInt()}°"
            lastAzimuthDisplayed = averagedAzimuth
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
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 0L, 0f, locationListener, Looper.getMainLooper()
        )
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val geomagneticField = GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis()
            )
            magneticDeclination = geomagneticField.declination
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle sensor accuracy changes if necessary
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
    }

    private fun unregisterListeners() {
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }

    private fun registerListeners() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }
}