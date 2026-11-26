package io.github.zealler9560.googleregion;

import android.content.res.Configuration;
import android.location.Location;
import android.os.LocaleList;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 区域伪装 hook 入口（assets/xposed_init 指向本类）。
 *
 * 只改应用读到的客户端区域信号：TelephonyManager 与 SubscriptionInfo 的 SIM/网络国家与运营商、
 * LocaleList/Locale/Configuration（可开关）、动态城市 TimeZone 与 persist.sys.timezone（可开关）、
 * gsm.* 系统属性、GMS 环境信号净化（屏蔽 Wi-Fi/基站扫描，可开关）、以及可选的 GPS 坐标伪装。
 * 不碰 IP、不碰网络流量。每个 hook 独立 try/catch，失败静默放行原结果，
 * 绝不因本模块让目标应用崩溃。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String MODULE_PACKAGE = "io.github.zealler9560.googleregion";
    private static final String GMS_PACKAGE = "com.google.android.gms";
    private static final String GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String GEMINI_PACKAGE = "com.google.android.apps.bard";
    private static final String ANONYMIZED_BSSID = "02:00:00:00:00:00";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!isTarget(lpparam.packageName)) {
            return;
        }
        RegionConfig.Resolved cfg = loadConfig();
        ClassLoader cl = lpparam.classLoader;

        hookTelephony(cl, cfg);
        hookSubscription(cl, cfg);
        if (cfg.localeEnabled && !shouldSkipLocaleHook(lpparam.packageName)) {
            hookLocaleAndConfiguration(cl, cfg);
        }
        if (cfg.timezoneEnabled) {
            hookTimeZone(cfg);
        }
        hookSystemProperties(cl, cfg);
        if (cfg.envPurifyEnabled && GMS_PACKAGE.equals(lpparam.packageName)) {
            hookEnvPurify(cl);
        }
        if (cfg.gpsEnabled) {
            hookGps(cl, lpparam.packageName, cfg);
        }
    }

    /**
     * Google 应用（含 Gemini 主页/悬浮窗）内部会校验 Resources Configuration 与应用语言，
     * 外部强改 Locale/Configuration 会触发无限 relaunch 抽搐，故自动跳过这两个包的语言 Hook，
     * 仅保留其 SIM/多卡/运营商/时区伪装；GMS、Play 商店等其他包仍正常生效。
     */
    private boolean shouldSkipLocaleHook(String pkg) {
        return GOOGLE_APP_PACKAGE.equals(pkg) || GEMINI_PACKAGE.equals(pkg);
    }

    private boolean isTarget(String pkg) {
        if (pkg == null) {
            return false;
        }
        // LSPosed 作用域是总闸：本模块只会被加载进用户勾选的应用。
        // 谷歌系包名一律放行——用户手动补勾的任何谷歌应用都真正生效。
        return pkg.startsWith("com.google.")
                || "com.android.vending".equals(pkg)
                || "com.android.chrome".equals(pkg);
    }

    /** 读设置界面保存的配置（分开的键）；任何失败逐项回退默认。 */
    private RegionConfig.Resolved loadConfig() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(
                    MODULE_PACKAGE, RegionConfig.PREFS_NAME);
            prefs.makeWorldReadable();
            prefs.reload();
            // v1.0 迁移：只有旧 preset_id 时用它当国家
            String country = prefs.getString(RegionConfig.KEY_COUNTRY, null);
            if (country == null) {
                country = prefs.getString(RegionConfig.KEY_LEGACY_PRESET_ID, "us");
            }
            return RegionConfig.resolve(
                    country,
                    prefs.getString(RegionConfig.KEY_CARRIER_NUMERIC, null),
                    prefs.getString(RegionConfig.KEY_GPS_CITY, null),
                    prefs.getString(RegionConfig.KEY_TIMEZONE_ID, null),
                    prefs.getBoolean(RegionConfig.KEY_TIMEZONE_ENABLED, true),
                    prefs.getBoolean(RegionConfig.KEY_LOCALE_ENABLED, true),
                    prefs.getBoolean(RegionConfig.KEY_ENV_PURIFY_ENABLED, true),
                    prefs.getBoolean(RegionConfig.KEY_GPS_ENABLED, false),
                    prefs.getString(RegionConfig.KEY_GPS_LAT, null),
                    prefs.getString(RegionConfig.KEY_GPS_LNG, null));
        } catch (Throwable t) {
            return RegionConfig.defaults();
        }
    }

    // ---------------- TelephonyManager ----------------

    private void hookTelephony(ClassLoader cl, final RegionConfig.Resolved cfg) {
        final Class<?> tm;
        try {
            tm = XposedHelpers.findClass("android.telephony.TelephonyManager", cl);
        } catch (Throwable t) {
            return;
        }
        String iso = cfg.country.iso;
        String numeric = cfg.carrier.numeric;
        String name = cfg.carrier.name;
        replace(tm, "getSimCountryIso", iso);
        replace(tm, "getNetworkCountryIso", iso);
        replace(tm, "getSimOperator", numeric);
        replace(tm, "getNetworkOperator", numeric);
        replace(tm, "getSimOperatorName", name);
        replace(tm, "getNetworkOperatorName", name);
        // 带 subId/phoneId 参数的隐藏重载（部分谷歌代码走这里），存在才 hook
        replaceWithInt(tm, "getSimCountryIso", iso);
        replaceWithInt(tm, "getNetworkCountryIso", iso);
        replaceWithInt(tm, "getSimOperator", numeric);
        replaceWithInt(tm, "getNetworkOperator", numeric);
        replaceWithInt(tm, "getSimOperatorName", name);
        replaceWithInt(tm, "getNetworkOperatorName", name);
    }

    // ---------------- SubscriptionInfo（Android 10+ 多卡管理 API） ----------------

    private void hookSubscription(ClassLoader cl, final RegionConfig.Resolved cfg) {
        final Class<?> subInfo;
        try {
            subInfo = XposedHelpers.findClass("android.telephony.SubscriptionInfo", cl);
        } catch (Throwable t) {
            return;
        }
        replace(subInfo, "getCountryIso", cfg.country.iso);
        replace(subInfo, "getMccString", cfg.carrier.getMccString());
        replace(subInfo, "getMncString", cfg.carrier.getMncString());
        replace(subInfo, "getMcc", cfg.carrier.getMcc());
        replace(subInfo, "getMnc", cfg.carrier.getMnc());
        replace(subInfo, "getCarrierName", cfg.carrier.name);
        replace(subInfo, "getDisplayName", cfg.carrier.name);
    }

    private void replace(Class<?> clazz, String method, final Object value) {
        try {
            XposedHelpers.findAndHookMethod(clazz, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!param.hasThrowable()) {
                            param.setResult(value);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
            // 方法不存在/签名不同：跳过，不影响其他 hook
        }
    }

    private void replaceWithInt(Class<?> clazz, String method, final Object value) {
        try {
            XposedHelpers.findAndHookMethod(clazz, method, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!param.hasThrowable()) {
                            param.setResult(value);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 环境信号净化（仅对 GMS 屏蔽 Wi-Fi / 基站扫描） ----------------

    private void hookEnvPurify(ClassLoader cl) {
        try {
            Class<?> wifiMgr = XposedHelpers.findClass("android.net.wifi.WifiManager", cl);
            replace(wifiMgr, "getScanResults", Collections.emptyList());
        } catch (Throwable ignored) {
        }
        try {
            Class<?> wifiInfo = XposedHelpers.findClass("android.net.wifi.WifiInfo", cl);
            replace(wifiInfo, "getBSSID", ANONYMIZED_BSSID);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> tm = XposedHelpers.findClass("android.telephony.TelephonyManager", cl);
            replace(tm, "getAllCellInfo", Collections.emptyList());
            replace(tm, "getCellLocation", null);
            try {
                Class<?> cbClass = XposedHelpers.findClass(
                        "android.telephony.TelephonyManager$CellInfoCallback", cl);
                XposedHelpers.findAndHookMethod(tm, "requestCellInfoUpdate",
                        Executor.class, cbClass, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    if (param.args == null || param.args.length < 2
                                            || !(param.args[0] instanceof Executor)
                                            || param.args[1] == null) {
                                        return;
                                    }
                                    Executor executor = (Executor) param.args[0];
                                    final Object callback = param.args[1];
                                    executor.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                XposedHelpers.callMethod(callback,
                                                        "onCellInfo", Collections.emptyList());
                                            } catch (Throwable ignored) {
                                            }
                                        }
                                    });
                                    param.setResult(null);
                                } catch (Throwable ignored) {
                                }
                            }
                        });
            } catch (Throwable ignored) {
                // Android 10 以下无 CellInfoCallback，跳过
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- Locale / LocaleList / Configuration ----------------

    /**
     * 在装入任何 Locale hook 之前读取真实系统语言列表，
     * 构造 [目标语言, 原系统语言...]，兼顾区域检测与中文回退。
     */
    private LocaleList buildLocaleList(Locale primary) {
        List<Locale> list = new ArrayList<>();
        list.add(primary);
        try {
            LocaleList orig = LocaleList.getDefault();
            if (orig != null) {
                for (int i = 0; i < orig.size(); i++) {
                    Locale loc = orig.get(i);
                    if (loc != null && !containsLocale(list, loc)) {
                        list.add(loc);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (list.size() == 1) {
            try {
                Locale sys = Locale.getDefault();
                if (sys != null && !containsLocale(list, sys)) {
                    list.add(sys);
                }
            } catch (Throwable ignored) {
            }
        }
        return new LocaleList(list.toArray(new Locale[0]));
    }

    private boolean containsLocale(List<Locale> list, Locale target) {
        for (Locale item : list) {
            if (item.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private void hookLocaleAndConfiguration(ClassLoader cl, final RegionConfig.Resolved cfg) {
        final Locale primary = cfg.country.toLocale();
        final LocaleList fakeLocaleList = buildLocaleList(primary);

        try {
            XposedHelpers.findAndHookMethod(Locale.class, "getDefault", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(primary);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(Locale.class, "getDefault",
                    Locale.Category.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                param.setResult(primary);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> llClass = XposedHelpers.findClass("android.os.LocaleList", cl);
            replace(llClass, "getDefault", fakeLocaleList);
            replace(llClass, "getAdjustedDefault", fakeLocaleList);
        } catch (Throwable ignored) {
        }

        try {
            Class<?> cfgClass = XposedHelpers.findClass("android.content.res.Configuration", cl);
            XposedHelpers.findAndHookMethod(cfgClass, "getLocales", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object result = param.getResult();
                        // 仅替换非空语言列表，保留空 override/diff Configuration 原样
                        if (result instanceof LocaleList && !((LocaleList) result).isEmpty()) {
                            param.setResult(fakeLocaleList);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> resClass = XposedHelpers.findClass("android.content.res.Resources", cl);
            XposedHelpers.findAndHookMethod(resClass, "getConfiguration", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object result = param.getResult();
                        if (result instanceof Configuration) {
                            // 返回改过 LocaleList 的副本，不动应用手里的原对象
                            Configuration copy = new Configuration((Configuration) result);
                            copy.setLocales(fakeLocaleList);
                            param.setResult(copy);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- TimeZone（动态城市时区） ----------------

    private void hookTimeZone(final RegionConfig.Resolved cfg) {
        final TimeZone targetTz = TimeZone.getTimeZone(cfg.timeZoneId);
        try {
            XposedHelpers.findAndHookMethod(TimeZone.class, "getDefault", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(targetTz.clone());
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(TimeZone.class, "getDefaultRef", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(targetTz);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // ---------------- SystemProperties（gsm.* 与 persist.sys.timezone） ----------------

    private void hookSystemProperties(ClassLoader cl, final RegionConfig.Resolved cfg) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 1
                                || !(param.args[0] instanceof String)) {
                            return;
                        }
                        String key = (String) param.args[0];
                        String value = null;
                        if ("gsm.sim.operator.iso-country".equals(key)
                                || "gsm.operator.iso-country".equals(key)) {
                            value = cfg.country.iso;
                        } else if ("gsm.sim.operator.numeric".equals(key)
                                || "gsm.operator.numeric".equals(key)) {
                            value = cfg.carrier.numeric;
                        } else if ("gsm.sim.operator.alpha".equals(key)
                                || "gsm.operator.alpha".equals(key)) {
                            value = cfg.carrier.name;
                        } else if (cfg.timezoneEnabled && "persist.sys.timezone".equals(key)) {
                            value = cfg.timeZoneId;
                        }
                        if (value != null) {
                            param.setResult(value);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, hook);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class, hook);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- GPS（仅开关开启时） ----------------

    /**
     * GPS 伪装：给所选城市坐标加一个按「包名+城市」哈希出的稳定小偏移
     * （同设备同应用每次一致，约 ±130 米内），避免坐标精确落在市中心点上。
     * hook 面：Location.getLatitude/getLongitude（所有 Location 对象读数改写）、
     * LocationManager.getLastKnownLocation（返回构造的假 Location）。
     */
    private void hookGps(ClassLoader cl, String packageName, RegionConfig.Resolved cfg) {
        int h = (packageName + "|" + cfg.city.name).hashCode();
        final double fakeLat = cfg.gpsLat + (((h & 0xFF) - 128) / 128.0) * 0.0012;
        final double fakeLng = cfg.gpsLng + ((((h >> 8) & 0xFF) - 128) / 128.0) * 0.0012;

        try {
            Class<?> locClass = XposedHelpers.findClass("android.location.Location", cl);
            XposedHelpers.findAndHookMethod(locClass, "getLatitude", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(fakeLat);
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedHelpers.findAndHookMethod(locClass, "getLongitude", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(fakeLng);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> lmClass = XposedHelpers.findClass("android.location.LocationManager", cl);
            XposedHelpers.findAndHookMethod(lmClass, "getLastKnownLocation",
                    String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                String provider = param.args != null && param.args.length > 0
                                        && param.args[0] instanceof String
                                        ? (String) param.args[0] : "gps";
                                Location fake = new Location(provider);
                                fake.setLatitude(fakeLat);
                                fake.setLongitude(fakeLng);
                                fake.setAccuracy(12f);
                                fake.setTime(System.currentTimeMillis());
                                try {
                                    fake.setElapsedRealtimeNanos(
                                            SystemClock.elapsedRealtimeNanos());
                                } catch (Throwable ignored) {
                                }
                                param.setResult(fake);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }
}
