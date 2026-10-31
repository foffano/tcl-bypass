package com.foffano.tclbypass;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Tela simples: status, botão "Aplicar agora" e o log. */
public class MainActivity extends Activity {
    private TextView status;
    private TextView log;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(96, 64, 96, 48);
        root.setBackgroundColor(Color.rgb(24, 26, 32));

        TextView title = new TextView(this);
        title.setText("TCL Bypass");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Desativa o HDR toda vez que a TV liga, para a luz de fundo não estourar.\n"
                + "Na primeira vez, aceite \"Permitir depuração\" e marque \"Sempre permitir\".");
        info.setTextSize(16);
        info.setTextColor(Color.rgb(180, 185, 195));
        info.setPadding(0, 16, 0, 24);
        root.addView(info);

        status = new TextView(this);
        status.setTextSize(22);
        status.setTextColor(Color.WHITE);
        root.addView(status);

        Button apply = new Button(this);
        apply.setText("Aplicar agora");
        apply.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                ApplyService.start(MainActivity.this, "botão", 0);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        lp.bottomMargin = 24;
        lp.gravity = Gravity.START;
        root.addView(apply, lp);
        apply.requestFocus();

        ScrollView scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextSize(13);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setTextColor(Color.rgb(150, 155, 165));
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void update() {
        status.setText(ApplyService.isRunning() ? "… aplicando" : Status.describe(this));
        String text = new Applier(this).readLog();
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.length - 25); i < lines.length; i++) sb.append(lines[i]).append('\n');
        log.setText(sb.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }
}
