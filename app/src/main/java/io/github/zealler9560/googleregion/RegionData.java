package io.github.zealler9560.googleregion;

import java.util.Locale;

/**
 * 区域数据：国家 → 运营商（二级联动）→ 城市（联动时区与可选 GPS）。
 * 纯 Java、无 Android 依赖，设置界面与 hook 侧共用同一份数据。
 * 运营商 MCC+MNC 均为各国主流运营商的真实公开编号。
 */
public final class RegionData {

    public static final class Carrier {
        public final String name;
        public final String numeric; // MCC+MNC

        public Carrier(String name, String numeric) {
            this.name = name;
            this.numeric = numeric;
        }

        public String getMccString() {
            return numeric != null && numeric.length() >= 3 ? numeric.substring(0, 3) : "";
        }

        public String getMncString() {
            return numeric != null && numeric.length() > 3 ? numeric.substring(3) : "";
        }

        public int getMcc() {
            try {
                return Integer.parseInt(getMccString());
            } catch (Throwable ignored) {
                return 0;
            }
        }

        public int getMnc() {
            try {
                return Integer.parseInt(getMncString());
            } catch (Throwable ignored) {
                return 0;
            }
        }
    }

    public static final class City {
        public final String name;
        public final String timeZoneId;
        public final double lat;
        public final double lng;

