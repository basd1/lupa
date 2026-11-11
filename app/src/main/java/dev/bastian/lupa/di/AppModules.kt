package dev.bastian.lupa.di

import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.Clock
import dev.bastian.lupa.domain.SystemClock
import org.koin.dsl.module

val appModule = module {
    single<Clock> { SystemClock }
    single { AppDispatchers() }
}

val appModules = listOf(appModule)
