package xyz.nextalone.nagram.helper

import androidx.core.content.edit
import org.telegram.messenger.UserConfig
import xyz.nextalone.nagram.NaConfig

/** Local-only profile overrides. Values are isolated by Telegram account and target user id. */
object LocalProfileOverrideHelper {
    private const val BIO_PREFIX = "localProfileBio_"
    private const val AVATAR_PREFIX = "localProfileAvatar_"

    @JvmStatic
    fun getBio(userId: Long, account: Int = UserConfig.selectedAccount): String? {
        if (userId == 0L) return null
        return NaConfig.getPreferences().getString(BIO_PREFIX + account + "_" + userId, null)
    }

    @JvmStatic
    fun setBio(userId: Long, bio: String?, account: Int = UserConfig.selectedAccount) {
        if (userId == 0L) return
        NaConfig.getPreferences().edit {
            if (bio.isNullOrBlank()) remove(BIO_PREFIX + account + "_" + userId)
            else putString(BIO_PREFIX + account + "_" + userId, bio.trim())
        }
    }

    @JvmStatic
    fun getAvatarUri(userId: Long, account: Int = UserConfig.selectedAccount): String? {
        if (userId == 0L) return null
        val value = NaConfig.getPreferences().getString(AVATAR_PREFIX + account + "_" + userId, null)
        return value?.takeIf { java.io.File(it).isFile }
    }

    @JvmStatic
    fun setAvatarUri(userId: Long, uri: String?, account: Int = UserConfig.selectedAccount) {
        if (userId == 0L) return
        NaConfig.getPreferences().edit {
            if (uri.isNullOrBlank()) remove(AVATAR_PREFIX + account + "_" + userId)
            else putString(AVATAR_PREFIX + account + "_" + userId, uri)
        }
    }
}
