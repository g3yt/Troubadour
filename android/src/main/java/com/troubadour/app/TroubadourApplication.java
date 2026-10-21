package com.troubadour.app;

import android.app.Application;
import com.troubadour.auth.YTAuth;
import com.troubadour.warp.WarpManager;
import com.troubadour.utils.Logger;
import com.troubadour.utils.StringsUtils;

/**
 * Application class for Troubadour.
 */
public class TroubadourApplication extends Application {
    private static SabrProxyServer sabrProxyServer;
    private static WarpManager warpManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize utilities
        Logger.init(this);
        StringsUtils.init(this);
        YTAuth.init(this);

        // Initialize and start SABR proxy server
        sabrProxyServer = new SabrProxyServer(this);
        sabrProxyServer.start();

        // Initialize WARP manager
        warpManager = new WarpManager(this);
        warpManager.init();

        // Log that the app has started
        Logger.i("Troubadour application started");
    }

    /**
     * Gets the SABR proxy server instance.
     */
    public static SabrProxyServer getSabrProxyServer() {
        return sabrProxyServer;
    }

    /**
     * Gets the WARP manager instance.
     */
    public static WarpManager getWarpManager() {
        return warpManager;
    }
}