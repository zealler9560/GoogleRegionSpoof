package io.github.zealler9560.googleregion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 设置页：外观（跟随系统/浅色/深色）+ 主题颜色 + 关于。
 * 选择即保存并立即重绘本页；主页在返回时检测到变化也会重绘。
 */
public class SettingsActivity extends Activity {

    private UiTheme theme;

    private static final String[] THEME_LABELS = {"跟随系统", "浅色模式", "深色模式"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = UiTheme.load(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(theme.bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        // edge-to-edge 下避让状态栏/导航栏/挖孔，内容不再顶进状态栏
        SystemBarInsets.applyTo(root);

        // 顶部返回行
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹  返回", 15, theme.accent, true);
        back.setPadding(0, dp(4), dp(12), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);
        TextView title = text("设置", 18, theme.text, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(8);
        top.addView(title, titleLp);
        root.addView(top);

        root.addView(appearanceCard());
        root.addView(aboutCard());
    }

    private View appearanceCard() {
        LinearLayout card = card();
        card.addView(text("🎨  外观", 15, theme.accent, true));

        final int mode = Prefs.get(this).getInt(RegionConfig.KEY_UI_THEME, 0);
        for (int i = 0; i < THEME_LABELS.length; i++) {
            final int value = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(11), 0, dp(11));
            View radio = radioDot(mode == value);
            LinearLayout.LayoutParams radioLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            radioLp.rightMargin = dp(12);
            row.addView(radio, radioLp);
            row.addView(text(THEME_LABELS[i], 15, theme.text, false));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor e = Prefs.get(SettingsActivity.this).edit();
                    e.putInt(RegionConfig.KEY_UI_THEME, value).commit();
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i == 0) {
                lp.topMargin = dp(6);
            }
            card.addView(row, lp);
        }

        // 主题颜色
        TextView colorLabel = text("主题颜色", 14, theme.text, false);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clLp.topMargin = dp(14);
        card.addView(colorLabel, clLp);

        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = dp(10);
        String current = Prefs.get(this).getString(RegionConfig.KEY_UI_ACCENT, "orange");
        for (int i = 0; i < UiTheme.ACCENT_KEYS.length; i++) {
            final String key = UiTheme.ACCENT_KEYS[i];
            boolean selected = key.equals(current);
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(UiTheme.accentColor(key));
            if (selected) {
                d.setStroke(dp(3), theme.text);
            }
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            if (i > 0) {
                lp.leftMargin = dp(14);
            }
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.get(SettingsActivity.this).edit()
                            .putString(RegionConfig.KEY_UI_ACCENT, key).commit();
                    recreate();
                }
            });
            dots.addView(dot, lp);
        }
        card.addView(dots, dotsLp);
        return withTopMargin(card, 14);
    }

    private View aboutCard() {
        LinearLayout card = card();
        card.addView(text("ℹ  关于", 15, theme.accent, true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.addView(text("当前版本", 14, theme.text, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String ver = "1.4";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
        }
        row.addView(text("v" + ver, 14, theme.subText, false));
        card.addView(row, lp);
        return withTopMargin(card, 12);
    }

    private View radioDot(boolean selected) {
        View v = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (selected) {
            d.setColor(theme.accent);
            d.setStroke(dp(2), theme.accent);
        } else {
            d.setColor(0x00000000);
            d.setStroke(dp(2), theme.subText);
        }
        v.setBackground(d);
        if (selected) {
            // 内圈白点效果：叠一个小白圆
            v.setForeground(null);
        }
        return v;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(theme.card);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), theme.cardBorder);
        card.setBackground(bg);
        return card;
    }

    private View withTopMargin(View v, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(marginDp);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
