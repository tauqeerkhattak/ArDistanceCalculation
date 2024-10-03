package com.eusopht.ardistancecalculation

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eusopht.ardistancecalculation.utils.Constants
import com.google.android.filament.Box
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.rendering.Material
import dev.romainguy.kotlin.math.Float3

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.collision.Quaternion
import io.github.sceneview.collision.Vector3
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.launch


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
//        sceneView.viewNodeWindowManager = ViewAttachmentManager(context, this).apply { onResume() }
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
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isEditable = false
                    lifecycleScope.launch {
                        isLoading = true
                        val anchorUrl = if (firstAnchorNode == null) { Constants.anchorStartUrl } else { Constants.anchorEndUrl }
                        buildModelNode(anchorUrl)?.let { it : ModelNode ->
                            addChildNode(it)
                        }
//                        buildViewNode()?.let { addChildNode(it) }
                        isLoading = false
                    }
                    if (firstAnchorNode == null) {
                        firstAnchorNode = this
                    } else if (secondAnchorNode == null) {
                        secondAnchorNode = this
                        drawLineBetween()
                    }
                }
        )
    }

    private suspend fun buildModelNode(anchorUrl: String): ModelNode? {
        sceneView.modelLoader.loadModelInstance(
            anchorUrl
        )?.let { modelInstance ->
            return ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.15f,
//                centerOrigin = Position(y = -0.5f)
            ).apply {
                isEditable = false
            }
        }
        return null
    }

    private fun drawLineBetween() {
        if (firstAnchorNode == null || secondAnchorNode == null) {
            println("BOTH ARE NULL")
            return
        }
        println("DRAWING")
        // Get the position of the two nodes
        val startPos = firstAnchorNode!!.worldPosition
        val endPos = secondAnchorNode!!.worldPosition
        val startVector = Vector3()
        startVector.x = startPos.x
        startVector.y = startPos.y
        startVector.z = startVector.z

        val endVector = Vector3()
        endVector.x = endPos.x
        endVector.y = endPos.y
        endVector.z = endVector.z

        // Calculate the direction and length of the line
        val difference = Vector3.subtract(endVector, startVector)
        val length = difference.length()

        // Calculate the center point between the two nodes
        val centerPos = Vector3.add(startVector, endVector).scaled(0.5f)
        val lineNode = Node(sceneView.engine)

        sceneView.addChildNode(
            AnchorNode(sceneView.engine, firstAnchorNode!!.anchor)
                .apply {
                    isEditable = false
                    lifecycleScope.launch {
                        val rotation = Quaternion.lookRotation(difference.normalized(), Vector3.up())
                        buildModelNode(anchorUrl)?.let { it : ModelNode ->
                            it.position = Float3(centerPos.x, centerPos.y, centerPos.z)
                            it.worldRotation = Rotation(rotation.x, rotation.y, rotation.z)
                            addChildNode(it)
                        }
                    }
                }
        )
    }

}