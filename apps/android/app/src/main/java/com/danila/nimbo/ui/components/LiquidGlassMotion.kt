package com.danila.nimbo.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * One sensor source for the whole theme. Every surface receives the same
 * smoothed light direction, keeping the glass layer visually coherent.
 */
@Composable
fun rememberLiquidGlassTilt(enabled: Boolean): LiquidGlassTilt {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tilt by remember { mutableStateOf(LiquidGlassTilt.Zero) }

    DisposableEffect(context, lifecycleOwner, enabled) {
        if (!enabled) {
            tilt = LiquidGlassTilt.Zero
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var registered = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val target = LiquidGlassTiltPolicy.fromGravity(
                        gravityX = event.values.getOrElse(0) { 0f },
                        gravityY = event.values.getOrElse(1) { 0f },
                        displayRotation = context.currentDisplayRotation()
                    )
                    // Sensor-delay UI plus a low-pass filter is responsive without
                    // making thin highlights shake with normal hand tremor.
                    val smoothing = 0.20f
                    tilt = LiquidGlassTilt(
                        x = tilt.x + (target.x - tilt.x) * smoothing,
                        y = tilt.y + (target.y - tilt.y) * smoothing
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            fun register() {
                if (!registered && gravitySensor != null) {
                    registered = sensorManager.registerListener(
                        listener,
                        gravitySensor,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }
            }

            fun unregister() {
                if (registered) {
                    sensorManager.unregisterListener(listener)
                    registered = false
                }
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> register()
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY -> unregister()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                register()
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                unregister()
                tilt = LiquidGlassTilt.Zero
            }
        }
    }

    val animatedX by animateFloatAsState(
        targetValue = tilt.x,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid_tilt_x"
    )
    val animatedY by animateFloatAsState(
        targetValue = tilt.y,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid_tilt_y"
    )

    return if (enabled) LiquidGlassTilt(animatedX, animatedY) else LiquidGlassTilt.Zero
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
private fun Context.currentDisplayRotation(): Int {
    val activity = findActivity()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity?.display?.rotation ?: Surface.ROTATION_0
    } else {
        activity?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}
