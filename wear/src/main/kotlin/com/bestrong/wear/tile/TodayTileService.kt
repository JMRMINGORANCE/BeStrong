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
import com.bestrong.core.model.Routine
import com.bestrong.core.training.Periodization
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import com.bestrong.wear.WearContainer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Tile «Hoy toca»: la sesión del programa activo (llega del móvil como primera rutina) o la
 * rutina que lleva más tiempo sin hacerse, con un botón para empezarla en el reloj.
 */
@AndroidEntryPoint
class TodayTileService : TileService() {
    @Inject lateinit var container: WearContainer

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(30 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(requestParams.deviceConfiguration)))
                .build(),
        )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    /** Rutina que toca: la del programa si la hay; si no, la que lleva más tiempo sin hacerse. */
    private fun todayRoutine(): Routine? {
        val routines = container.store.routines.value.filter { it.exercises.isNotEmpty() }
        routines.firstOrNull { it.id.startsWith(Periodization.ROUTINE_PREFIX) }?.let { return it }
        val last = container.store.history.value.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        return routines.minByOrNull { last[it.id] ?: 0L }
    }

    private fun layout(device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val routine = todayRoutine()
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
        if (routine != null) activity.addKeyToExtraMapping(MainActivity.EXTRA_ROUTINE, ActionBuilders.stringExtra(routine.id))
        val launch = ModifiersBuilders.Clickable.Builder()
            .setId("today")
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
            .build()

        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, routine?.name ?: getString(R.string.tile_today_none))
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .setMaxLines(3)
                    .build(),
            )
        routine?.let {
            content.addContent(
                Text.Builder(this, getString(R.string.tile_today_detail, it.exercises.size))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(0xFFBDBDBD.toInt()))
                    .build(),
            )
        }

        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(
                Text.Builder(this, getString(R.string.tile_today)).setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(BeStrongPalette.OrangeArgb)).build(),
            )
            .setContent(content.build())
            .setPrimaryChipContent(CompactChip.Builder(this, getString(R.string.tile_start), launch, device).build())
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
