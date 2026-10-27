package dev.satin.debugguard;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AndroidBackend backend;
    private Transaction transaction;

    private TextView permText;
    private TextView sessionText;
    private TextView messageText;
    private final Map<String, TextView> valueViews = new LinkedHashMap<>();
    private TextView safeButton;
    private TextView pauseButton;
    private TextView restoreButton;

    private int colorBg;
    private int colorSurface;
    private int colorInk;
    private int colorMuted;
    private int colorLine;
    private int colorRipple;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        resolveThemeColors();
        backend = new AndroidBackend(this);
        transaction = new Transaction(backend, backend);
        setContentView(buildUi());
        safeButton.setOnClickListener(v -> runSafeProfile());
        pauseButton.setOnClickListener(v -> runPauseRestore(true));
        restoreButton.setOnClickListener(v -> runPauseRestore(false));
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        GuardService.start(this);
        worker.execute(() -> {
            try {
                transaction.recoverInterrupted();
                if (backend.load() != null) Notifications.show(this, false);
            } catch (Exception e) {
                Notifications.show(this, true);
            }
            refresh();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void resolveThemeColors() {
        TypedArray ta = obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorSecondary
        });
        try {
            colorBg = ta.getColor(0, 0xFFFFFFFF);
            colorInk = ta.getColor(1, 0xFF111111);
            colorMuted = ta.getColor(2, 0xFF888888);
        } finally {
            ta.recycle();
        }
        // Surface slightly lifted from the window background so lists read as a panel.
        colorSurface = mix(colorBg, colorInk, isDark() ? 0.10f : 0.04f);
        colorLine = mix(colorBg, colorInk, isDark() ? 0.22f : 0.12f);
        colorRipple = mix(colorInk, colorBg, 0.72f);
    }

    private boolean isDark() {
        return luminance(colorBg) < 0.45f;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(colorBg);

        LinearLayout root = column();
        int pad = dp(20);
        root.setPadding(pad, dp(24), pad, dp(32));
        scroll.addView(root, matchWrap());

        LinearLayout header = row();
        header.setGravity(Gravity.BOTTOM);
        TextView title = label("DebugGuard", 28, colorInk, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        TextView version = label(BuildInfo.VERSION_NAME, 13, colorMuted, false);
        version.setGravity(Gravity.END | Gravity.BOTTOM);
        header.addView(version);
        root.addView(header);
        root.addView(gap(18));

        permText = label("", 13, colorMuted, false);
        root.addView(permText);
        root.addView(gap(14));

        root.addView(sectionTitle("Estado"));
        root.addView(gap(6));
        LinearLayout values = column();
        values.setBackground(hairlineBox());
        values.setPadding(dp(14), dp(4), dp(14), dp(4));
        addValueRow(values, Transaction.DEVELOPER, "Modo desenvolvedor");
        addValueRow(values, Transaction.USB, "Depuração USB");
        addValueRow(values, Transaction.WIFI, "Depuração Wi‑Fi");
        addValueRow(values, SafeProfile.WINDOW, "Animação · janela");
        addValueRow(values, SafeProfile.TRANSITION, "Animação · transição");
        addValueRow(values, SafeProfile.ANIMATOR, "Animação · animador");
        root.addView(values);
        root.addView(gap(10));
        sessionText = label("", 13, colorMuted, false);
        root.addView(sessionText);
        root.addView(gap(22));

        root.addView(sectionTitle("Ações"));
        root.addView(gap(10));
        safeButton = accentButton("Aplicar perfil seguro");
        root.addView(safeButton);
        root.addView(gap(8));
        pauseButton = quietButton("Pausar ADB");
        root.addView(pauseButton);
        root.addView(gap(8));
        restoreButton = quietButton("Restaurar pausa");
        root.addView(restoreButton);
        root.addView(gap(22));

        root.addView(sectionTitle("Notificações"));
        root.addView(gap(8));
        root.addView(label(
                "O Android exige um aviso mínimo enquanto o Guard roda (canal silencioso, sem som).",
                12, colorMuted, false));
        root.addView(gap(10));
        LinearLayout notifRow = row();
        notifRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView notifLabel = label("Notificação extra de pausa", 14, colorInk, false);
        notifLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch extraPauseSwitch = new Switch(this);
        extraPauseSwitch.setChecked(Prefs.extraPauseNotification(this));
        extraPauseSwitch.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setExtraPauseNotification(this, checked);
            if (!checked) Notifications.clear(this);
            messageText.setText(checked
                    ? "Notificação extra ligada (ainda silenciosa)."
                    : "Só o aviso mínimo do Guard.");
            messageText.setTextColor(colorMuted);
        });
        notifRow.addView(notifLabel);
        notifRow.addView(extraPauseSwitch);
        root.addView(notifRow);
        root.addView(gap(6));
        root.addView(label(
                "Desligado = menos ícones. Em Configurações → Apps → DebugGuard → Notificações você pode ocultar o canal do Guard na barra.",
                12, colorMuted, false));
        root.addView(gap(16));

        messageText = label("", 13, colorMuted, false);
        root.addView(messageText);
        root.addView(gap(18));
        root.addView(label("Rotina: ao abrir banco → pausa ADB, reabre o app; ao sair → restaura.", 12, colorMuted, false));

        return scroll;
    }

    private void addValueRow(LinearLayout parent, String key, String title) {
        LinearLayout row = row();
        row.setPadding(0, dp(11), 0, dp(11));
        TextView left = label(title, 14, colorInk, false);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = label("—", 14, colorMuted, false);
        right.setGravity(Gravity.END);
        right.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueViews.put(key, right);
        row.addView(left);
        row.addView(right);
        parent.addView(row);
        if (!SafeProfile.ANIMATOR.equals(key)) {
            View line = new View(this);
            line.setBackgroundColor(colorLine);
            parent.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private void runSafeProfile() {
        setBusy(true);
        worker.execute(() -> {
            String error = null;
            try {
                requireSecureSettings();
                new SafeProfile(backend).apply();
                GuardService.start(MainActivity.this);
            } catch (Exception e) {
                error = shortError(e);
            }
            finishWork(error, error == null ? "Perfil seguro aplicado." : null);
        });
    }

    private void runPauseRestore(boolean pauseRequested) {
        setBusy(true);
        worker.execute(() -> {
            String error = null;
            String ok = null;
            try {
                requireSecureSettings();
                if (pauseRequested) {
                    transaction.pause();
                    Notifications.show(this, false);
                    ok = "ADB pausado. Restaure pela notificação ou pelo botão.";
                } else {
                    transaction.restore();
                    Notifications.clear(this);
                    ok = "Pausa restaurada.";
                }
            } catch (Exception e) {
                error = shortError(e);
                try {
                    if (backend.load() != null) Notifications.show(this, true);
                } catch (Exception ignored) {
                    Notifications.show(this, true);
                }
            }
            finishWork(error, ok);
        });
    }

    private void finishWork(String error, String ok) {
        runOnUiThread(() -> {
            messageText.setText(error != null ? error : (ok != null ? ok : ""));
            messageText.setTextColor(error != null ? 0xFFD14343 : colorMuted);
            setBusy(false);
            refresh();
        });
    }

    private void requireSecureSettings() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Falta WRITE_SECURE_SETTINGS (ADB grant).");
        }
    }

    private void refresh() {
        worker.execute(() -> {
            boolean granted = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
            String session;
            try {
                Transaction.Record record = backend.load();
                session = record == null ? "Sem pausa pendente." : "Pausa ativa · fase " + record.phase + ".";
            } catch (Exception e) {
                session = "Falha ao ler sessão: " + e.getMessage();
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (String key : valueViews.keySet()) {
                try {
                    values.put(key, formatValue(key, backend.read(key)));
                } catch (Exception e) {
                    values.put(key, "erro");
                }
            }
            final boolean okPerm = granted;
            final String sessionFinal = session;
            final Map<String, String> valuesFinal = values;
            runOnUiThread(() -> {
                permText.setText(okPerm ? "WRITE_SECURE_SETTINGS ok" : "WRITE_SECURE_SETTINGS ausente");
                permText.setTextColor(okPerm ? colorMuted : 0xFFD14343);
                sessionText.setText(sessionFinal);
                for (Map.Entry<String, TextView> entry : valueViews.entrySet()) {
                    String formatted = valuesFinal.get(entry.getKey());
                    entry.getValue().setText(formatted);
                    entry.getValue().setTextColor(valueColor(formatted));
                }
                setBusy(false);
            });
        });
    }

    private int valueColor(String formatted) {
        if (formatted == null) return colorMuted;
        if (formatted.startsWith("on") || formatted.equals("0.5")) return colorInk;
        if (formatted.startsWith("off")) return colorMuted;
        return colorInk;
    }

    private static String formatValue(String key, String raw) {
        if (Transaction.DEVELOPER.equals(key) || Transaction.USB.equals(key) || Transaction.WIFI.equals(key)) {
            if ("1".equals(raw)) return "on";
            if ("0".equals(raw)) return "off";
        }
        return raw == null || raw.isEmpty() ? "—" : raw;
    }

    private static String shortError(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    private void setBusy(boolean busy) {
        safeButton.setEnabled(!busy);
        pauseButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
    }

    private TextView sectionTitle(String value) {
        TextView view = label(value.toUpperCase(), 12, colorMuted, true);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView accentButton(String text) {
        // Hard contrast pair so the label survives OLED + screenshot downscale.
        int fill = isDark() ? 0xFFFFFFFF : 0xFF111111;
        int fg = isDark() ? 0xFF000000 : 0xFFFFFFFF;
        TextView button = baseButton(text);
        button.setTextColor(fg);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setBackground(ripple(filled(fill, dp(10)), 0x44000000));
        return button;
    }

    private TextView quietButton(String text) {
        TextView button = baseButton(text);
        button.setTextColor(colorInk);
        GradientDrawable stroke = filled(colorSurface, dp(10));
        stroke.setStroke(dp(1), colorLine);
        button.setBackground(ripple(stroke, colorRipple));
        return button;
    }

    private TextView baseButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setClickable(true);
        button.setFocusable(true);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        button.setLayoutParams(lp);
        return button;
    }

    private TextView label(String value, float sp, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    private GradientDrawable hairlineBox() {
        GradientDrawable drawable = filled(colorSurface, dp(12));
        drawable.setStroke(dp(1), colorLine);
        return drawable;
    }

    private GradientDrawable filled(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private RippleDrawable ripple(GradientDrawable content, int ripple) {
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, null);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private View gap(int dp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp)));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int mix(int base, int tint, float amount) {
        float a = clamp(amount);
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
        int r = Math.round(br + (tr - br) * a);
        int g = Math.round(bg + (tg - bg) * a);
        int b = Math.round(bb + (tb - bb) * a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float luminance(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }
}
