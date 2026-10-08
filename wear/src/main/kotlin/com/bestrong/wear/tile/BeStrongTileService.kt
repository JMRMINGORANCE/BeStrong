package com.bestrong.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import com.bestrong.wear.WearContainer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Tile: entrenos de la semana, racha y botón para empezar a entrenar. */
@AndroidEntryPoint
class BeStrongTileService : TileService() {
    @Inject lateinit var container: WearContainer

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val layout = layout(requestParams.deviceConfiguration)
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(30 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun layout(device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val history = container.store.history.value
        val active = container.session.workout.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val week = WorkoutStats.weeklySummary(history, 1, today, zone).first()
        val streak = WorkoutStats.weeklyStreak(history, today, zone)

        val launch = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, "${week.workouts}")
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                    .setColor(argb(BRAND))
                    .build(),
            )
            .addContent(
                Text.Builder(this, getString(R.string.tile_week))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build(),
            )
            .addContent(
                Text.Builder(this, getString(R.string.tile_streak, streak, container.store.settings.value.unit.formatVolume(week.volume)))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(0xFFBDBDBD.toInt()))
                    .build(),
            )
            .build()

        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "BeStrong").setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(BRAND)).build(),
            )
            .setContent(content)
            .setPrimaryChipContent(
                CompactChip.Builder(this, getString(if (active != null) R.string.continue_ else R.string.train), launch, device).build(),
            )
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val BRAND = 0xFFFF6B1A.toInt()
    }
}
