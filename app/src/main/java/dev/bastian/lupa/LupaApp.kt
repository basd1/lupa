package dev.bastian.lupa

import android.app.Application
import dev.bastian.lupa.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class LupaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LupaApp)
            // Initializes WorkManager with a worker factory that builds workers through Koin.
            workManagerFactory()
            modules(appModules)
        }
    }
}
