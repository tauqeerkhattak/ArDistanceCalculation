package com.eusopht.ardistancecalculation

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.eusopht.ardistancecalculation.utils.Constants
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
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
                    break
                }
            }
        }
    }

    private fun addAnchorNode(anchor: Anchor) {
        if (firstAnchorNode != null && secondAnchorNode == null) {
            return
        }
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isEditable = true
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
                // Scale to fit in a 0.5 meters cube
                scaleToUnits = 0.05f,
                // Bottom origin instead of center so the model base is on floor
                centerOrigin = Position(y = -0.5f)
            ).apply {
                isEditable = false
            }
        }
        return null
    }

//    suspend fun buildViewNode(): ViewNode? {
//        return withContext(Dispatchers.Main) {
//            val engine = sceneView.engine
//            val materialLoader = sceneView.materialLoader
//            val windowManager = sceneView.viewNodeWindowManager ?: return@withContext null
//            val view = LayoutInflater.from(materialLoader.context).inflate(R.layout.view_node_label, null, false)
//            val ViewAttachmentManager(context, this).apply { onResume() }
//            val viewNode = ViewNode(engine, windowManager, materialLoader, view, true, true)
//            viewNode.position = Position(0f, -0.2f, 0f)
//            anchorNodeView = view
//            viewNode
//        }
//    }

}