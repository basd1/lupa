package dev.bastian.lupa.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import coil.ImageLoader
import dev.bastian.lupa.data.AndroidInstalledAppsSource
import dev.bastian.lupa.data.AppIconFetcher
import dev.bastian.lupa.data.AppIconKeyer
import dev.bastian.lupa.data.DataStoreSettings
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.Clock
import dev.bastian.lupa.domain.InstalledAppsSource
import dev.bastian.lupa.domain.SettingsStore
import dev.bastian.lupa.domain.SystemClock
import dev.bastian.lupa.ui.apps.AppsViewModel
import dev.bastian.lupa.ui.detail.AppDetailViewModel
import dev.bastian.lupa.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<Clock> { SystemClock }
    single { AppDispatchers() }

    single<DataStore<Preferences>> {
        val context = androidContext()
        PreferenceDataStoreFactory.create { context.filesDir.resolve("datastore/settings.preferences_pb") }
    }
    single<SettingsStore> { DataStoreSettings(get()) }

    single<InstalledAppsSource> { AndroidInstalledAppsSource(androidContext(), get()) }
    single { AppCatalog(source = get(), settings = get(), clock = get(), dispatchers = get()) }

    single {
        ImageLoader.Builder(androidContext())
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(androidContext().packageManager))
            }
            .build()
    }

    viewModel { AppsViewModel(catalog = get(), settings = get(), dispatchers = get()) }
    viewModel { params -> AppDetailViewModel(packageName = params.get(), catalog = get()) }
    viewModel { SettingsViewModel(store = get()) }
}

val appModules = listOf(appModule)
