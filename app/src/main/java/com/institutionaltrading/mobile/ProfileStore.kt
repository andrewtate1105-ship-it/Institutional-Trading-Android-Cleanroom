package com.institutionaltrading.mobile

import android.content.Context
import java.io.File

class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "operator/profile.json")

    fun save(profile: OperatorProfile) {
        val validated = Validation.profile(profile)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "profile.json.tmp")
        temp.writeText(ProfileCodec.encode(validated))
        require(temp.renameTo(file)) { "Could not atomically save profile" }
    }

    fun load(): OperatorProfile? {
        if (!file.exists()) return null
        return runCatching { ProfileCodec.decode(file.readText()) }.getOrNull()
    }
}
