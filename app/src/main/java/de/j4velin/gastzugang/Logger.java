package de.j4velin.gastzugang;

import android.database.Cursor;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

public class Logger {

    private static FileWriter fw;
    private static final Date date = new Date();
    private final static String APP = "GuestAccess";

    public static void log(final Throwable ex) {
        if (!BuildConfig.DEBUG)
            return;
        log(ex.getMessage());
        for (StackTraceElement ste : ex.getStackTrace()) {
            log(ste.toString());
        }
    }

    @SuppressWarnings("deprecation")
    public static void log(final String msg) {
        if (!BuildConfig.DEBUG)
            return;
        android.util.Log.d(APP, msg);
        try {
            if (fw == null) {
                fw = new FileWriter(new File(Environment.getExternalStorageDirectory().toString() + "/" + APP + ".log"), true);
            }
            date.setTime(System.currentTimeMillis());
            fw.write(date.toLocaleString() + " - " + msg + "\n");
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void log(final Cursor c) {
        if (!BuildConfig.DEBUG)
            return;
        c.moveToFirst();
        String title = "";
        for (int i = 0; i < c.getColumnCount(); i++)
            title += c.getColumnName(i) + " | ";
        log(title);
        while (!c.isAfterLast()) {
            title = "";
            for (int i = 0; i < c.getColumnCount(); i++)
                title += c.getString(i) + " | ";
            log(title);
            c.moveToNext();
        }
    }

    protected void finalize() throws Throwable {
        try {
            if (fw != null)
                fw.close();
        } finally {
            super.finalize();
        }
    }

}
