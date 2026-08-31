package com.chessbeater.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.chessbeater.vision.models.ChessAppTarget

/**
 * Model representing an installed application on the user's Android device.
 */
data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable? = null,
    val isChessApp: Boolean = false
)

/**
 * Utility helper that scans the Android PackageManager for launchable applications
 * and intelligently ranks chess-related apps at the top.
 */
object InstalledAppScanner {

    private val CHESS_KEYWORDS = listOf("chess", "catur", "lichess", "skak", "schach", "ajedrez", "xadrez")

    /**
     * Scans and returns all launchable installed apps, sorted with chess applications first.
     */
    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val seenPackages = HashSet<String>()
        val appList = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue // Skip Chess Beater itself
            if (!seenPackages.add(pkg)) continue

            val appName = try {
                resolveInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }

            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (e: Exception) {
                null
            }

            val lowerName = appName.lowercase()
            val lowerPkg = pkg.lowercase()

            val isChess = CHESS_KEYWORDS.any { keyword ->
                lowerName.contains(keyword) || lowerPkg.contains(keyword)
            }

            appList.add(
                InstalledAppInfo(
                    appName = appName,
                    packageName = pkg,
                    icon = icon,
                    isChessApp = isChess
                )
            )
        }

        // Sorting: Chess apps first, then alphabetical by App Name
        return appList.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isChessApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Automatically infers the optimal ChessAppTarget preset based on the package name.
     */
    fun mapPackageToTarget(packageName: String): ChessAppTarget {
        val lower = packageName.lowercase()
        return when {
            lower.contains("chess.com") || lower.contains("com.chess") -> ChessAppTarget.CHESS_COM
            lower.contains("lichess") -> ChessAppTarget.LICHESS
            else -> ChessAppTarget.CHESS_COM // Default 1:1 center crop for mobile chess apps
        }
    }
}
