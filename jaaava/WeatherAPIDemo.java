import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class WeatherAPIDemo {
    // Visual Crossing Weather用の結果クラス
    public static class VisualCrossingResult {
        public String description;
        public double temp;
        public double tempmax;
        public double tempmin;
        public double precip;
        public double precipprob;
        public double humidity;
        public double windspeed;
        public String winddir;
        public double pressure;
        public double cloudcover;
        public String sunrise;
        public String sunset;
        public double uvindex;
    }

    // --- レスポンス格納用クラス ---
    public static class WeatherApiComResult {
        public String condition;
        public int conditionCode;
        public double maxTemp;
        public double minTemp;
        public double maxWind;
        public double totalPrecip;
        public String sunrise;
        public String sunset;
    }

    public static class OpenMeteoResult {
        public String wmoCode;
        public double maxTemp;
        public double minTemp;
        public double precipitation;
        public double maxWindspeed;
        public double sunshineDuration;
    }

    public static class TomorrowIoResult {
        public double temp;
        public double apparentTemp;
        public double humidity;
        public double windSpeed;
        public double windDirection;
        public double pressureSeaLevel;
        public double precipIntensity;
        public double cloudCover;
        public double visibility;
        public double uvIndex;
        public int weatherCode;
    }

    // --- 定数定義 ---
    // APIキーは環境変数から読み込むことを強く推奨します。
    // System.getenv() を使って、コードに直接キーを書くのを避けます。
    private static final String WEATHER_API_KEY = "415daaf76e124a4a96044142250407";
    private static final String VISUAL_CROSSING_KEY = "Z3G2W5MBQ6UN2SQM3RETULC6Q";
    private static final String TOMORROW_IO_KEY = "mVjmcBBh9TBDaFfH1x9VQNYvWLKHiLTb";

    private static final String OSAKA_LAT = "34.6937"; // 大阪の緯度
    private static final String OSAKA_LON = "135.5023"; // 大阪の経度

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        // APIキーが環境変数に設定されているかチェック
        if (WEATHER_API_KEY == null || VISUAL_CROSSING_KEY == null || TOMORROW_IO_KEY == null ||
                WEATHER_API_KEY.isEmpty() || VISUAL_CROSSING_KEY.isEmpty() || TOMORROW_IO_KEY.isEmpty()) {
            System.err.println("エラー: 必要なAPIキーが環境変数に設定されていません。");
            System.err.println("以下の環境変数を設定してください: ");
            System.err.println("  - WEATHER_API_KEY, VISUAL_CROSSING_KEY, TOMORROW_IO_KEY");
            return; // プログラムを終了
        }

        try (Scanner scanner = new Scanner(System.in)) {
            String date = getDateFromUser(scanner);

            System.out.println("\n--- " + date + "の大阪の天気情報を取得します ---");

            // 1. WeatherAPI.com（履歴APIで日付指定）
            getWeatherApiDotCom(OSAKA_LAT, OSAKA_LON, date);

            // 2. Open-Meteo（日付指定）
            getOpenMeteo(OSAKA_LAT, OSAKA_LON, date);

            // 3. Visual Crossing Weather
            getVisualCrossingWeather(OSAKA_LAT, OSAKA_LON, date);

            // 4. Tomorrow.io (リアルタイムAPI)
            getTomorrowIoWeather(OSAKA_LAT, OSAKA_LON);
        }
    }

    private static String getDateFromUser(Scanner scanner) {
        String date;
        while (true) {
            System.out.print("日付を入力してください (例: 2023-07-07 または 3 (3日前)): ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println("入力が空です。");
                continue;
            }

            try {
                // yyyy-MM-dd形式か検証
                LocalDate.parse(input);
                date = input;
                break;
            } catch (DateTimeParseException e1) {
                // yyyy-MM-ddでなければ、数字(日数)か試す
                try {
                    int daysAgo = Integer.parseInt(input);
                    // 負の値は無効
                    if (daysAgo < 0) {
                        System.out.println("日数には0以上の整数を入力してください。");
                        continue;
                    }
                    LocalDate targetDate = LocalDate.now().minusDays(daysAgo);
                    date = targetDate.toString(); // APIで使えるように yyyy-MM-dd 形式に変換
                    System.out.println("( " + date + " として扱います)");
                    break;
                } catch (NumberFormatException e2) {
                    // 日付でも数字でもない場合
                    System.out.println("日付の形式が正しくありません。yyyy-MM-dd形式または日数(整数)で入力してください。");
                }
            }
        }
        return date;
    }

    /**
     * 1. WeatherAPI.com から過去または未来の天気を取得
     */
    private static void getWeatherApiDotCom(String lat, String lon, String date) {
        System.out.println("\n[1. WeatherAPI.com]");
        LocalDate targetDate = LocalDate.parse(date);
        LocalDate today = LocalDate.now();
        boolean isFuture = targetDate.isAfter(today);

        if (date.compareTo("2010-01-01") >= 0) {
            String url = "https://api.weatherapi.com/v1/history.json?key=" + WEATHER_API_KEY + "&q=" + lat + "," + lon
                    + "&dt=" + date + "&lang=ja";
            fetch(url).ifPresentOrElse(body -> {
                WeatherApiComResult result = parseWeatherApiCom(body);
                if (result != null) {
                    printWeatherApiCom(result);
                } else {
                    System.out.println("  データの解析に失敗しました。");
                }
            }, () -> System.out.println("  データの取得に失敗しました。"));
        } else if (isFuture) {
            // 未来の天気を取得
            String url = "https://api.weatherapi.com/v1/forecast.json?key=" + WEATHER_API_KEY + "&q=" + lat + "," + lon
                    + "&dt=" + date + "&lang=ja";
            fetch(url).ifPresentOrElse(body -> {
                WeatherApiComResult result = parseWeatherApiCom(body);
                if (result != null) {
                    printWeatherApiCom(result);
                } else {
                    System.out.println("  データの解析に失敗しました。");
                }
            }, () -> System.out.println("  データの取得に失敗しました。"));
        } else {
            System.out.println("  2010-01-01より前の日付はサポート外です。");
        }
    }

    // WeatherAPI.com レスポンス解析
    private static WeatherApiComResult parseWeatherApiCom(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject forecastday = root.getAsJsonObject("forecast").getAsJsonArray("forecastday").get(0)
                    .getAsJsonObject();
            JsonObject day = forecastday.getAsJsonObject("day");
            JsonObject astro = forecastday.getAsJsonObject("astro");
            WeatherApiComResult result = new WeatherApiComResult();
            result.condition = day.getAsJsonObject("condition").get("text").getAsString();
            result.conditionCode = day.getAsJsonObject("condition").get("code").getAsInt();
            result.maxTemp = day.get("maxtemp_c").getAsDouble();
            result.minTemp = day.get("mintemp_c").getAsDouble();
            result.maxWind = day.get("maxwind_kph").getAsDouble();
            result.totalPrecip = day.get("totalprecip_mm").getAsDouble();
            result.sunrise = astro.get("sunrise").getAsString();
            result.sunset = astro.get("sunset").getAsString();
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // WeatherAPI.com 結果表示
    private static void printWeatherApiCom(WeatherApiComResult r) {
        System.out.printf("  天気: %s (コード: %d), 最高: %.1f℃, 最低: %.1f℃%n", r.condition, r.conditionCode, r.maxTemp,
                r.minTemp);
        System.out.printf("  日の出: %s, 日の入: %s, 最大風速: %.1f km/h%n", r.sunrise, r.sunset, r.maxWind);
        System.out.printf("  降水量: %.1f mm%n", r.totalPrecip);
    }

    /**
     * 2. Open-Meteo から過去または未来の天気を取得
     */
    private static void getOpenMeteo(String lat, String lon, String date) {
        System.out.println("\n[2. Open-Meteo]");
        LocalDate targetDate = LocalDate.parse(date);
        LocalDate today = LocalDate.now();
        boolean isFuture = targetDate.isAfter(today);

        String baseUrl = isFuture ? "https://api.open-meteo.com/v1/forecast"
                : "https://archive-api.open-meteo.com/v1/archive";
        String url = baseUrl + "?latitude=" + lat + "&longitude=" + lon
                + (isFuture ? "&forecast_days=1" : "&start_date=" + date + "&end_date=" + date)
                + "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max";

        fetch(url).ifPresentOrElse(body -> {
            OpenMeteoResult result = parseOpenMeteo(body);
            if (result != null) {
                printOpenMeteo(result);
            } else {
                System.out.println("  データの解析に失敗しました。");
            }
        }, () -> System.out.println("  データの取得に失敗しました。"));
    }

    private static OpenMeteoResult parseOpenMeteo(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("daily") || root.get("daily").isJsonNull())
                return null;
            JsonObject daily = root.getAsJsonObject("daily");
            if (daily == null
                    || !daily.has("temperature_2m_max") || daily.getAsJsonArray("temperature_2m_max").size() == 0
                    || !daily.has("temperature_2m_min") || daily.getAsJsonArray("temperature_2m_min").size() == 0
                    || !daily.has("precipitation_sum") || daily.getAsJsonArray("precipitation_sum").size() == 0
                    || !daily.has("windspeed_10m_max") || daily.getAsJsonArray("windspeed_10m_max").size() == 0
                    || !daily.has("weathercode") || daily.getAsJsonArray("weathercode").size() == 0)
                return null;
            OpenMeteoResult r = new OpenMeteoResult();
            r.maxTemp = daily.getAsJsonArray("temperature_2m_max").get(0).getAsDouble();
            r.minTemp = daily.getAsJsonArray("temperature_2m_min").get(0).getAsDouble();
            r.precipitation = daily.getAsJsonArray("precipitation_sum").get(0).getAsDouble();
            r.maxWindspeed = daily.getAsJsonArray("windspeed_10m_max").get(0).getAsDouble();
            r.wmoCode = daily.getAsJsonArray("weathercode").get(0).getAsString();
            r.sunshineDuration = 0.0;
            if (daily.has("sunshine_duration") && daily.getAsJsonArray("sunshine_duration").size() > 0
                    && !daily.getAsJsonArray("sunshine_duration").get(0).isJsonNull()) {
                r.sunshineDuration = daily.getAsJsonArray("sunshine_duration").get(0).getAsDouble();
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static void printOpenMeteo(OpenMeteoResult r) {
        System.out.printf("  天気コード: %s, 最高: %.1f℃, 最低: %.1f℃%n", r.wmoCode, r.maxTemp, r.minTemp);
        System.out.printf("  降水量: %.1f mm, 最大風速: %.1f km/h", r.precipitation, r.maxWindspeed);
        if (r.sunshineDuration > 0) {
            System.out.printf(", 日照時間: %.1f 時間%n", r.sunshineDuration / 3600);
        } else {
            System.out.println();
        }
    }

    /**
     * 3. Visual Crossing Weather から過去の天気を取得
     */
    private static void getVisualCrossingWeather(String lat, String lon, String date) {
        System.out.println("\n[3. Visual Crossing Weather]");
        String url = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/"
                + lat + "," + lon + "/" + date + "?unitGroup=metric&key=" + VISUAL_CROSSING_KEY
                + "&lang=ja&include=days";
        fetch(url).ifPresentOrElse(body -> {
            VisualCrossingResult result = parseVisualCrossing(body);
            if (result != null) {
                printVisualCrossing(result);
            } else {
                System.out.println("  有効なデータが見つかりませんでした。");
            }
        }, () -> System.out.println("  データの取得に失敗しました。"));
    }

    private static VisualCrossingResult parseVisualCrossing(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("days") || !root.get("days").isJsonArray() || root.getAsJsonArray("days").size() == 0)
                return null;
            JsonObject day = root.getAsJsonArray("days").get(0).getAsJsonObject();
            VisualCrossingResult r = new VisualCrossingResult();
            r.description = day.has("description") && !day.get("description").isJsonNull()
                    ? day.get("description").getAsString()
                    : "";
            r.temp = day.has("temp") && !day.get("temp").isJsonNull() ? day.get("temp").getAsDouble() : 0.0;
            r.tempmax = day.has("tempmax") && !day.get("tempmax").isJsonNull() ? day.get("tempmax").getAsDouble() : 0.0;
            r.tempmin = day.has("tempmin") && !day.get("tempmin").isJsonNull() ? day.get("tempmin").getAsDouble() : 0.0;
            r.precip = day.has("precip") && !day.get("precip").isJsonNull() ? day.get("precip").getAsDouble() : 0.0;
            r.precipprob = day.has("precipprob") && !day.get("precipprob").isJsonNull()
                    ? day.get("precipprob").getAsDouble()
                    : 0.0;
            r.humidity = day.has("humidity") && !day.get("humidity").isJsonNull() ? day.get("humidity").getAsDouble()
                    : 0.0;
            r.windspeed = day.has("windspeed") && !day.get("windspeed").isJsonNull()
                    ? day.get("windspeed").getAsDouble()
                    : 0.0;
            r.winddir = day.has("winddir") && !day.get("winddir").isJsonNull() ? day.get("winddir").getAsString() : "";
            r.pressure = day.has("pressure") && !day.get("pressure").isJsonNull() ? day.get("pressure").getAsDouble()
                    : 0.0;
            r.cloudcover = day.has("cloudcover") && !day.get("cloudcover").isJsonNull()
                    ? day.get("cloudcover").getAsDouble()
                    : 0.0;
            r.sunrise = day.has("sunrise") && !day.get("sunrise").isJsonNull() ? day.get("sunrise").getAsString() : "";
            r.sunset = day.has("sunset") && !day.get("sunset").isJsonNull() ? day.get("sunset").getAsString() : "";
            r.uvindex = day.has("uvindex") && !day.get("uvindex").isJsonNull() ? day.get("uvindex").getAsDouble() : 0.0;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static void printVisualCrossing(VisualCrossingResult r) {
        System.out.printf("  天気: %s, 平均気温: %.1f℃ (最高: %.1f℃, 最低: %.1f℃)%n", r.description, r.temp, r.tempmax,
                r.tempmin);
        System.out.printf("  降水量: %.1f mm, 降水確率: %.0f%%, 湿度: %.0f%%, 風速: %.1f km/h (風向: %s)%n",
                r.precip, r.precipprob, r.humidity, r.windspeed, r.winddir);
        System.out.printf("  気圧: %.1f hPa, 雲量: %.0f%%, UV指数: %.1f%n", r.pressure, r.cloudcover, r.uvindex);
        System.out.printf("  日の出: %s, 日の入: %s%n", r.sunrise, r.sunset);
    }

    /**
     * 4. Tomorrow.io から「現在」の天気を取得
     */
    private static void getTomorrowIoWeather(String lat, String lon) {
        System.out.println("\n[4. Tomorrow.io (リアルタイム)]");
        String url = "https://api.tomorrow.io/v4/weather/realtime?location=" + lat + "," + lon + "&apikey="
                + TOMORROW_IO_KEY;
        fetch(url).ifPresentOrElse(body -> {
            TomorrowIoResult result = parseTomorrowIo(body);
            if (result != null) {
                printTomorrowIo(result);
            } else {
                System.out.println("  データの解析に失敗しました。");
            }
        }, () -> System.out.println("  データの取得に失敗しました。"));
    }

    private static TomorrowIoResult parseTomorrowIo(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || !data.has("values") || data.get("values").isJsonNull())
                return null;
            JsonObject values = data.getAsJsonObject("values");
            TomorrowIoResult r = new TomorrowIoResult();
            r.temp = getSafeDouble(values, "temperature");
            r.apparentTemp = getSafeDouble(values, "apparentTemperature");
            r.humidity = getSafeDouble(values, "humidity");
            r.windSpeed = getSafeDouble(values, "windSpeed");
            r.windDirection = getSafeDouble(values, "windDirection");
            r.pressureSeaLevel = getSafeDouble(values, "pressureSeaLevel");
            r.precipIntensity = getSafeDouble(values, "precipitationIntensity");
            r.cloudCover = getSafeDouble(values, "cloudCover");
            r.visibility = getSafeDouble(values, "visibility");
            r.uvIndex = getSafeDouble(values, "uvIndex");
            r.weatherCode = values.has("weatherCode") && !values.get("weatherCode").isJsonNull()
                    ? values.get("weatherCode").getAsInt()
                    : 0;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static void printTomorrowIo(TomorrowIoResult r) {
        System.out.printf("  気温: %.1f℃ (体感: %.1f℃), 湿度: %.0f%%, 風速: %.1f m/s (風向: %.0f°)%n", r.temp, r.apparentTemp,
                r.humidity, r.windSpeed, r.windDirection);
        System.out.printf("  気圧: %.1f hPa, 降水強度: %.2f mm/hr, 雲量: %.0f%%, 視程: %.1f km, UV指数: %.1f (天気コード: %d)%n",
                r.pressureSeaLevel, r.precipIntensity, r.cloudCover, r.visibility, r.uvIndex, r.weatherCode);
    }

    // Tomorrow.io用のnull安全な値取得メソッド
    private static double getSafeDouble(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsDouble();
            } catch (Exception e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * 指定されたURLからデータを取得し、レスポンスボディを返す汎用メソッド。
     * 
     * @param url 取得先のURL
     * @return レスポンスボディの文字列を含むOptional。失敗した場合はempty。
     */
    private static Optional<String> fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                return Optional.of(res.body());
            } else {
                System.err.printf("  APIエラー (%s): ステータスコード %d, レスポンス: %s%n",
                        URI.create(url).getHost(), res.statusCode(), res.body());
                return Optional.empty();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("  リクエスト中にエラーが発生しました: " + e.getMessage());
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}