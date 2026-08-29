package xyz.nextalone.nagram.helper

import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.MessagesController
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
 * Stores visual-only collectible gifts selected from the "Apply Style" page for the current account.
 * This data never writes Telegram server emoji status, gift pinning, ownership, or payment data.
 */
object LocalProfileGiftHelper {
    const val KEY_PREFIX = "localProfileGift_"

    private val gson = Gson()
    private val listType = object : TypeToken<ArrayList<LocalProfileGiftData>>() {}.type
    private val dataMap = mutableMapOf<Long, ArrayList<LocalProfileGiftData>>()
    private val loadedUsers = mutableSetOf<Long>()

    /**
     * Compatibility accessor for callers that only need a single visible collectible.
     */
    @JvmStatic
    fun getMountedGift(user: TLRPC.User?): TLRPC.TL_emojiStatusCollectible? {
        return getMountedGifts(user).firstOrNull()
    }

    /**
     * Returns every locally mounted style collectible for the current local account only.
     */
    @JvmStatic
    fun getMountedGifts(user: TLRPC.User?): ArrayList<TLRPC.TL_emojiStatusCollectible> {
        if (!NekoConfig.localPremium.Bool() || user == null || !isLocalUser(user.id)) return arrayListOf()
        return ArrayList(getDataForUser(user.id).map(::toStatus))
    }

    /**
     * Exposes visual metadata for the local section of the profile gifts page.
     */
    @JvmStatic
    fun getMountedGiftData(user: TLRPC.User?): ArrayList<LocalProfileGiftData> {
        if (!NekoConfig.localPremium.Bool() || user == null || !isLocalUser(user.id)) return arrayListOf()
        return ArrayList(getDataForUser(user.id))
    }

    @JvmStatic
    fun isMountedGift(userId: Long, collectibleId: Long): Boolean {
        if (!NekoConfig.localPremium.Bool() || !isLocalUser(userId)) return false
        return getDataForUser(userId).any { it.collectibleId == collectibleId }
    }

    /**
     * Retains the previous single-gift API for explicit replacements. A null status never clears data.
     */
    @JvmStatic
    fun apply(status: TLRPC.TL_emojiStatusCollectible?, gift: TL_stars.TL_starGiftUnique?) {
        if (status == null) return
        applyData(arrayListOf(toData(status, gift)))
    }

    /**
     * Saves all collectible gifts loaded by the profile "Apply Style" page as a local mounted set.
     * Ordinary SavedStarGift cards are intentionally not accepted by this API.
     */
    @JvmStatic
    fun applyAll(gifts: List<TL_stars.TL_starGiftUnique>?) {
        if (gifts.isNullOrEmpty()) return
        val seen = hashSetOf<Long>()
        val mounted = arrayListOf<LocalProfileGiftData>()
        for (gift in gifts) {
            if (!seen.add(gift.id)) continue
            val status = MessagesController.emojiStatusCollectibleFromGift(gift) ?: continue
            mounted.add(toData(status, gift))
        }
        if (mounted.isNotEmpty()) {
            applyData(mounted)
        }
    }

    @JvmStatic
    fun remove(userId: Long, collectibleId: Long) {
        if (userId == 0L || !isLocalUser(userId)) return
        val current = ArrayList(getDataForUser(userId))
        if (current.removeAll { it.collectibleId == collectibleId }) {
            saveData(userId, current)
        }
    }

    /** Returns a compact local-only summary for the current account's purchased virtual gifts. */
    @JvmStatic
    fun getCurrentMountedGiftSummary(): String {
        val userId = currentUserId()
        if (userId == 0L) return "当前 0 个"
        val data = getDataForUser(userId)
        if (data.isEmpty()) return "当前 0 个"
        val titles = data.mapNotNull { it.title.takeIf(String::isNotBlank) }.take(3)
        val suffix = if (data.size > titles.size) " 等" else ""
        return "当前 ${data.size} 个：" + titles.joinToString("、") + suffix
    }

    /** Explicit removal is the only operation that clears all local style gifts. */
    @JvmStatic
    fun clear(userId: Long = currentUserId()) {
        if (userId == 0L || !isLocalUser(userId)) return
        dataMap[userId] = arrayListOf()
        loadedUsers.add(userId)
        NaConfig.getPreferences().edit { remove(KEY_PREFIX + userId) }
    }

    private fun applyData(data: ArrayList<LocalProfileGiftData>) {
        val userId = currentUserId()
        if (!NekoConfig.localPremium.Bool() || userId == 0L || data.isEmpty()) return
        saveData(userId, data)
    }

    private fun saveData(userId: Long, data: ArrayList<LocalProfileGiftData>) {
        dataMap[userId] = ArrayList(data)
        loadedUsers.add(userId)
        if (data.isEmpty()) {
            NaConfig.getPreferences().edit { remove(KEY_PREFIX + userId) }
        } else {
            NaConfig.getPreferences().edit { putString(KEY_PREFIX + userId, gson.toJson(data)) }
        }
    }

    private fun toData(status: TLRPC.TL_emojiStatusCollectible, gift: TL_stars.TL_starGiftUnique?): LocalProfileGiftData {
        return LocalProfileGiftData(
            status.collectible_id,
            status.document_id,
            status.title ?: gift?.title ?: "",
            gift?.slug ?: status.slug ?: "",
            status.pattern_document_id,
            status.center_color,
            status.edge_color,
            status.pattern_color,
            status.text_color
        )
    }

    private fun toStatus(data: LocalProfileGiftData): TLRPC.TL_emojiStatusCollectible {
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

    private fun getDataForUser(userId: Long): ArrayList<LocalProfileGiftData> {
        if (userId == 0L) return arrayListOf()
        initForUser(userId)
        return dataMap[userId] ?: arrayListOf()
    }

    private fun initForUser(userId: Long) {
        if (loadedUsers.contains(userId)) return
        loadedUsers.add(userId)
        val raw = NaConfig.getPreferences().getString(KEY_PREFIX + userId, null)
        dataMap[userId] = try {
            if (raw.isNullOrEmpty()) {
                arrayListOf()
            } else {
                gson.fromJson<ArrayList<LocalProfileGiftData>>(raw, listType)
                    ?.let(::ArrayList)
                    ?: arrayListOf()
            }
        } catch (_: Exception) {
            // Migration from the prior single-object format.
            try {
                gson.fromJson(raw, LocalProfileGiftData::class.java)?.let { arrayListOf(it) } ?: arrayListOf()
            } catch (_: Exception) {
                arrayListOf()
            }
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
