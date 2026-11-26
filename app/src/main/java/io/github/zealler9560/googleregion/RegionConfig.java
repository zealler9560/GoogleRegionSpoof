package io.github.zealler9560.googleregion;

/**
 * 配置键与解析逻辑：设置界面与 hook 侧共用。
 * 国家、运营商、城市/时区与各项开关分开存储（兼容 v1.0 的 preset_id 做一次性迁移）。
 */
public final class RegionConfig {

    public static final String PREFS_NAME = "region_config";

    // 伪装配置（hook 侧读）
    public static final String KEY_COUNTRY = "country";                     // 国家 id，如 "us"
    public static final String KEY_CARRIER_NUMERIC = "carrier_numeric";     // MCC+MNC
    public static final String KEY_CARRIER_NAME = "carrier_name";           // 运营商名
    public static final String KEY_TIMEZONE_ID = "timezone_id";             // IANA 时区 ID
    public static final String KEY_TIMEZONE_ENABLED = "timezone_enabled";   // 接管应用时区开关
    public static final String KEY_LOCALE_ENABLED = "locale_enabled";       // 接管应用语言开关
    public static final String KEY_ENV_PURIFY_ENABLED = "env_purify_enabled"; // 屏蔽 GMS Wi-Fi/基站扫描开关
    public static final String KEY_GPS_ENABLED = "gps_enabled";             // GPS 伪装开关（高风险）
    public static final String KEY_GPS_CITY = "gps_city";                   // 所选城市名（联动时区与 GPS）
    public static final String KEY_GPS_LAT = "gps_lat";                     // GPS 纬度（字符串存，避免 float 精度）
    public static final String KEY_GPS_LNG = "gps_lng";                     // GPS 经度

    // 界面配置（仅模块自己用）
    public static final String KEY_UI_THEME = "ui_theme";   // 0 跟随系统 / 1 浅色 / 2 深色
    public static final String KEY_UI_ACCENT = "ui_accent"; // orange/blue/green/purple/red

    // v1.0 旧键，仅迁移用
    public static final String KEY_LEGACY_PRESET_ID = "preset_id";

    /** 一次解析完成的生效配置。 */
    public static final class Resolved {
        public final RegionData.Country country;
        public final RegionData.Carrier carrier;
        public final RegionData.City city;
        public final String timeZoneId;
        public final boolean timezoneEnabled;
        public final boolean localeEnabled;
        public final boolean envPurifyEnabled;
        public final boolean gpsEnabled;
        public final double gpsLat;
        public final double gpsLng;

        Resolved(RegionData.Country country, RegionData.Carrier carrier,
                 RegionData.City city, String timeZoneId,
                 boolean timezoneEnabled, boolean localeEnabled,
                 boolean envPurifyEnabled, boolean gpsEnabled,
                 double gpsLat, double gpsLng) {
            this.country = country;
            this.carrier = carrier;
            this.city = city;
            this.timeZoneId = timeZoneId;
            this.timezoneEnabled = timezoneEnabled;
            this.localeEnabled = localeEnabled;
            this.envPurifyEnabled = envPurifyEnabled;
            this.gpsEnabled = gpsEnabled;
            this.gpsLat = gpsLat;
            this.gpsLng = gpsLng;
        }
    }

    /**
     * 由原始配置值解析出生效配置。任何值缺失/非法都逐项回退：
     * 国家回退美国、运营商回退该国第一家、城市回退默认城市、时区回退所选城市时区。
     */
    public static Resolved resolve(String countryId, String carrierNumeric,
                                   String cityName, String timeZoneId,
                                   boolean timezoneEnabled, boolean localeEnabled,
                                   boolean envPurifyEnabled, boolean gpsEnabled,
                                   String gpsLatStr, String gpsLngStr) {
        RegionData.Country country = RegionData.byId(countryId);
        RegionData.Carrier carrier = country.carrierByNumeric(carrierNumeric);
        RegionData.City city = country.cityByName(cityName);
        String resolvedTz = (timeZoneId != null && !timeZoneId.trim().isEmpty())
                ? timeZoneId.trim() : city.timeZoneId;
        double lat = city.lat;
        double lng = city.lng;
        try {
            if (gpsLatStr != null && gpsLngStr != null) {
                lat = Double.parseDouble(gpsLatStr);
                lng = Double.parseDouble(gpsLngStr);
            }
        } catch (Throwable ignored) {
            lat = city.lat;
            lng = city.lng;
        }
        return new Resolved(country, carrier, city, resolvedTz,
                timezoneEnabled, localeEnabled, envPurifyEnabled, gpsEnabled, lat, lng);
    }

    /** 默认配置（美国 / T-Mobile / 纽约 / 时区开 / 语言开 / 环境净化开 / GPS 关）。 */
    public static Resolved defaults() {
        return resolve("us", null, null, null, true, true, true, false, null, null);
    }
}
