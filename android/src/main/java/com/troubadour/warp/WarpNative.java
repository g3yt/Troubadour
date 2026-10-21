package com.troubadour.warp;

import android.util.Log;

/**
 * JNI interface for native WireGuard/AmneziaWG implementation.
 * This class provides the Java bindings for the native C library
 * that implements WireGuard/AmneziaWG protocol.
 */
public class WarpNative {
    private static final String TAG = "WarpNative";

    static {
        try {
            System.loadLibrary("warp");
            Log.i("WarpNative", "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e("WarpNative", "Failed to load native library: " + e.getMessage());
        }
    }

    // Native methods for WireGuard/AmneziaWG tunnel management
    public static native int warpInit(String endpoint, int port,
                                      String privateKey, String publicKey,
                                      String presharedKey,
                                      String reserved1, String reserved2, String reserved3,
                                      boolean enableObfuscation);

    public static native int warpStart();

    public static native int warpStop();

    public static native int warpSetAllowedIPs(String[] allowedIPs);

    public static native int warpSetDNS(String[] dnsServers);

    public static native int warpSetMTU(int mtu);

    public static native WarpStats getStats();

    public static native String getVersion();

    // Key generation
    public static native String generatePrivateKey();

    public static native String derivePublicKey(String privateKey);

    public static native String generatePresharedKey();

    // Key exchange / handshake
    public static native int warpHandshake();

    // Traffic statistics
    public static native long getBytesSent();

    public static native long getBytesReceived();

    public static native long getPacketsSent();

    public static native long getPacketsReceived();

    // Configuration
    public static native int setEndpoint(String endpoint, int port);

    public static native int setPrivateKey(String privateKey);

    public static native int setPublicKey(String publicKey);

    public static native int setPresharedKey(String presharedKey);

    public static native int setObfuscationEnabled(boolean enabled);

    public static native int setObfuscationParams(String reserved1, String reserved2, String reserved3);

    public static native int setAllowedIPs(String[] allowedIPs);

    public static native int setDNS(String[] dnsServers);

    public static native int setMTU(int mtu);

    public static native int setKeepalive(int seconds);

    // Obfuscation (AmneziaWG specific)
    public static native int setObfuscationEnabled(boolean enabled);

    public static native int setObfuscationParams(String jc, String jmin, String jmax,
                                                  String s1, String s2, String s3, String s4,
                                                  String h1, String h2, String h3, String h4,
                                                  String i1, String i2, String i3, String i4, String i5);

    // Traffic control
    public static native int setAllowedApps(String[] packageNames);

    public static native int setExcludedApps(String[] packageNames);

    // Status
    public static native int getState(); // 0=stopped, 1=starting, 2=connected, 3=error

    public static native String getLastError();

    // Crypto operations (for key generation, etc.)
    public static native byte[] cryptoCurve25519(byte[] privateKey, byte[] basePoint);

    public static native byte[] cryptoChaCha20Poly1305Encrypt(byte[] key, byte[] nonce,
                                                              byte[] plaintext, byte[] aad);

    public static native byte[] cryptoChaCha20Poly1305Decrypt(byte[] key, byte[] nonce,
                                                              byte[] ciphertext, byte[] aad);

    public static native byte[] cryptoBlake2s(byte[] input, byte[] key, int outLen);

    // Tunnel interface
    public static native int createTunInterface(String name, int mtu);

    public static native int writeTunPacket(byte[] packet, int offset, int length);

    public static native int readTunPacket(byte[] buffer, int offset, int length);

    public static native void closeTunInterface(int fd);

    // Packet processing (called from native when packets arrive)
    public static void onPacketReceived(byte[] packet, int offset, int length) {
        // Called from native code when a decrypted packet is received
        // This would be implemented to forward packets to the TUN interface
    }

    public static void onHandshakeComplete(boolean success) {
        // Called from native when handshake completes
    }

    public static void onStateChanged(int state) {
        // Called from native when state changes
    }

    public static void onError(int errorCode, String message) {
        // Called from native when error occurs
    }
}