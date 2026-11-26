package io.github.zealler9560.googleregion;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * 配置存取（模块自身进程内用）：读写 region_config，
 * 并负责旧版配置的平滑迁移、以及把配置文件放开读权限
 * 供目标进程的 XSharedPreferences 读取。
 */
public final class Prefs {

    private Prefs() {
    }

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(RegionConfig.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 首次运行落盘默认配置；从旧版本升级时自动补全新增键。 */
    public static void ensureDefaults(Context ctx) {
        SharedPreferences sp = get(ctx);
        if (!sp.contains(RegionConfig.KEY_COUNTRY)) {
            String countryId = "us";
            String legacy = sp.getString(RegionConfig.KEY_LEGACY_PRESET_ID, null);
            if (legacy != null) {
                countryId = RegionData.byId(legacy).id;
            }
            RegionData.Country c = RegionData.byId(countryId);
            RegionData.Carrier carrier = c.carriers[0];
            RegionData.City city = c.cities[0];
            sp.edit()
                    .putString(RegionConfig.KEY_COUNTRY, c.id)
                    .putString(RegionConfig.KEY_CARRIER_NUMERIC, carrier.numeric)
                    .putString(RegionConfig.KEY_CARRIER_NAME, carrier.name)
                    .putString(RegionConfig.KEY_TIMEZONE_ID, city.timeZoneId)
                    .putBoolean(RegionConfig.KEY_TIMEZONE_ENABLED, true)
                    .putBoolean(RegionConfig.KEY_LOCALE_ENABLED, true)
                    .putBoolean(RegionConfig.KEY_ENV_PURIFY_ENABLED, true)
                    .putBoolean(RegionConfig.KEY_GPS_ENABLED, false)
                    .putString(RegionConfig.KEY_GPS_CITY, city.name)
                    .putString(RegionConfig.KEY_GPS_LAT, String.valueOf(city.lat))
                    .putString(RegionConfig.KEY_GPS_LNG, String.valueOf(city.lng))
                    .commit();
            makeWorldReadable(ctx);
            return;
        }

        // 旧版升级补齐新增键
        boolean dirty = false;
        SharedPreferences.Editor editor = sp.edit();
        if (!sp.contains(RegionConfig.KEY_TIMEZONE_ID)) {
            RegionData.Country c = RegionData.byId(sp.getString(RegionConfig.KEY_COUNTRY, "us"));
            RegionData.City city = c.cityByName(sp.getString(RegionConfig.KEY_GPS_CITY, null));
            editor.putString(RegionConfig.KEY_TIMEZONE_ID, city.timeZoneId);
            dirty = true;
        }
        if (!sp.contains(RegionConfig.KEY_TIMEZONE_ENABLED)) {
            editor.putBoolean(RegionConfig.KEY_TIMEZONE_ENABLED, true);
            dirty = true;
        }
        if (!sp.contains(RegionConfig.KEY_LOCALE_ENABLED)) {
            editor.putBoolean(RegionConfig.KEY_LOCALE_ENABLED, true);
            dirty = true;
        }
        if (!sp.contains(RegionConfig.KEY_ENV_PURIFY_ENABLED)) {
            editor.putBoolean(RegionConfig.KEY_ENV_PURIFY_ENABLED, true);
            dirty = true;
        }
        if (dirty) {
            editor.commit();
            makeWorldReadable(ctx);
        }
    }

    /** 读当前配置并解析（界面显示用）。 */
    public static RegionConfig.Resolved current(Context ctx) {
        SharedPreferences sp = get(ctx);
        return RegionConfig.resolve(
                sp.getString(RegionConfig.KEY_COUNTRY, "us"),
                sp.getString(RegionConfig.KEY_CARRIER_NUMERIC, null),
                sp.getString(RegionConfig.KEY_GPS_CITY, null),
                sp.getString(RegionConfig.KEY_TIMEZONE_ID, null),
                sp.getBoolean(RegionConfig.KEY_TIMEZONE_ENABLED, true),
                sp.getBoolean(RegionConfig.KEY_LOCALE_ENABLED, true),
                sp.getBoolean(RegionConfig.KEY_ENV_PURIFY_ENABLED, true),
                sp.getBoolean(RegionConfig.KEY_GPS_ENABLED, false),
                sp.getString(RegionConfig.KEY_GPS_LAT, null),
                sp.getString(RegionConfig.KEY_GPS_LNG, null));
    }

    /** 切国家：运营商重置为该国第一家、城市与时区重置为该国默认城市。 */
    public static void saveCountry(Context ctx, RegionData.Country c) {
        RegionData.Carrier carrier = c.carriers[0];
        RegionData.City city = c.cities[0];
        get(ctx).edit()
                .putString(RegionConfig.KEY_COUNTRY, c.id)
                .putString(RegionConfig.KEY_CARRIER_NUMERIC, carrier.numeric)
                .putString(RegionConfig.KEY_CARRIER_NAME, carrier.name)
                .putString(RegionConfig.KEY_TIMEZONE_ID, city.timeZoneId)
                .putString(RegionConfig.KEY_GPS_CITY, city.name)
                .putString(RegionConfig.KEY_GPS_LAT, String.valueOf(city.lat))
                .putString(RegionConfig.KEY_GPS_LNG, String.valueOf(city.lng))
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveCarrier(Context ctx, RegionData.Carrier carrier) {
        get(ctx).edit()
                .putString(RegionConfig.KEY_CARRIER_NUMERIC, carrier.numeric)
                .putString(RegionConfig.KEY_CARRIER_NAME, carrier.name)
                .commit();
        makeWorldReadable(ctx);
    }

    /** 切城市：同步联动时区 ID 与城市坐标。 */
    public static void saveCity(Context ctx, RegionData.City city) {
        get(ctx).edit()
                .putString(RegionConfig.KEY_TIMEZONE_ID, city.timeZoneId)
                .putString(RegionConfig.KEY_GPS_CITY, city.name)
                .putString(RegionConfig.KEY_GPS_LAT, String.valueOf(city.lat))
                .putString(RegionConfig.KEY_GPS_LNG, String.valueOf(city.lng))
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveTimezoneEnabled(Context ctx, boolean enabled) {
        get(ctx).edit()
                .putBoolean(RegionConfig.KEY_TIMEZONE_ENABLED, enabled)
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveLocaleEnabled(Context ctx, boolean enabled) {
        get(ctx).edit()
                .putBoolean(RegionConfig.KEY_LOCALE_ENABLED, enabled)
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveEnvPurifyEnabled(Context ctx, boolean enabled) {
        get(ctx).edit()
                .putBoolean(RegionConfig.KEY_ENV_PURIFY_ENABLED, enabled)
                .commit();
        makeWorldReadable(ctx);
    }

    public static void saveGpsEnabled(Context ctx, boolean enabled) {
        get(ctx).edit()
                .putBoolean(RegionConfig.KEY_GPS_ENABLED, enabled)
                .commit();
        makeWorldReadable(ctx);
    }

    /** 让目标进程的 XSharedPreferences 能读到配置文件。失败不影响保存本身。 */
    public static void makeWorldReadable(Context ctx) {
        try {
            File dataDir = new File(ctx.getApplicationInfo().dataDir);
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, RegionConfig.PREFS_NAME + ".xml");
            dataDir.setExecutable(true, false);
            prefsDir.setExecutable(true, false);
            prefsDir.setReadable(true, false);
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }
}
