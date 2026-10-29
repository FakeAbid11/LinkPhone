package app.linkphone

import android.app.Application
import app.linkphone.core.update.AppUpdater

class LinkPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppUpdater.start(this)
    }
}
