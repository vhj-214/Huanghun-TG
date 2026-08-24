package xyz.nextalone.nagram.helper

import androidx.core.content.edit
import com.google.gson.Gson
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_stars
import tw.nekomimi.nekogram.NekoConfig
import xyz.nextalone.nagram.NaConfig

data class LocalProfileGiftData(
    var collectibleId: Long,
    var documentId: Long,
    var title: String,
    var slug: String,
    var patternDocumentId: Long,
    var centerColor: Int,
    var edgeColor: Int,
    var patternColor: Int,
    var textColor: Int
)

/**
 * Stores a locally mounted collectible style for the current account only.
 * The data is deliberately visual-only: it never sends or changes the account's
 * server-side emoji status, gift pinning state, or ownership data.
 */
object LocalProfileGiftHelper {
    const val KEY_PREFIX = "localProfileGift_"

    private val dataMap = mutableMapOf<Long, LocalProfileGiftData?>()
    private val loadedUsers = mutableSetOf<Long>()

    @JvmStatic
    fun getMountedGift(user: TLRPC.User?): TLRPC.TL_emojiStatusCollectible? {
        if (!NekoConfig.localPremium.Bool() || user == null || !isLocalUser(user.id)) return null
        val data = getDataForUser(user.id) ?: return null
        return TLRPC.TL_emojiStatusCollectible().apply {
            collectible_id = data.collectibleId
            document_id = data.documentId
            title = data.title
            slug = data.slug
            pattern_document_id = data.patternDocumentId
            center_color = data.centerColor
            edge_color = data.edgeColor
            pattern_color = data.patternColor
            text_color = data.textColor
        }
    }

    @JvmStatic
    fun isMountedGift(userId: Long, collectibleId: Long): Boolean {
        if (!NekoConfig.localPremium.Bool() || !isLocalUser(userId)) return false
        return getDataForUser(userId)?.collectibleId == collectibleId
    }

    @JvmStatic
    fun apply(status: TLRPC.TL_emojiStatusCollectible?, gift: TL_stars.TL_starGiftUnique?) {
        val userId = currentUserId()
        if (!NekoConfig.localPremium.Bool() || userId == 0L) return
        if (status == null) {
            clear(userId)
            return
        }
        val data = LocalProfileGiftData(
            status.collectible_id,
            status.document_id,
            status.title ?: "",
            gift?.slug ?: status.slug ?: "",
            status.pattern_document_id,
            status.center_color,
            status.edge_color,
            status.pattern_color,
            status.text_color
        )
        dataMap[userId] = data
        loadedUsers.add(userId)
        NaConfig.getPreferences().edit { putString(KEY_PREFIX + userId, Gson().toJson(data)) }
    }

    @JvmStatic
    fun clear(userId: Long = currentUserId()) {
        if (userId == 0L || !isLocalUser(userId)) return
        dataMap[userId] = null
        loadedUsers.add(userId)
        NaConfig.getPreferences().edit { remove(KEY_PREFIX + userId) }
    }

    private fun getDataForUser(userId: Long): LocalProfileGiftData? {
        if (userId == 0L) return null
        initForUser(userId)
        return dataMap[userId]
    }

    private fun initForUser(userId: Long) {
        if (loadedUsers.contains(userId)) return
        loadedUsers.add(userId)
        dataMap[userId] = try {
            NaConfig.getPreferences().getString(KEY_PREFIX + userId, null)
                ?.takeIf { it.isNotEmpty() }
                ?.let { Gson().fromJson(it, LocalProfileGiftData::class.java) }
        } catch (_: Exception) {
            null
        }
    }

    private fun currentUserId(): Long = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId

    private fun isLocalUser(userId: Long): Boolean {
        for (account in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            val config = UserConfig.getInstance(account)
            if (config.isClientActivated && config.clientUserId == userId) return true
        }
        return false
    }
}
