package com.troubadour.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility to read ZIP archives, similar to YTZip.
 * Reads from assets or external storage.
 */
public class ZipUtil {
    private static final String TAG = "ZipUtil";

    /**
     * Get names of all entries in a ZIP archive located in assets.
     *
     * @param assetPath path within assets/ folder (e.g., "skins/ios6.zip")
     * @return list of entry names, or empty list if archive not found or error
     */
    public static List<String> namesInArchive(Context context, String assetPath) {
        AssetManager assets = context.getAssets();
        try {
            InputStream is = assets.open(assetPath);
            // We need to read as ZipFile; ZipFile expects a File, not InputStream.
            // We'll copy the asset to a temporary file.
            // Alternatively, we can use ZipInputStream to enumerate entries.
            // Let's use ZipInputStream for simplicity (does not support random access, but we only need enumeration).
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is);
            List<String> names = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
                zis.closeEntry();
            }
            zis.close();
            is.close();
            return names;
        } catch (IOException e) {
            // Log error
            android.util.Log.e(TAG, "Failed to read ZIP asset: " + assetPath, e);
            return new ArrayList<>();
        }
    }

    /**
     * Get the data for a specific entry in a ZIP archive located in assets.
     *
     * @param assetPath path within assets/ folder
     * @param entryName name of the entry to extract
     * @return byte array of the entry data, or null if not found or error
     */
    public static byte[] dataForEntry(Context context, String assetPath, String entryName) {
        AssetManager assets = context.getAssets();
        try {
            InputStream is = assets.open(assetPath);
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName) && !entry.isDirectory()) {
                    // Read the entry data
                    int len = (int) entry.getSize();
                    if (len <= 0) {
                        // Size unknown, we'll read until end
                        // We'll use a buffer
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[1024];
                        while ((nRead = zis.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        buffer.flush();
                        byte[] result = buffer.toByteArray();
                        zis.closeEntry();
                        zis.close();
                        is.close();
                        return result;
                    } else {
                        byte[] data = new byte[len];
                        int offset = 0;
                        int remaining = len;
                        while (remaining > 0) {
                            int read = zis.read(data, offset, remaining);
                            if (read <= 0) break;
                            offset += read;
                            remaining -= read;
                        }
                        if (remaining > 0) {
                            // Not enough data
                            zis.closeEntry();
                            zis.close();
                            is.close();
                            return null;
                        }
                        zis.closeEntry();
                        zis.close();
                        is.close();
                        return data;
                    }
                }
                zis.closeEntry();
            }
            zis.close();
            is.close();
        } catch (IOException e) {
            android.util.Log.e(TAG, "Failed to read ZIP entry: " + entryName + " from " + assetPath, e);
        }
        return null;
    }

    /**
     * Get names of all entries in a ZIP archive located at a file path.
     * Useful for reading from external storage.
     */
    public static List<String> namesInArchiveFile(String filePath) {
        List<String> names = new ArrayList<>();
        try {
            ZipFile zipFile = new ZipFile(filePath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
            zipFile.close();
        } catch (IOException e) {
            android.util.Log.e(TAG, "Failed to read ZIP file: " + filePath, e);
        }
        return names;
    }

    /**
     * Get the data for a specific entry in a ZIP archive located at a file path.
     */
    public static byte[] dataForEntryFile(String filePath, String entryName) {
        try {
            ZipFile zipFile = new ZipFile(filePath);
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                zipFile.close();
                return null;
            }
            int len = (int) entry.getSize();
            if (len <= 0) {
                // Size unknown, we'll read via input stream
                java.io.InputStream is = zipFile.getInputStream(entry);
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] data = new byte[1024];
                int nRead;
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] result = buffer.toByteArray();
                is.close();
                zipFile.close();
                return result;
            } else {
                byte[] data = new byte[len];
                java.io.InputStream is = zipFile.getInputStream(entry);
                int offset = 0;
                int remaining = len;
                while (remaining > 0) {
                    int read = is.read(data, offset, remaining);
                    if (read <= 0) break;
                    offset += read;
                    remaining -= read;
                }
                if (remaining > 0) {
                    // Not enough data
                    is.close();
                    zipFile.close();
                    return null;
                }
                is.close();
                zipFile.close();
                return data;
            }
        } catch (IOException e) {
            android.util.Log.e(TAG, "Failed to read ZIP entry: " + entryName + " from " + filePath, e);
        }
        return null;
    }
}