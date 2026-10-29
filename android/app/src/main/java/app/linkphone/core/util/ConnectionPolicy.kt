package app.linkphone.core.util

object ConnectionPolicy {
    fun shouldAutoStart(
        paired: Boolean,
        autoConnect: Boolean,
        desiredConnected: Boolean,
    ): Boolean = paired && autoConnect && desiredConnected

    fun shouldStartOnBoot(
        paired: Boolean,
        autoConnect: Boolean,
        desiredConnected: Boolean,
    ): Boolean = paired && autoConnect && desiredConnected
}
