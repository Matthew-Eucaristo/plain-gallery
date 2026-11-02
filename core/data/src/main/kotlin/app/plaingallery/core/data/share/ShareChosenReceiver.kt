package app.plaingallery.core.data.share

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import app.plaingallery.core.data.DataGraph
import kotlinx.coroutines.launch

/** Receives which app the user picked in the system share sheet. */
class ShareChosenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val component = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java) ?: return
        val graph = (context.applicationContext as DataGraph.Owner).dataGraph
        val pending = goAsync()
        graph.scope.launch {
            try {
                graph.share.recordTarget(component)
            } finally {
                pending.finish()
            }
        }
    }
}
