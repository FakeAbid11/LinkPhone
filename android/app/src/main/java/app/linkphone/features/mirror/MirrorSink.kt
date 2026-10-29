package app.linkphone.features.mirror

import android.view.Surface

/** A capture target attached to the MediaProjection virtual display. */
interface MirrorSink {
    val kind: String
    val width: Int
    val height: Int
    val surface: Surface

    fun start()

    fun requestKeyframe()

    fun release()
}
