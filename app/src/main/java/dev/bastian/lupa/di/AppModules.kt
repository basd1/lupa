package dev.bastian.lupa.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.work.WorkManager
import coil.ImageLoader
import dev.bastian.lupa.data.db.LupaDatabase
import dev.bastian.lupa.data.db.RoomSnapshotRepository
import dev.bastian.lupa.data.notify.AndroidChangeNotifier
import dev.bastian.lupa.data.work.AnalysisScheduler
import dev.bastian.lupa.data.work.AnalysisWorker
import dev.bastian.lupa.domain.AnalysisRunner
import dev.bastian.lupa.domain.ChangeNotifier
import dev.bastian.lupa.domain.SnapshotRepository
import dev.bastian.lupa.ui.changes.ChangesViewModel
import org.koin.androidx.workmanager.dsl.workerOf
import dev.bastian.lupa.data.AndroidInstalledAppsSource
import dev.bastian.lupa.data.AppIconFetcher
import dev.bastian.lupa.data.AppIconKeyer
import dev.bastian.lupa.data.DataStoreSettings
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.AppFilterStore
import dev.bastian.lupa.domain.Clock
import dev.bastian.lupa.domain.InstalledAppsSource
import dev.bastian.lupa.domain.SettingsStore
import dev.bastian.lupa.domain.SystemClock
import dev.bastian.lupa.ui.apps.AppsViewModel
import dev.bastian.lupa.ui.category.CategoryViewModel
import dev.bastian.lupa.ui.detail.AppDetailViewModel
import dev.bastian.lupa.ui.summary.SummaryViewModel
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

    single { AppFilterStore() }

    single {
        Room.databaseBuilder(androidContext(), LupaDatabase::class.java, "lupa.db").build()
    }
    single<SnapshotRepository> { RoomSnapshotRepository(get(), get()) }
    single { AnalysisRunner(catalog = get(), snapshots = get(), settings = get(), clock = get()) }

    single { WorkManager.getInstance(androidContext()) }
    single { AnalysisScheduler(get()) }
    single<ChangeNotifier> { AndroidChangeNotifier(androidContext()) }
    workerOf(::AnalysisWorker)

    viewModel { AppsViewModel(catalog = get(), filterStore = get(), settings = get(), dispatchers = get()) }
    viewModel { SummaryViewModel(catalog = get(), filterStore = get(), dispatchers = get()) }
    viewModel { params -> CategoryViewModel(category = params.get(), catalog = get(), dispatchers = get()) }
    viewModel { params -> AppDetailViewModel(packageName = params.get(), catalog = get()) }
    viewModel { ChangesViewModel(snapshots = get(), runner = get(), settings = get(), dispatchers = get()) }
    viewModel { SettingsViewModel(store = get(), scheduler = get()) }
}

val appModules = listOf(appModule)
