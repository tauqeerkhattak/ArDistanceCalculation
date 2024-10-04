package com.eusopht.ardistancecalculation

import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eusopht.ardistancecalculation.utils.Constants
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.pow

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private lateinit var sceneView: ARSceneView
    private var firstAnchorNode: AnchorNode? = null
    private var secondAnchorNode: AnchorNode? = null
    private var isLoading = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sceneView = findViewById<ARSceneView>(R.id.sceneView).apply {
            lifecycle = this@MainActivity.lifecycle
            planeRenderer.isEnabled = true
            configureSession { session, config ->
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            }
            onGestureListener = GestureDetector.SimpleOnGestureListener()
            onTouchEvent = { event, node ->
                println("EVENT: ${event.action} ${event.pointerCount}")
                if (event.action == MotionEvent.ACTION_DOWN) {
                    println("DOUBLE TAP EVENT")
                    handleDoubleTap(event)
                }
                false
            }
//            onSessionUpdated = { _, frame ->
//                if (firstAnchorNode == null || secondAnchorNode == null) {
//                    frame.getUpdatedPlanes()
//                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
//                        ?.let { plane ->
////                            addAnchorNode(plane.createAnchor(plane.centerPose))
//                        }
//                }
//            }
            onTrackingFailureChanged = { reason ->
//                this@MainActivity.trackingFailureReason = reason
                print("REASON: $reason")
            }
        }
    }

    private fun handleDoubleTap(event: MotionEvent) {
        val frame = sceneView.frame
        val cameraState = frame?.camera?.trackingState
        if (frame != null && cameraState == TrackingState.TRACKING) {
            println("CONDITIONS FULFILLED!")
            for (hitResult in frame.hitTest(event)) {
                val trackable = hitResult.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)) {
                    println("Adding anchor node!")
                    addAnchorNode(hitResult.createAnchor())
                    println("After Adding anchor node!")
                    break
                }
            }
        }
    }

    private fun addAnchorNode(anchor: Anchor) {
        println("FIRST: ${firstAnchorNode != null} SECOND: ${secondAnchorNode != null}")
        if (firstAnchorNode != null && secondAnchorNode != null) {
            firstAnchorNode?.detachAnchor()
            secondAnchorNode?.detachAnchor()
            firstAnchorNode = null
            secondAnchorNode = null
        }
        addAnchor(anchor, Constants.anchorEndUrl, 0.15f)
        drawLineBetween(firstAnchorNode, secondAnchorNode)
    }

    private fun addAnchor(anchor: Anchor, anchorUrl: String, scale: Float) {
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isEditable = false
                    lifecycleScope.launch {
                        buildModelNode(anchorUrl, scale)?.let { it : ModelNode ->
                            addChildNode(it)
                        }
                    }
                    if (firstAnchorNode == null) {
                        firstAnchorNode = this
                    } else if (secondAnchorNode == null) {
                        secondAnchorNode = this
                    }
                }
        )
    }

    private suspend fun buildModelNode(anchorUrl: String, scale: Float): ModelNode? {
        sceneView.modelLoader.loadModelInstance(
            anchorUrl
        )?.let { modelInstance ->
            return ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = scale,
//                centerOrigin = Position(y = -0.5f)
            ).apply {
                isEditable = false
            }
        }
        return null
    }

    private fun drawLineBetween(anchor1: AnchorNode?, anchor2: AnchorNode?) {
        if (anchor1 == null || anchor2 == null) {
            println("BOTH ARE NULL")
            return
        }
        val position1 = anchor1.worldPosition
        val position2 = anchor2.worldPosition
        val midpoint = Float3(
            (position1.x + position2.x) / 2,
            (position1.y + position2.y) / 2,
            (position1.z + position2.z) / 2,
        )
        val anchor = sceneView.session!!.createAnchor(Pose.makeTranslation(midpoint.toFloatArray()))
        addAnchor(anchor, Constants.cube, 0.015f)
        val firstPose = anchor1.pose
        val secondPose = anchor2.pose
        val dx = secondPose.tx() - firstPose.tx()
        val dy = secondPose.ty() - firstPose.ty()
        val dz = secondPose.tz() - firstPose.tz()
        val sum = pow(dx, 2f) + pow(dy , 2f) + pow(dz, 2f)
        val distance = sqrt(sum) * 100
        if (distance > 10) {
            val newAnchorNode = AnchorNode(sceneView.engine, anchor)
            drawLineBetween(anchor1, newAnchorNode)
            drawLineBetween(newAnchorNode, anchor2)
        }
        /*val message = String.format(Locale("en"),"%.4f", distance)
        AlertDialog
            .Builder(this)
            .setMessage("Distance: $message cm")
            .setPositiveButton("Okay") { dialog, b ->
                dialog.dismiss()
            }.setOnDismissListener {
                firstAnchorNode?.detachAnchor()
                secondAnchorNode?.detachAnchor()
                firstAnchorNode = null
                secondAnchorNode = null
            }.show() */

        // Get the position of the two nodes
//        val startPos = firstAnchorNode!!.worldPosition
//        val endPos = secondAnchorNode!!.worldPosition
//        val startVector = Vector3()
//        startVector.x = startPos.x
//        startVector.y = startPos.y
//        startVector.z = startVector.z
//
//        val endVector = Vector3()
//        endVector.x = endPos.x
//        endVector.y = endPos.y
//        endVector.z = endVector.z
//
//        // Calculate the direction and length of the line
//        val difference = Vector3.subtract(endVector, startVector)
//        val length = difference.length()
//
//        // Calculate the center point between the two nodes
//        val centerPos = Vector3.add(startVector, endVector).scaled(0.5f)
//        val rotation = Quaternion.lookRotation(difference.normalized(), Vector3.up())
//        val pose = sceneView.frame!!.camera.pose
//        val anchor = sceneView.session!!.createAnchor(pose)
//        sceneView.addChildNode(
//            AnchorNode(sceneView.engine, anchor)
//                .apply {
//                    isEditable = false
//                    lifecycleScope.launch {
//                        val url = Constants.MID_POINT_URL
//                        buildModelNode(url)?.let { it : ModelNode ->
//                            it.position = Float3(centerPos.x, centerPos.y, centerPos.z)
//                            it.worldRotation = Rotation(rotation.x, rotation.y, rotation.z)
//                            addChildNode(it)
//                        }
//                    }
//                }
//        )
    }

}