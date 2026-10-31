package com.foffano.tclbypass;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Guarda o resultado da última execução para a tela do app. */
public class Status {
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("status", Context.MODE_PRIVATE);
    }

    public static void save(Context c, boolean ok, String msg) {
        String when = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date());
        prefs(c).edit().putBoolean("ok", ok).putString("msg", msg).putString("when", when).apply();
    }

    public static String describe(Context c) {
        SharedPreferences p = prefs(c);
        String when = p.getString("when", null);
        if (when == null) return "Ainda não rodou.";
        return (p.getBoolean("ok", false) ? "✔ " : "✘ ") + p.getString("msg", "") + "  (" + when + ")";
    }
}