        public City(String name, String timeZoneId, double lat, double lng) {
            this.name = name;
            this.timeZoneId = timeZoneId;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static final class Country {
        public final String id;            // 配置标识（小写国家码）
        public final String zhName;        // 中文名
        public final String flag;          // 国旗 emoji
        public final String iso;           // ISO 国家码（小写，两位）
        public final String language;      // Locale 语言
        public final String localeCountry; // Locale 国家（大写）
        public final String timeZoneId;    // 默认时区 ID
        public final Carrier[] carriers;
        public final City[] cities;        // 第一个为默认城市

        public Country(String id, String zhName, String flag, String iso,
                       String language, String localeCountry, String timeZoneId,
                       Carrier[] carriers, City[] cities) {
            this.id = id;
            this.zhName = zhName;
            this.flag = flag;
            this.iso = iso;
            this.language = language;
            this.localeCountry = localeCountry;
            this.timeZoneId = timeZoneId;
            this.carriers = carriers;
            this.cities = cities;
        }

        public Locale toLocale() {
            return new Locale(language, localeCountry);
        }

        public Carrier carrierByNumeric(String numeric) {
            if (numeric != null) {
                for (Carrier c : carriers) {
                    if (c.numeric.equals(numeric)) {
                        return c;
                    }
                }
            }
            return carriers[0]; // 不认识的运营商回退该国第一家
        }

        public City cityByName(String name) {
            if (name != null) {
                for (City c : cities) {
                    if (c.name.equals(name)) {
                        return c;
                    }
                }
            }
            return cities[0]; // 回退默认城市
        }
    }

    private static Carrier[] carriers(String... nameNumericPairs) {
        Carrier[] out = new Carrier[nameNumericPairs.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Carrier(nameNumericPairs[i * 2], nameNumericPairs[i * 2 + 1]);
        }
        return out;
    }

    private static City[] cities(Object... nameTzLatLng) {
        City[] out = new City[nameTzLatLng.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = new City(
                    (String) nameTzLatLng[i * 4],
                    (String) nameTzLatLng[i * 4 + 1],
                    (Double) nameTzLatLng[i * 4 + 2],
                    (Double) nameTzLatLng[i * 4 + 3]);
        }
        return out;
    }

    /** 全部国家。第一个即默认值（美国）。 */
    public static final Country[] ALL = {
            new Country("us", "美国", "🇺🇸", "us", "en", "US", "America/New_York",
                    carriers("T-Mobile", "310260", "AT&T", "310410", "Verizon", "311480"),
                    cities("纽约", "America/New_York", 40.7128, -74.0060,
                            "洛杉矶", "America/Los_Angeles", 34.0522, -118.2437,
                            "芝加哥", "America/Chicago", 41.8781, -87.6298,
                            "丹佛", "America/Denver", 39.7392, -104.9903,
                            "旧金山", "America/Los_Angeles", 37.7749, -122.4194)),
            new Country("jp", "日本", "🇯🇵", "jp", "ja", "JP", "Asia/Tokyo",
                    carriers("NTT docomo", "44010", "SoftBank", "44020", "au (KDDI)", "44007"),
                    cities("东京", "Asia/Tokyo", 35.6762, 139.6503,
                            "大阪", "Asia/Tokyo", 34.6937, 135.5023)),
            new Country("sg", "新加坡", "🇸🇬", "sg", "en", "SG", "Asia/Singapore",
                    carriers("Singtel", "52501", "StarHub", "52505", "M1", "52503"),
                    cities("新加坡", "Asia/Singapore", 1.3521, 103.8198)),
            new Country("hk", "香港", "🇭🇰", "hk", "zh", "HK", "Asia/Hong_Kong",
                    carriers("CMHK 中国移动香港", "45400", "CSL", "45419", "SmarTone", "45406"),
                    cities("香港", "Asia/Hong_Kong", 22.3193, 114.1694)),
            new Country("tw", "台湾", "🇹🇼", "tw", "zh", "TW", "Asia/Taipei",
                    carriers("中华电信", "46692", "台湾大哥大", "46697", "远传电信", "46601"),
                    cities("台北", "Asia/Taipei", 25.0330, 121.5654,
                            "高雄", "Asia/Taipei", 22.6163, 120.3133,
                            "台中", "Asia/Taipei", 24.1477, 120.6736)),
            new Country("gb", "英国", "🇬🇧", "gb", "en", "GB", "Europe/London",
                    carriers("EE", "23430", "O2", "23410", "Vodafone", "23415"),
                    cities("伦敦", "Europe/London", 51.5074, -0.1278,
                            "曼彻斯特", "Europe/London", 53.4808, -2.2426)),
            new Country("de", "德国", "🇩🇪", "de", "de", "DE", "Europe/Berlin",
                    carriers("Telekom.de", "26201", "O2", "26207", "Vodafone", "26202"),
                    cities("柏林", "Europe/Berlin", 52.5200, 13.4050,
                            "法兰克福", "Europe/Berlin", 50.1109, 8.6821,
                            "慕尼黑", "Europe/Berlin", 48.1351, 11.5820)),
            new Country("kr", "韩国", "🇰🇷", "kr", "ko", "KR", "Asia/Seoul",
                    carriers("SK Telecom", "45005", "KT", "45008", "LG U+", "45006"),
                    cities("首尔", "Asia/Seoul", 37.5665, 126.9780,
                            "釜山", "Asia/Seoul", 35.1796, 129.0756)),
            new Country("th", "泰国", "🇹🇭", "th", "th", "TH", "Asia/Bangkok",
                    carriers("AIS", "52001", "TrueMove H", "52004", "dtac", "52005"),
                    cities("曼谷", "Asia/Bangkok", 13.7563, 100.5018,
                            "清迈", "Asia/Bangkok", 18.7883, 98.9853)),
            new Country("au", "澳大利亚", "🇦🇺", "au", "en", "AU", "Australia/Sydney",
                    carriers("Telstra", "50501", "Optus", "50502", "Vodafone", "50503"),
                    cities("悉尼", "Australia/Sydney", -33.8688, 151.2093,
                            "墨尔本", "Australia/Melbourne", -37.8136, 144.9631)),
            new Country("my", "马来西亚", "🇲🇾", "my", "ms", "MY", "Asia/Kuala_Lumpur",
                    carriers("Maxis", "50212", "Celcom", "50219", "Digi", "50216"),
                    cities("吉隆坡", "Asia/Kuala_Lumpur", 3.1390, 101.6869)),
            new Country("ph", "菲律宾", "🇵🇭", "ph", "en", "PH", "Asia/Manila",
                    carriers("Globe", "51502", "Smart", "51503"),
                    cities("马尼拉", "Asia/Manila", 14.5995, 120.9842)),
            new Country("vn", "越南", "🇻🇳", "vn", "vi", "VN", "Asia/Ho_Chi_Minh",
                    carriers("Viettel", "45204", "Vinaphone", "45202", "Mobifone", "45201"),
                    cities("胡志明市", "Asia/Ho_Chi_Minh", 10.8231, 106.6297,
                            "河内", "Asia/Ho_Chi_Minh", 21.0278, 105.8342)),
            new Country("br", "巴西", "🇧🇷", "br", "pt", "BR", "America/Sao_Paulo",
                    carriers("Vivo", "72410", "Claro", "72405", "TIM", "72402"),
                    cities("圣保罗", "America/Sao_Paulo", -23.5505, -46.6333,
                            "里约热内卢", "America/Sao_Paulo", -22.9068, -43.1729)),
    };

    public static Country byId(String id) {
        if (id != null) {
            for (Country c : ALL) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
        }
        return ALL[0]; // 读不到 / 不认识一律回退默认（美国）
    }
}
