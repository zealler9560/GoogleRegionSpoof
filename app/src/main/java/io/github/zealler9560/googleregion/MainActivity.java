package io.github.zealler9560.googleregion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 主页：状态卡 / 当前地区（可搜索选择）/ 运营商卡片（与地区二级联动）/
 * 城市、时区与语言开关 / 环境信号净化与定位开关 / 当前伪装值面板 / 说明 / 设置入口。
 * 全部代码构建 + ScrollView，顶部内容不会再被裁掉；
 * 配色由 UiTheme 按外观设置（浅/深/跟随系统 + 主题色）即时生成。
 */
public class MainActivity extends Activity {

    private UiTheme theme;
    private String themeSignature;
    private LinearLayout root;      // ScrollView 里的纵向容器
    private boolean valuesExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.ensureDefaults(this);
        theme = UiTheme.load(this);
        themeSignature = signature();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.makeWorldReadable(this);
        // 在设置页改了外观/主题色，回来立即生效
        if (!signature().equals(themeSignature)) {
            recreate();
            return;
        }
        refresh();
    }

    private String signature() {
        return Prefs.get(this).getInt(RegionConfig.KEY_UI_THEME, 0) + "/"
                + Prefs.get(this).getString(RegionConfig.KEY_UI_ACCENT, "orange") + "/"
                + (UiTheme.load(this).dark ? "d" : "l");
    }

    // ---------------- 界面搭建 ----------------

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(theme.bg);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        // edge-to-edge 下避让状态栏/导航栏/挖孔，内容不再顶进状态栏
        SystemBarInsets.applyTo(root);
        refresh();
    }

    /** 按当前配置重画全部卡片（切换国家/运营商/城市/开关后调用）。 */
    private void refresh() {
        if (root == null) {
            return;
        }
        root.removeAllViews();
        RegionConfig.Resolved cfg = Prefs.current(this);

        root.addView(statusCard(cfg));
        root.addView(regionCard(cfg));
        root.addView(carrierSection(cfg));
        root.addView(cityTimezoneLocaleCard(cfg));
        root.addView(envAndGpsCard(cfg));
        root.addView(valuesPanel(cfg));
        root.addView(noteView());
        root.addView(settingsEntry());
    }

    private View statusCard(RegionConfig.Resolved cfg) {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF34C759);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.rightMargin = dp(8);
        row.addView(dot, dotLp);

        TextView title = text("谷歌区域伪装", 17, theme.text, true);
        row.addView(title);
        card.addView(row);

        TextView sub = text("伪装配置已保存 · 在 LSPosed 中启用并勾选作用域后，强停目标应用生效",
                13, theme.subText, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        card.addView(sub, subLp);
        return withTopMargin(card, 0);
    }

    private View regionCard(final RegionConfig.Resolved cfg) {
        LinearLayout card = card();
        card.addView(text("当前地区", 13, theme.subText, false));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        TextView name = text(cfg.country.flag + "  " + cfg.country.zhName
                + "  " + cfg.country.iso.toUpperCase(Locale.US), 18, theme.text, true);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView change = text("更改", 15, theme.accent, true);
        change.setPadding(dp(8), dp(4), dp(0), dp(4));
        row.addView(change);
        card.addView(row, rowLp);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCountryPicker();
            }
        });
        return withTopMargin(card, 12);
    }

    private View carrierSection(final RegionConfig.Resolved cfg) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("运营商（随地区联动 · 同步覆盖多卡接口）", 13, theme.subText, false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = dp(4);
        box.addView(label, labelLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        RegionData.Carrier[] carriers = cfg.country.carriers;
        for (int i = 0; i < carriers.length; i++) {
            final RegionData.Carrier carrier = carriers[i];
            boolean selected = carrier.numeric.equals(cfg.carrier.numeric);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(6), dp(12), dp(6), dp(12));
            c.setBackground(roundRect(selected ? theme.accentSoft : theme.card,
                    selected ? theme.accent : theme.cardBorder, selected ? 2 : 1, 12));
            TextView n = text(carrier.name, 14, theme.text, true);
            n.setGravity(Gravity.CENTER);
            c.addView(n);
            TextView num = text(carrier.numeric, 12, theme.subText, false);
            num.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            numLp.topMargin = dp(3);
            c.addView(num, numLp);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.saveCarrier(MainActivity.this, carrier);
                    refresh();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                lp.leftMargin = dp(8);
            }
            row.addView(c, lp);
        }
        box.addView(row, rowLp);
        return withTopMargin(box, 14);
    }

    private View cityTimezoneLocaleCard(final RegionConfig.Resolved cfg) {
        LinearLayout card = card();

        // 城市 / 时区选择行（点击弹出城市选择器，联动时区与 GPS 坐标）
        LinearLayout cityBox = new LinearLayout(this);
        cityBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout cityRow = new LinearLayout(this);
        cityRow.setOrientation(LinearLayout.HORIZONTAL);
        cityRow.setGravity(Gravity.CENTER_VERTICAL);
        cityRow.addView(text("🕒 城市 / 时区", 15, theme.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView city = text(cfg.city.name + "  ›", 15, theme.accent, true);
        city.setPadding(dp(8), dp(2), dp(0), dp(2));
        cityRow.addView(city);
        cityBox.addView(cityRow);
        TextView tzSub = text("当前联动时区：" + cfg.timeZoneId + "（请与代理 IP 所在时区匹配）",
                12, theme.subText, false);
        LinearLayout.LayoutParams tzSubLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tzSubLp.topMargin = dp(4);
        cityBox.addView(tzSub, tzSubLp);
        cityBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCityPicker(cfg);
            }
        });
        card.addView(cityBox);

        // 接管应用时区开关
        card.addView(switchRow(
                "接管应用时区",
                "开启后注入上方城市时区；关闭后直接读取手机系统时区",
                cfg.timezoneEnabled,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.saveTimezoneEnabled(MainActivity.this, checked);
                        Toast.makeText(MainActivity.this, checked
                                ? "已开启时区接管（" + cfg.timeZoneId + "），强停目标应用生效"
                                : "已关闭时区接管，将跟随系统时区", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                }), topMarginLp(14));

        // 接管应用语言开关
        card.addView(switchRow(
                "接管应用语言（Locale）",
                "开启后将目标语言置于首位并保留系统中文回退；关闭后完全跟随系统",
                cfg.localeEnabled,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.saveLocaleEnabled(MainActivity.this, checked);
                        Toast.makeText(MainActivity.this, checked
                                ? "已开启语言接管（保留中文回退），强停目标应用生效"
                                : "已关闭语言接管，强停目标应用生效", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                }), topMarginLp(14));

        return withTopMargin(card, 12);
    }

    private View envAndGpsCard(final RegionConfig.Resolved cfg) {
        LinearLayout card = card();
        card.addView(text("📡 环境与定位防泄漏", 15, theme.text, true));

        // 环境信号净化开关（默认开）
        card.addView(switchRow(
                "环境信号净化（屏蔽 Wi-Fi / 基站扫描）",
                "仅对 GMS 生效，阻断周边 Wi-Fi 列表与基站扫描泄露真实物理位置（推荐开启）",
                cfg.envPurifyEnabled,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.saveEnvPurifyEnabled(MainActivity.this, checked);
                        Toast.makeText(MainActivity.this, checked
                                ? "已开启 GMS 环境信号净化，强停 GMS 后生效"
                                : "已关闭 GMS 环境信号净化", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                }), topMarginLp(12));

        // GPS 伪装开关（高风险，默认关）
        card.addView(switchRow(
                "GPS 伪装（高风险）",
                "⚠ 高风险：不建议对 GMS 开启，易与真实网络环境冲突；开启后使用上方城市坐标",
                cfg.gpsEnabled,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.saveGpsEnabled(MainActivity.this, checked);
                        Toast.makeText(MainActivity.this, checked
                                ? "GPS 伪装已开启（高风险）：请强停目标应用后生效"
                                : "GPS 伪装已关闭", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                }), topMarginLp(14));

        return withTopMargin(card, 12);
    }

    private View valuesPanel(final RegionConfig.Resolved cfg) {
        LinearLayout card = card();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("当前伪装值", 15, theme.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(text(valuesExpanded ? "收起" : "展开", 14, theme.accent, true));
        card.addView(header);

        if (valuesExpanded) {
            StringBuilder sb = new StringBuilder();
            sb.append("SIM 国家码        ").append(cfg.country.iso.toUpperCase(Locale.US)).append('\n');
            sb.append("网络国家码      ").append(cfg.country.iso.toUpperCase(Locale.US)).append('\n');
            sb.append("SIM 运营商       ").append(cfg.carrier.numeric)
                    .append("（").append(cfg.carrier.name)
                    .append(" · MCC ").append(cfg.carrier.getMccString())
                    .append(" / MNC ").append(cfg.carrier.getMncString()).append("）\n");
            sb.append("多卡接口           SubscriptionInfo 已同步\n");
            sb.append("语言 / 地区      ").append(cfg.localeEnabled
                    ? cfg.country.language + "-" + cfg.country.localeCountry + "（首位，保留系统回退）"
                    : "未接管（跟随系统）").append('\n');
            sb.append("时区                 ").append(cfg.timezoneEnabled
                    ? cfg.timeZoneId + "（含 persist.sys.timezone）"
                    : "未接管（跟随系统）").append('\n');
            sb.append("环境净化           ").append(cfg.envPurifyEnabled
                    ? "已开启（仅 GMS 屏蔽 Wi-Fi/基站）"
                    : "未开启").append('\n');
            sb.append("GPS 位置          ").append(cfg.gpsEnabled
                    ? "高风险开启：" + cfg.city.name + "（" + cfg.gpsLat + ", " + cfg.gpsLng + "）"
                    : "未开启（推荐）");
            TextView body = text(sb.toString(), 13, theme.subText, false);
            body.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            card.addView(body, lp);
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                valuesExpanded = !valuesExpanded;
                refresh();
            }
        });
        return withTopMargin(card, 12);
    }

    private View noteView() {
        TextView note = text("说明：本模块只伪装客户端读取到的硬件与环境信号（SIM/多卡/运营商/语言/时区/环境净化），"
                + "不改变 IP 出口，请务必将城市时区与代理住宅 IP 保持一致。修改配置后请强停目标应用；"
                + "Play 商店还需清一次缓存才生效。\n"
                + "⚠ 定位建议：建议开启「环境信号净化」并保持「GPS 伪装」关闭，避免假 GPS 与真实物理网络打架。",
                12, theme.subText, false);
        note.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        note.setLayoutParams(lp);
        return note;
    }

    private View settingsEntry() {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("⚙  设置", 15, theme.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 18, theme.subText, false));
        card.addView(row);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        return withTopMargin(card, 12);
    }

    // ---------------- 选择弹窗 ----------------

    private void showCountryPicker() {
        final List<RegionData.Country> filtered = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<String>());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(4));
        final EditText search = new EditText(this);
        search.setHint("搜索国家或代码");
        search.setSingleLine(true);
        box.addView(search);
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(380)));

        final Runnable refill = new Runnable() {
            @Override
            public void run() {
                String q = search.getText().toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                adapter.clear();
                for (RegionData.Country c : RegionData.ALL) {
                    if (q.isEmpty() || c.zhName.contains(q) || c.id.contains(q)
                            || c.iso.contains(q)) {
                        filtered.add(c);
                        adapter.add(c.flag + "   " + c.zhName + "   "
                                + c.iso.toUpperCase(Locale.US));
                    }
                }
                adapter.notifyDataSetChanged();
            }
        };
        refill.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                refill.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择国家 / 地区")
                .setView(box)
                .setNegativeButton("取消", null)
                .create();
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RegionData.Country picked = filtered.get(position);
                Prefs.saveCountry(MainActivity.this, picked);
                Toast.makeText(MainActivity.this, "已切换到 " + picked.zhName
                        + "（" + picked.carriers[0].name + " · " + picked.cities[0].timeZoneId + "）",
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refresh();
            }
        });
        dialog.show();
    }

    private void showCityPicker(final RegionConfig.Resolved cfg) {
        final RegionData.City[] cities = cfg.country.cities;
        String[] names = new String[cities.length];
        int checked = 0;
        for (int i = 0; i < cities.length; i++) {
            names[i] = cities[i].name + " · " + cities[i].timeZoneId;
            if (cities[i].name.equals(cfg.city.name)) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("选择城市与时区（" + cfg.country.zhName + "）")
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RegionData.City picked = cities[which];
                        Prefs.saveCity(MainActivity.this, picked);
                        Toast.makeText(MainActivity.this,
                                "已切换到 " + picked.name + "（" + picked.timeZoneId + "）",
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 小工具 ----------------

    private View switchRow(String title, String subtitle, boolean checked,
                           CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout swTexts = new LinearLayout(this);
        swTexts.setOrientation(LinearLayout.VERTICAL);
        swTexts.setPadding(0, 0, dp(10), 0);
        swTexts.addView(text(title, 15, theme.text, false));
        TextView sub = text(subtitle, 12, theme.subText, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        swTexts.addView(sub, subLp);
        swRow.addView(swTexts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        swRow.addView(sw);
        return swRow;
    }

    private LinearLayout.LayoutParams topMarginLp(int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(marginDp);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundRect(theme.card, theme.cardBorder, 1, 14));
        return card;
    }

    private View withTopMargin(View v, int marginDp) {
        v.setLayoutParams(topMarginLp(marginDp));
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

    private GradientDrawable roundRect(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            d.setStroke(dp(strokeDp), stroke);
        }
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
