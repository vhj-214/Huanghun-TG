package xyz.nextalone.nagram.helper

import org.telegram.messenger.NotificationCenter
import tw.nekomimi.nekogram.NekoConfig

/**
 * Local-only Gram balance used by the gift preview/purchase flow.
 * It never sends a Telegram payment request or changes server-side TON balance.
 */
object LocalGramHelper {
    @JvmStatic
    fun getBalance(): Long {
        if (!NekoConfig.huanghunLocalGramInitialized.Bool()) {
            if (NekoConfig.huanghunLocalGram.Long() <= 0L) {
                NekoConfig.huanghunLocalGram.setConfigLong(9999L)
            }
            NekoConfig.huanghunLocalGramInitialized.setConfigBool(true)
        }
        return NekoConfig.huanghunLocalGram.Long().coerceAtLeast(0L)
    }

    @JvmStatic
    fun canAfford(amount: Long): Boolean = amount >= 0L && getBalance() >= amount

    @JvmStatic
    @Synchronized
    fun spend(currentAccount: Int, amount: Long): Boolean {
        if (!canAfford(amount)) return false
        setBalance(currentAccount, getBalance() - amount)
        return true
    }

    @JvmStatic
    fun setBalance(currentAccount: Int, amount: Long) {
        NekoConfig.huanghunLocalGram.setConfigLong(amount.coerceAtLeast(0L))
        NotificationCenter.getInstance(currentAccount)
            .postNotificationName(NotificationCenter.starBalanceUpdated)
    }
}
