package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProxyRotationController implements NotificationCenter.NotificationCenterDelegate {
    private final static ProxyRotationController INSTANCE = new ProxyRotationController();

    public final static int DEFAULT_TIMEOUT_INDEX = 1;
    public final static List<Integer> ROTATION_TIMEOUTS = Arrays.asList(
            5, 10, 15, 30, 60
    );

    /** Automatic proxy health/list probing is deliberately limited to once per minute. */
    private static final long PROXY_HEALTH_CHECK_INTERVAL = 60_000L;
    private static final long ACTIVE_PROXY_HEALTH_CHECK_INTERVAL = PROXY_HEALTH_CHECK_INTERVAL;

    private boolean isCurrentlyChecking;
    private boolean isActiveProxyChecking;
    private final Runnable activeProxyHealthCheckRunnable = this::checkActiveProxyHealth;
    private final Runnable rotationPingRefreshRunnable = this::refreshRotationProxyPings;
    private Runnable checkProxyAndSwitchRunnable = () -> {
        isCurrentlyChecking = true;

        int currentAccount = UserConfig.selectedAccount;
        boolean startedCheck = false;
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                continue;
            }
            startedCheck = true;
            proxyInfo.checking = true;
            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
            }));
        }

        if (!startedCheck) {
            isCurrentlyChecking = false;
            switchToAvailable();
        }
    };

    public static void init() {
        INSTANCE.initInternal();
    }

    /**
     * Called after the rotation switch or its interval changes in the settings UI.
     * The selected interval is a real Ping refresh cadence, not merely a connection-timeout value.
     */
    public static void onRotationSettingsChanged() {
        INSTANCE.scheduleRotationPingRefresh(0L);
    }

    private long getRotationPingRefreshInterval() {
        // ROTATION_TIMEOUTS controls connection-failure switching, not health polling.
        // Do not let its 5-second default cause continuous proxy probes in the background.
        return PROXY_HEALTH_CHECK_INTERVAL;
    }

    private boolean shouldRefreshRotationPings() {
        return SharedConfig.isProxyEnabled() && SharedConfig.proxyRotationEnabled && SharedConfig.proxyList.size() > 1;
    }

    private void scheduleRotationPingRefresh(long delay) {
        AndroidUtilities.cancelRunOnUIThread(rotationPingRefreshRunnable);
        if (shouldRefreshRotationPings()) {
            AndroidUtilities.runOnUIThread(rotationPingRefreshRunnable, delay);
        }
    }

    /**
     * Re-tests every configured proxy at the user-selected interval. This is intentionally
     * separate from the connection-failure path below: periodic Ping refresh must not switch
     * a healthy connection merely because another proxy reports a lower latency.
     */
    private void refreshRotationProxyPings() {
        if (!shouldRefreshRotationPings()) {
            return;
        }
        final int account = UserConfig.selectedAccount;
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            final SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking) {
                continue;
            }
            proxyInfo.checking = true;
            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(account).checkProxy(
                    proxyInfo.address,
                    proxyInfo.port,
                    proxyInfo.username,
                    proxyInfo.password,
                    proxyInfo.secret,
                    time -> AndroidUtilities.runOnUIThread(() -> {
                        proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                        proxyInfo.checking = false;
                        proxyInfo.available = time != -1;
                        proxyInfo.ping = time == -1 ? 0 : time;
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
                    })
            );
        }
        scheduleRotationPingRefresh(getRotationPingRefreshInterval());
    }

    /**
     * Checks the currently enabled built-in proxy even when rotation is disabled.
     * A failed probe reapplies the same proxy settings, which asks tgnet to establish
     * a fresh transport instead of leaving background notification connections stalled.
     */
    private void checkActiveProxyHealth() {
        if (!SharedConfig.isProxyEnabled() || SharedConfig.currentProxy == null || isActiveProxyChecking) {
            return;
        }
        final SharedConfig.ProxyInfo proxyInfo = SharedConfig.currentProxy;
        if (proxyInfo.checking) {
            scheduleActiveProxyHealthCheck(ACTIVE_PROXY_HEALTH_CHECK_INTERVAL);
            return;
        }
        isActiveProxyChecking = true;
        final int account = UserConfig.selectedAccount;
        proxyInfo.checking = true;
        proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(account).checkProxy(
                proxyInfo.address,
                proxyInfo.port,
                proxyInfo.username,
                proxyInfo.password,
                proxyInfo.secret,
                time -> AndroidUtilities.runOnUIThread(() -> {
                    proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                    proxyInfo.checking = false;
                    isActiveProxyChecking = false;
                    proxyInfo.available = time != -1;
                    proxyInfo.ping = time == -1 ? 0 : time;
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
                    if (time == -1 && SharedConfig.isProxyEnabled() && SharedConfig.currentProxy == proxyInfo) {
                        ConnectionsManager.setProxySettings(true, proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
                    }
                    scheduleActiveProxyHealthCheck(ACTIVE_PROXY_HEALTH_CHECK_INTERVAL);
                })
        );
    }

    private void scheduleActiveProxyHealthCheck(long delay) {
        AndroidUtilities.cancelRunOnUIThread(activeProxyHealthCheckRunnable);
        if (SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null) {
            AndroidUtilities.runOnUIThread(activeProxyHealthCheckRunnable, delay);
        }
    }

    @SuppressWarnings("ComparatorCombinators")
    private void switchToAvailable() {
        isCurrentlyChecking = false;

        if (!SharedConfig.proxyRotationEnabled) {
            return;
        }

        List<SharedConfig.ProxyInfo> sortedList = new ArrayList<>(SharedConfig.proxyList);
        Collections.sort(sortedList, (o1, o2) -> Long.compare(o1.ping, o2.ping));
        for (SharedConfig.ProxyInfo info : sortedList) {
            if (info == SharedConfig.currentProxy || info.checking || !info.available) {
                continue;
            }

            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putString("proxy_ip", info.address);
            editor.putString("proxy_pass", info.password);
            editor.putString("proxy_user", info.username);
            editor.putInt("proxy_port", info.port);
            editor.putString("proxy_secret", info.secret);
            editor.putBoolean("proxy_enabled", true);

            if (!info.secret.isEmpty()) {
                editor.putBoolean("proxy_enabled_calls", false);
            }
            editor.apply();

            SharedConfig.currentProxy = info;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
            ConnectionsManager.setProxySettings(true, SharedConfig.currentProxy.address, SharedConfig.currentProxy.port, SharedConfig.currentProxy.username, SharedConfig.currentProxy.password, SharedConfig.currentProxy.secret);
            break;
        }
    }

    private void initInternal() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        scheduleActiveProxyHealthCheck(ACTIVE_PROXY_HEALTH_CHECK_INTERVAL);
        scheduleRotationPingRefresh(0L);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyCheckDone) {
            if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyRotationEnabled || SharedConfig.proxyList.size() <= 1 || !isCurrentlyChecking) {
                return;
            }

            switchToAvailable();
        } else if (id == NotificationCenter.proxySettingsChanged) {
            AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
            scheduleActiveProxyHealthCheck(ACTIVE_PROXY_HEALTH_CHECK_INTERVAL);
            scheduleRotationPingRefresh(getRotationPingRefreshInterval());
        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (SharedConfig.isProxyEnabled()) {
                int activeState = ConnectionsManager.getInstance(account).getConnectionState();
                long delay = activeState == ConnectionsManager.ConnectionStateConnectingToProxy ? 15_000L : ACTIVE_PROXY_HEALTH_CHECK_INTERVAL;
                scheduleActiveProxyHealthCheck(delay);
            }

            if (!SharedConfig.isProxyEnabled() && !SharedConfig.proxyRotationEnabled || SharedConfig.proxyList.size() <= 1) {
                return;
            }

            int state = ConnectionsManager.getInstance(account).getConnectionState();

            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                if (!isCurrentlyChecking) {
                    AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, ROTATION_TIMEOUTS.get(SharedConfig.proxyRotationTimeout) * 1000L);
                }
            } else {
                AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
            }
        }
    }
}
