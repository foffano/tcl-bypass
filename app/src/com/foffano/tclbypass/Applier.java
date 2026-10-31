package com.foffano.tclbypass;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faz o mesmo que scripts/aplicar.sh, mas de dentro da TV:
 * grava as configurações de HDR, abre o menu escondido "Seleção de formato" e
 * alterna Automático -> Manual para o sistema aplicar a lista de formatos desativados.
 */
public class Applier {
    private static final String TAG = "TclBypass";
    private static final Object LOCK = new Object();

    private static final String[] ADVANCED = {"Configurações de tela avançadas", "Advanced display settings"};
    private static final String[] FORMAT = {"Seleção de formato", "Format selection"};
    private static final String[] AUTOMATIC = {"Automático", "Automatic"};
    private static final String[] MANUAL = {"Manual"};

    private static final Pattern NODE = Pattern.compile("<node [^>]*>");
    private static final Pattern ATTR_TEXT = Pattern.compile(" text=\"([^\"]*)\"");
    private static final Pattern ATTR_FOCUSED = Pattern.compile(" focused=\"(true|false)\"");
    private static final Pattern ATTR_BOUNDS = Pattern.compile(" bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"");

    private final Context context;
    private final File logFile;

    public Applier(Context context) {
        this.context = context.getApplicationContext();
        this.logFile = new File(this.context.getFilesDir(), "log.txt");
    }

    /** Aplica com até 3 tentativas. Retorna true se o display confirmou [1, 2, 3, 4]. */
    public boolean run(String reason) {
        synchronized (LOCK) {
            log("== início (" + reason + ")");
            for (int attempt = 1; attempt <= 3; attempt++) {
                try (AdbClient adb = connect()) {
                    if (applyOnce(adb)) {
                        log("OK: HDR desativado e aplicado (tentativa " + attempt + ")");
                        Status.save(context, true, "HDR desativado");
                        return true;
                    }
                    log("ainda não aplicou, tentativa " + attempt);
                } catch (Exception e) {
                    log("erro na tentativa " + attempt + ": " + e);
                }
                sleep(10000);
            }
            Status.save(context, false, "Falhou, veja o log");
            return false;
        }
    }

    public String readLog() {
        try {
            return new String(java.nio.file.Files.readAllBytes(logFile.toPath()), "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private AdbClient connect() throws IOException {
        // Logo depois do boot o adbd pode demorar a abrir a porta 5555.
        IOException last = null;
        for (int i = 0; i < 24; i++) {
            try {
                return new AdbClient("127.0.0.1", 5555, context.getFilesDir(), 120000);
            } catch (IOException e) {
                last = e;
                sleep(5000);
            }
        }
        throw last;
    }

    private boolean applyOnce(AdbClient adb) throws IOException {
        adb.shell("settings put global are_user_disabled_hdr_formats_allowed 0");
        adb.shell("settings put global user_disabled_hdr_formats 1,2,3,4");
        log("configurações gravadas");

        adb.shell("am start -S -n com.android.tv.settings/.device.displaysound.DisplaySoundActivity");
        sleep(3000);
        // Passa direto por "Frame rate" e "Resolução" sem parar: se o foco ficar em
        // "Resolução", a prévia dela derruba o app de Configurações nesta TCL
        // (NoSuchMethodError em ResolutionSelectionFragment).
        adb.shell("input keyevent 20 20 20");
        sleep(1200);
        if (!select(adb, ADVANCED)) return home(adb, "não achei 'Configurações de tela avançadas'");
        sleep(2000);
        if (!select(adb, FORMAT)) return home(adb, "não achei 'Seleção de formato'");
        sleep(2000);
        if (!select(adb, AUTOMATIC)) return home(adb, "não achei 'Automático'");
        sleep(1500);
        if (!select(adb, MANUAL)) return home(adb, "não achei 'Manual'");
        sleep(1500);
        adb.shell("input keyevent 4; sleep 0.5; input keyevent 4; sleep 0.5; input keyevent 4");

        String display = adb.shell("dumpsys display | grep -m1 -oE 'userDisabledHdrTypes \\[[^]]*\\]'").trim();
        log("display: " + display);
        return display.contains("1, 2, 3, 4");
    }

    private boolean home(AdbClient adb, String why) throws IOException {
        log(why);
        adb.shell("input keyevent 3");
        return false;
    }

    /** Desce na lista até o item focado ter um dos textos e aperta OK. Só aperta OK se achar. */
    private boolean select(AdbClient adb, String[] targets) throws IOException {
        for (int i = 0; i < 10; i++) {
            List<String> focused = focusedTexts(adb);
            String shown = focused.toString();
            log("focado: " + (shown.length() > 80 ? shown.substring(0, 80) + "…" : shown));
            for (String t : focused) {
                for (String target : targets) {
                    if (t.equals(target)) {
                        adb.shell("input keyevent 23");
                        log("selecionado: " + target);
                        return true;
                    }
                }
            }
            adb.shell("input keyevent 20");
            sleep(600);
        }
        return false;
    }

    /** Textos dentro do retângulo do elemento focado na tela. */
    private List<String> focusedTexts(AdbClient adb) throws IOException {
        String xml = "";
        for (int i = 0; i < 3 && !xml.contains("<node"); i++) {
            xml = adb.shell("uiautomator dump /data/local/tmp/tclbypass_ui.xml >/dev/null 2>&1; cat /data/local/tmp/tclbypass_ui.xml");
            if (!xml.contains("<node")) sleep(800);
        }
        if (!xml.contains("<node")) log("dump vazio: " + xml.trim());
        List<int[]> boxes = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<int[]> textBoxes = new ArrayList<>();
        Matcher m = NODE.matcher(xml);
        while (m.find()) {
            String node = m.group();
            int[] b = bounds(node);
            if (b == null) continue;
            Matcher f = ATTR_FOCUSED.matcher(node);
            if (f.find() && f.group(1).equals("true")) boxes.add(b);
            Matcher t = ATTR_TEXT.matcher(node);
            if (t.find() && !t.group(1).isEmpty()) {
                texts.add(t.group(1).trim());
                textBoxes.add(b);
            }
        }
        List<String> result = new ArrayList<>();
        for (int[] box : boxes) {
            for (int i = 0; i < texts.size(); i++) {
                int[] tb = textBoxes.get(i);
                if (tb[0] >= box[0] && tb[1] >= box[1] && tb[2] <= box[2] && tb[3] <= box[3]) {
                    result.add(texts.get(i));
                }
            }
        }
        return result;
    }

    private static int[] bounds(String node) {
        Matcher b = ATTR_BOUNDS.matcher(node);
        if (!b.find()) return null;
        return new int[]{Integer.parseInt(b.group(1)), Integer.parseInt(b.group(2)),
                Integer.parseInt(b.group(3)), Integer.parseInt(b.group(4))};
    }

    private void log(String s) {
        Log.i(TAG, s);
        String line = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date()) + "  " + s + "\n";
        try {
            if (logFile.length() > 64 * 1024) logFile.delete();
            try (FileWriter w = new FileWriter(logFile, true)) {
                w.write(line);
            }
        } catch (IOException ignored) {
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
