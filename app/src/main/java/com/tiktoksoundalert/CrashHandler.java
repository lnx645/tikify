package com.tiktoksoundalert;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.tiktoksoundalert.ui.ReportBugActivity;

/**
 * Global uncaught-exception handler. On any crash it captures the stack trace,
 * persists it, opens the Report Bug screen (which lives in its own process so
 * it survives after the crashed process is killed) and then terminates the
 * crashed process.
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    public static final String EXTRA_CRASH_REPORT = "crash_report";
    private static final String TAG = "CrashHandler";
    private static final String CRASH_FILE = "last_crash_report.txt";

    private final Application app;

    /** Guards against a crash loop inside the handler itself. */
    private static volatile boolean handling = false;

    private CrashHandler(Application app) {
        this.app = app;
    }

    public static void install(Application app) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(app));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        if (handleCrash()) {
            return;
        }
        String report = buildReport(thread, throwable);
        Log.e(TAG, "Uncaught exception on " + (thread != null ? thread.getName() : "?") + ": " + throwable);
        persist(report);
        try {
            Intent intent = new Intent(app, ReportBugActivity.class);
            intent.putExtra(EXTRA_CRASH_REPORT, report);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            app.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open report screen", e);
        }
        // ReportBugActivity runs in a separate process; give the launch IPC a
        // moment, then end this (crashed) process.
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            Process.killProcess(Process.myPid());
            System.exit(11);
        }, "CrashExit").start();
    }

    /** Returns true when a previous crash is still being handled (avoid a loop). */
    private static synchronized boolean handleCrash() {
        if (handling) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            Process.killProcess(Process.myPid());
            System.exit(10);
            return true;
        }
        handling = true;
        return false;
    }

    private static String buildReport(Thread thread, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("LAPORAN ERROR BARU (dikirim otomatis)\n");
        sb.append("=====================================\n\n");
        sb.append("Aplikasi: ").append(BuildConfig.APPLICATION_ID).append('\n');
        sb.append("Versi: ").append(BuildConfig.VERSION_NAME)
                .append(" (build ").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("Waktu: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date())).append('\n');
        sb.append("Perangkat: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ")
                .append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Thread: ").append(thread != null ? thread.getName() : "?").append('\n');
        sb.append("\n--- Stack Trace ---\n");
        sb.append(Log.getStackTraceString(throwable));
        sb.append('\n');
        return sb.toString();
    }

    private void persist(String report) {
        try {
            File f = new File(app.getFilesDir(), CRASH_FILE);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(report.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist crash report", e);
        }
    }

    /** Reads the last saved crash report (null when none yet). */
    public static String loadLastReport(Context context) {
        File f = new File(context.getFilesDir(), CRASH_FILE);
        if (!f.exists()) {
            return null;
        }
        try {
            byte[] bytes = new byte[(int) Math.min(f.length(), 1 << 20)];
            try (FileInputStream fis = new FileInputStream(f)) {
                int off = 0;
                while (off < bytes.length) {
                    int read = fis.read(bytes, off, bytes.length - off);
                    if (read < 0) {
                        break;
                    }
                    off += read;
                }
                return new String(bytes, 0, off, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read crash report", e);
            return null;
        }
    }
}