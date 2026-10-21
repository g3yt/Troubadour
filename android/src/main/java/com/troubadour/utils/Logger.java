package com.troubadour.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Loformat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Logger that writes to both Logcat and a file, similar to YTLog.
 */
public class Logger {
    private static final String TAG = "Troubadour";
    private static final boolean LOG_ENABLED = true; // Set to false for release builds
    private static final long LOG_LIMIT = 512 * 1024; // 512 KB

    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static Context sContext;

    /**
     * Initialize the logger with application context.
     * Should be called from Application.onCreate().
     */
    public static void init(Context context) {
        sContext = context.getApplicationContext();
    }

    /**
     * Log a verbose message.
     */
    public static void v(String message) {
        if (!LOG_ENABLED) return;
        Log.v(TAG, message);
        writeToFile(message);
    }

    /**
     * Log a debug message.
     */
    public static void d(String message) {
        if (!LOG_ENABLED) return;
        Log.d(TAG, message);
        writeToFile(message);
    }

    /**
     * Log an info message.
     */
    public static void i(String message) {
        if (!LOG_ENABLED) return;
        Log.i(TAG, message);
        writeToFile(message);
    }

    /**
     * Log a warning message.
     */
    public static void w(String message) {
        if (!LOG_ENABLED) return;
        Log.w(TAG, message);
        writeToFile(message);
    }

    /**
     * Log an error message.
     */
    public static void e(String message) {
        if (!LOG_ENABLED) return;
        Log.e(TAG, message);
        writeToFile(message);
    }

    /**
     * Log a message with a throwable.
     */
    public static void e(String message, Throwable tr) {
        if (!LOG_ENABLED) return;
        Log.e(TAG, message, tr);
        writeToFile(message + "\n" + Log.getStackTraceString(tr));
    }

    /**
     * Clear the log file.
     */
    public static void clear() {
        if (sContext == null) return;
        LOG_EXECUTOR.execute(() -> {
            File logFile = new File(sContext.getFilesDir(), "youtube.log");
            if (logFile.exists()) {
                logFile.delete();
            }
            File prevFile = new File(sContext.getFilesDir(), "youtube-prev.log");
            if (prevFile.exists()) {
                prevFile.delete();
            }
        });
    }

    /**
     * Get the path to the log file.
     */
    public static String getLogPath() {
        if (sContext == null) return "";
        return new File(sContext.getFilesDir(), "youtube.log").getAbsolutePath();
    }

    private static void writeToFile(final String message) {
        if (sContext == null) return;
        LOG_EXECUTOR.execute(() -> {
            try {
                File logFile = new File(sContext.getFilesDir(), "youtube.log");
                if (!logFile.exists()) {
                    logFile.createNewFile();
                }

                // Check size and rotate if needed
                if (logFile.length() > LOG_LIMIT) {
                    File prevFile = new File(sContext.getFilesDir(), "youtube-prev.log");
                    if (prevFile.exists()) {
                        prevFile.delete();
                    }
                    logFile.renameTo(prevFile);
                    logFile.createNewFile();
                }

                String line = String.format(Locale.US, "%s %s%n",
                        TIME_FORMAT.format(new Date()), message);
                FileWriter writer = new FileWriter(logFile, true);
                writer.append(line);
                writer.close();
            } catch (IOException e) {
                // Fallback to Logcat only
                Log.e(TAG, "Failed to write to log file", e);
            }
        });
    }
}