package dev.bastian.lupa.data

import android.content.pm.PackageManager
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options

/** Coil model for the launcher icon of an installed app. [updatedAt] makes a new icon replace the cached one. */
data class AppIcon(val packageName: String, val updatedAt: Long)

class AppIconKeyer : Keyer<AppIcon> {
    override fun key(data: AppIcon, options: Options): String = "app-icon:${data.packageName}:${data.updatedAt}"
}

/** Loads the icon from PackageManager; an app without a readable icon gets its default one. */
class AppIconFetcher(
    private val data: AppIcon,
    private val packageManager: PackageManager,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val drawable = try {
            packageManager.getApplicationIcon(data.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // Uninstalled since the scan: show no icon instead of failing the row.
            return null
        }
        return DrawableResult(drawable, isSampled = false, dataSource = DataSource.DISK)
    }

    class Factory(private val packageManager: PackageManager) : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, packageManager)
    }
}
