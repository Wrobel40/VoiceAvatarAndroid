package com.aiavatar.app.avatar

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Manages custom GLB avatar skins:
 * - Saves imported GLB files to app's private storage
 * - Persists metadata (name, path) to JSON
 * - Provides list of all saved custom skins
 */
data class CustomSkin(
    val id: String,
    val name: String,
    val filePath: String,  // absolute path in app cache
    val addedAt: Long = System.currentTimeMillis()
)

class AvatarManager(private val context: Context) {
    private val gson = Gson()
    private val metaFile = File(context.filesDir, "custom_skins.json")
    private val skinsDir = File(context.filesDir, "skins").also { it.mkdirs() }

    fun loadCustomSkins(): List<CustomSkin> {
        if (!metaFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<CustomSkin>>() {}.type
            gson.fromJson(metaFile.readText(), type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun importGlb(uri: Uri, displayName: String): CustomSkin? {
        return try {
            val id = "skin_${System.currentTimeMillis()}"
            val destFile = File(skinsDir, "$id.glb")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            val skin = CustomSkin(id = id, name = displayName.removeSuffix(".glb"), filePath = destFile.absolutePath)
            val skins = loadCustomSkins().toMutableList()
            skins.add(skin)
            saveMeta(skins)
            skin
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    fun deleteSkin(id: String) {
        val skins = loadCustomSkins().toMutableList()
        val skin = skins.find { it.id == id } ?: return
        File(skin.filePath).delete()
        skins.removeAll { it.id == id }
        saveMeta(skins)
    }

    private fun saveMeta(skins: List<CustomSkin>) {
        metaFile.writeText(gson.toJson(skins))
    }
}
