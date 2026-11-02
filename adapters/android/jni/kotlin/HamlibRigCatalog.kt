package com.js8call.core

/**
 * Provides Hamlib rig model metadata via JNI for settings UI.
 */
object HamlibRigCatalog {
    init {
        System.loadLibrary("js8core-jni")
    }

    /** [network]: the rig is reached over the network (PC Capabilities::network); [catPtt], [micData]: PC has_CAT_PTT, has_CAT_PTT_mic_data. */
    data class RigModel(val id: String, val label: String, val network: Boolean = false,
                        val catPtt: Boolean = false, val micData: Boolean = false)

    private val models by lazy { listRigModels().associateBy { it.id } }

    fun model(id: String): RigModel? = models[id]

    fun listRigModels(): List<RigModel> {
        val raw = nativeListRigModels()
        if (raw.isEmpty()) return emptyList()

        val models = ArrayList<RigModel>(raw.size)
        for (entry in raw) {
            val parts = entry.split('|', limit = 3)
            if (parts.size < 2) continue
            val id = parts[0].trim()
            val label = parts.last().trim()
            if (id.isEmpty() || label.isEmpty()) continue
            val caps = if (parts.size == 3) parts[1] else ""
            models.add(RigModel(id, label, 'n' in caps, 'p' in caps, 'm' in caps))
        }
        return models
    }

    private external fun nativeListRigModels(): Array<String>
}
