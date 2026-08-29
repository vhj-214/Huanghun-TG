package xyz.nextalone.nagram.helper

import org.telegram.messenger.NotificationCenter
import tw.nekomimi.nekogram.NekoConfig

/**
 * Coordinates Huanghun's local-only virtual Stars balance.
 *
 * The configured value is deliberately kept local: it is used only by the
 * Huanghun purchase-preview flow and never sends a Telegram payment request.
 * Every mutation emits Telegram's existing balance-update notification so all
 * attached balance views redraw immediately.
 */
object LocalStarsHelper {
    @JvmStatic
    fun getBalance(): Long = NekoConfig.huanghunLocalStars.Long().coerceAtLeast(0L)

    @JvmStatic
    fun canAfford(amount: Long): Boolean = amount >= 0L && getBalance() >= amount

    /**
     * Atomically validates and spends local virtual Stars for the supplied UI
     * account. Returns false without changing the balance when it is too low.
     */
    @JvmStatic
    @Synchronized
    fun spend(currentAccount: Int, amount: Long): Boolean {
        if (!canAfford(amount)) {
            return false
        }
        setBalance(currentAccount, getBalance() - amount)
        return true
    }

    @JvmStatic
    fun setBalance(currentAccount: Int, amount: Long) {
        NekoConfig.huanghunLocalStars.setConfigLong(amount.coerceAtLeast(0L))
        NotificationCenter.getInstance(currentAccount)
            .postNotificationName(NotificationCenter.starBalanceUpdated)
    }
}
