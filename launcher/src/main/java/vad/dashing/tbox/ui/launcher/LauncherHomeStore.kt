package vad.dashing.tbox.ui.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

sealed class LauncherHomeItem {
    abstract val key: String
    abstract val slotIndex: Int

    data class App(val packageName: String, override val slotIndex: Int) : LauncherHomeItem() {
        override val key: String = "app:$packageName@$slotIndex"
    }

    data class Split(val presetId: String, override val slotIndex: Int) : LauncherHomeItem() {
        override val key: String = "split:$presetId@$slotIndex"
    }

    fun withSlot(newSlot: Int): LauncherHomeItem = when (this) {
        is App -> copy(slotIndex = newSlot)
        is Split -> copy(presetId = presetId, slotIndex = newSlot)
    }
}

internal object LauncherHomeStore {
    private const val PREFS = "tbox_launcher_app_config"
    private const val KEY_HOME = "home_items_json"
    private const val KEY_AUTOSTART = "autostart_item_key"

    /** Key ([LauncherHomeItem.key]) of the single shortcut launched at launcher start; null = off. */
    fun autostartKey(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_AUTOSTART, null)
            ?.takeIf { it.isNotBlank() }

    fun setAutostartKey(context: Context, key: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (key == null) remove(KEY_AUTOSTART) else putString(KEY_AUTOSTART, key) }
            .apply()
    }

    fun loadItems(context: Context): List<LauncherHomeItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HOME, null)
        if (!raw.isNullOrBlank()) {
            return parseItems(raw)
        }
        return migrateFromGrid(context)
    }

    fun saveItems(context: Context, items: List<LauncherHomeItem>) {
        val array = JSONArray()
        items.forEach { item ->
            when (item) {
                is LauncherHomeItem.App -> array.put(
                    JSONObject().put("type", "app").put("package", item.packageName).put("slot", item.slotIndex)
                )
                is LauncherHomeItem.Split -> array.put(
                    JSONObject().put("type", "split").put("id", item.presetId).put("slot", item.slotIndex)
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOME, array.toString())
            .apply()
    }

    fun addApp(context: Context, packageName: String, targetSlot: Int = -1) {
        val items = loadItems(context).toMutableList()
        val occupiedSlots = items.map { it.slotIndex }.toSet()
        val slot = if (targetSlot >= 0 && targetSlot !in occupiedSlots) {
            targetSlot
        } else {
            (0..1000).first { it !in occupiedSlots }
        }
        items.add(LauncherHomeItem.App(packageName, slot))
        saveItems(context, items)
    }

    fun addSplit(context: Context, presetId: String, targetSlot: Int = -1) {
        val items = loadItems(context).toMutableList()
        val occupiedSlots = items.map { it.slotIndex }.toSet()
        val slot = if (targetSlot >= 0 && targetSlot !in occupiedSlots) {
            targetSlot
        } else {
            (0..1000).first { it !in occupiedSlots }
        }
        items.add(LauncherHomeItem.Split(presetId, slot))
        saveItems(context, items)
    }

    fun removeAtSlot(context: Context, slotIndex: Int) {
        val items = loadItems(context).toMutableList()
        val item = items.firstOrNull { it.slotIndex == slotIndex } ?: return
        items.remove(item)
        saveItems(context, items)
        if (autostartKey(context) == item.key) setAutostartKey(context, null)
    }

    fun replaceAtSlot(context: Context, slotIndex: Int, newItem: LauncherHomeItem) {
        val items = loadItems(context).toMutableList()
        val item = items.firstOrNull { it.slotIndex == slotIndex }
        if (item != null) {
            val idx = items.indexOf(item)
            items[idx] = newItem.withSlot(slotIndex)
        } else {
            items.add(newItem.withSlot(slotIndex))
        }
        saveItems(context, items)
    }

    fun moveItem(context: Context, fromSlot: Int, toSlot: Int) {
        val items = loadItems(context).toMutableList()
        val fromItem = items.firstOrNull { it.slotIndex == fromSlot } ?: return
        val targetItem = items.firstOrNull { it.slotIndex == toSlot }

        if (targetItem != null) {
            val idxFrom = items.indexOf(fromItem)
            val idxTarget = items.indexOf(targetItem)
            items[idxFrom] = fromItem.withSlot(toSlot)
            items[idxTarget] = targetItem.withSlot(fromSlot)
        } else {
            val idxFrom = items.indexOf(fromItem)
            items[idxFrom] = fromItem.withSlot(toSlot)
        }
        saveItems(context, items)
    }

    private fun parseItems(raw: String): List<LauncherHomeItem> =
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val slot = if (obj.has("slot")) obj.optInt("slot", i) else i
                    when (obj.optString("type")) {
                        "app" -> obj.optString("package").takeIf { it.isNotBlank() }?.let { add(LauncherHomeItem.App(it, slot)) }
                        "split" -> obj.optString("id").takeIf { it.isNotBlank() }?.let { add(LauncherHomeItem.Split(it, slot)) }
                    }
                }
            }
        }.getOrDefault(emptyList())

    private fun migrateFromGrid(context: Context): List<LauncherHomeItem> =
        LauncherAppConfigStore.gridPackages(context)
            .filter { it.isNotBlank() }
            .mapIndexed { idx, pkg -> LauncherHomeItem.App(pkg, idx) }
            .also { if (it.isNotEmpty()) saveItems(context, it) }
}
