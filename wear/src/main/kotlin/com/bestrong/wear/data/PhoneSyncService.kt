package com.bestrong.wear.data

import com.bestrong.core.sync.SyncContract
import com.bestrong.wear.BeStrongWearApp
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Recibe rutinas e historial del móvil. */
class PhoneSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val container = (application as BeStrongWearApp).container
        events.filter { it.type == DataEvent.TYPE_CHANGED }.forEach { event ->
            val item = event.dataItem
            PhoneSync.applyDataItem(
                container.store,
                item.uri.path.orEmpty(),
                DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD),
            )
        }
        container.refreshSurfaces()
    }
}
