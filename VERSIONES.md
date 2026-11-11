# Versions

Everything not listed here uses the versions fixed by the portfolio guide (Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2026.03.01, Room 2.8.5, DataStore 1.2.1, Navigation 2.9.8, MockK 1.14.2, Turbine 1.2.0, Robolectric 4.16.1, and so on). All of these resolve and build with Gradle on this machine.

| Change | Why |
|---|---|
| Koin 4.2.2 (`koin-android`, `koin-androidx-compose`, `koin-androidx-workmanager`, `koin-test`) replaces Hilt and `hilt-work` | Kotlin-first DI chosen by the project owner over Hilt/Dagger. Workers are built through Koin (`workManagerFactory()` and `workerOf`). Not in the guide's tables. |
| WorkManager 2.10.5 | Suggested version from the guide; resolves. |
| `kotlinx-serialization-json` 1.9.0 | Suggested version from the guide; resolves. Used for typed navigation routes, snapshot rows and the JSON report. |
| Coil 2.7.0 (`coil-compose`) | Pinned version from the guide; used for app icons through a custom fetcher. |
| `benchmark-macro-junit4` 1.4.1, plugin `androidx.baselineprofile` 1.4.1, `profileinstaller` 1.4.1 | Suggested versions from the guide; all resolve with AGP 8.13.2 and Kotlin 2.2.21. |
| `androidx.test.uiautomator:uiautomator` 2.3.0 | Needed by the benchmark module to drive the app; not in the guide's tables. |
| `org.jetbrains.kotlin:kotlin-test-junit` at the Kotlin version, `androidx.test:core-ktx` 1.6.1, `androidx.work:work-testing` 2.10.5 | Test dependencies. |
| Robolectric tests run on SDK 34 (`app/src/test/resources/robolectric.properties`, `application=android.app.Application`) | Robolectric's SDK 36 needs Java 21; the project uses JDK 17. A plain Application is used because the real one starts Koin. |

No Ktor, OkHttp or Retrofit: the app has no `INTERNET` permission.
