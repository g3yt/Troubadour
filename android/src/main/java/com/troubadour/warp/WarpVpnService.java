package com.troubadour.warp;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.troubadour.utils.Logger;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WARP VPN Service.
 * This service runs in the background and manages the WARP tunnel.
 * It creates a TUN interface and forwards packets to/from the native WireGuard implementation.
 */
public class WarpVpnService extends VpnService {
    private static final String TAG = "WarpVpnService";
    private static final int MTU = 1280;
    private static final int PACKET_BUFFER_SIZE = 32768;

    private ParcelFileDescriptor vpnInterface = null;
    private FileDescriptor vpnFileDescriptor = null;
    private FileInputStream vpnInputStream = null;
    private FileOutputStream vpnOutputStream = null;
    private FileChannel vpnChannel = null;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler();
    private boolean isRunning = false;
    private WarpManager warpManager;
    private boolean isPrepared = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("WarpVpnService", "WARP VPN Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isPrepared) {
            prepareVpn();
        }
        return START_STICKY;
    }

    private void prepareVpn() {
        try {
            Builder builder = new Builder();
            builder.setSession("Troubadour WARP")
                   .addAddress("10.0.0.2", 32)
                   .addRoute("0.0.0.0", 0)  // Route all IPv4 traffic
                   .addRoute("::", 0)        // Route all IPv6 traffic
                   .addDnsServer("1.1.1.1")
                   .addDnsServer("1.0.0.1")
                   .setMtu(1280)
                   .setBlocking(true);

            // Allow all traffic by default - selective routing handled by native layer
            // In a real implementation, we'd add specific routes for YouTube/Google

            vpnInterface = establish(builder);
            if (vpnInterface != null) {
                vpnFileDescriptor = vpnInterface.getFileDescriptor();
                vpnInputStream = new FileInputStream(vpnFileDescriptor);
                vpnOutputStream = new FileOutputStream(vpnFileDescriptor);
                vpnChannel = vpnInputStream.getChannel();
                isPrepared = true;
                Logger.i("WARP VPN interface prepared successfully");
            }
        } catch (Exception e) {
            Log.e("WarpVpnService", "Failed to prepare VPN: " + e.getMessage());
        }
    }

    private Builder vpnBuilder = new Builder();

    /**
     * Start the VPN tunnel with the given configuration.
     */
    public void startVpn(WarpManager.WarpConfig config) {
        if (!isPrepared) {
            prepareVpn();
        }

        // Start packet processing loops
        executor.submit(this::readFromVpnLoop);
        executor.submit(this::writeToVpnLoop);

        Log.i("WarpVpnService", "VPN tunnel started");
    }

    /**
     * Read packets from the VPN interface and send to native WireGuard for encryption.
     */
    private void readFromVpnLoop() {
        ByteBuffer packetBuffer = ByteBuffer.allocateDirect(PACKET_BUFFER_SIZE);
        while (true) {
            try {
                int length = vpnInputStream.read(packetBuffer.array());
                if (length > 0) {
                    // Send packet to native WireGuard for encryption and transmission
                    // WarpNative.writeTunPacket(packetBuffer.array(), 0, length);
                    // For now, just log
                    Log.v("WarpVpnService", "Read " + length + " bytes from VPN");
                }
            } catch (IOException e) {
                Log.e("WarpVpnService", "Error reading from VPN: " + e.getMessage());
                break;
            }
        }
    }

    /**
     * Read packets from native WireGuard and write to VPN interface.
     */
    private void writeToVpnLoop() {
        // This would be called from native code when decrypted packets are available
        // For now, this is a placeholder
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Write a decrypted packet to the VPN interface.
     * Called from native code when a packet is decrypted.
     */
    public void writePacketToVpn(byte[] packet, int offset, int length) {
        try {
            if (vpnOutputStream != null) {
                vpnOutputStream.write(packet, offset, length);
                vpnOutputStream.flush();
            }
        } catch (IOException e) {
            Log.e("WarpVpnService", "Error writing to VPN: " + e.getMessage());
        }
    }

    /**
     * Read a packet from the VPN interface for encryption.
     * Called from native code when it needs to send a packet.
     */
    public int readPacketFromVpn(byte[] buffer, int offset, int length) throws IOException {
        if (vpnInputStream != null) {
            return vpnInputStream.read(buffer, offset, length);
        }
        return -1;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (vpnInputStream != null) vpnInputStream.close();
            if (vpnOutputStream != null) vpnOutputStream.close();
            if (vpnInterface != null) vpnInterface.close();
        } catch (IOException e) {
            Log.e("WarpVpnService", "Error closing VPN: " + e.getMessage());
        }
        executor.shutdownNow();
        Log.i("WarpVpnService", "WARP VPN Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}