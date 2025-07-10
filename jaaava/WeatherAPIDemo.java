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

    /**
     * ユーザーから正しい形式の日付を取得します。
     */
    private static String getDateFromUser(Scanner scanner) {
        String date;
        while (true) {
            System.out.print("日付を入力してください (例: 2023-07-07): ");
            date = scanner.nextLine();
            try {
                LocalDate.parse(date); // yyyy-MM-dd形式か検証
                break;
            } catch (DateTimeParseException e) {
                System.out.println("日付の形式が正しくありません。yyyy-MM-dd形式で入力してください。");
            }
        }
        return date;
    }

    /**
     * 1. WeatherAPI.com から過去の天気を取得
     */
    private static void getWeatherApiDotCom(String lat, String lon, String date) {
        System.out.println("\n[1. WeatherAPI.com]");
        if (date.compareTo("2010-01-01") >= 0) {
            String url = "https://api.weatherapi.com/v1/history.json?key=" + WEATHER_API_KEY + "&q=" + lat + "," + lon + "&dt=" + date + "&lang=ja";
            fetch(url).ifPresentOrElse(body -> {
                // JsonParserを使用して必要な情報のみを抽出
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonObject forecastday = root.getAsJsonObject("forecast").getAsJsonArray("forecastday").get(0).getAsJsonObject();
                JsonObject day = forecastday.getAsJsonObject("day");
                String condition = day.getAsJsonObject("condition").get("text").getAsString();
                double maxTemp = day.get("maxtemp_c").getAsDouble();
                double minTemp = day.get("mintemp_c").getAsDouble();
                System.out.printf("  天気: %s, 最高気温: %.1f℃, 最低気温: %.1f℃%n", condition, maxTemp, minTemp);
            }, () -> System.out.println("  データの取得に失敗しました。"));
        } else {
            System.out.println("  2010-01-01より前の日付はサポート外です。");
        }
    }

    /**
     * 2. Open-Meteo から過去の天気を取得
     */
    private static void getOpenMeteo(String lat, String lon, String date) {
        System.out.println("\n[2. Open-Meteo]");
        // 過去のデータを取得するため、forecastではなくarchive APIを使用
        String url = "https://archive-api.open-meteo.com/v1/archive?latitude=" + lat + "&longitude=" + lon
                + "&start_date=" + date + "&end_date=" + date + "&daily=weathercode,temperature_2m_max,temperature_2m_min";
        fetch(url).ifPresentOrElse(body -> {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject daily = root.getAsJsonObject("daily");
            double maxTemp = daily.getAsJsonArray("temperature_2m_max").get(0).getAsDouble();
            double minTemp = daily.getAsJsonArray("temperature_2m_min").get(0).getAsDouble();
            System.out.printf("  最高気温: %.1f℃, 最低気温: %.1f℃ (WMOコード: %s)%n",
                    maxTemp, minTemp, daily.getAsJsonArray("weathercode").get(0).getAsString());
        }, () -> System.out.println("  データの取得に失敗しました。"));
    }

    // Visual Crossing APIのレスポンスをマッピングするためのレコード
    public record VisualCrossingResponse(List<Day> days) {
        public record Day(String datetime, double temp, String description) {}
    }

    /**
     * 3. Visual Crossing Weather から過去の天気を取得
     */
    private static void getVisualCrossingWeather(String lat, String lon, String date) {
        System.out.println("\n[3. Visual Crossing Weather]");
        String url = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/"
                + lat + "," + lon + "/" + date + "?unitGroup=metric&key=" + VISUAL_CROSSING_KEY + "&lang=ja&include=days";
        fetch(url).ifPresentOrElse(body -> {
            // Gsonを使用してJSONをJavaオブジェクトにマッピング
            VisualCrossingResponse response = gson.fromJson(body, VisualCrossingResponse.class);
            if (response != null && response.days() != null && !response.days().isEmpty()) {
                VisualCrossingResponse.Day day = response.days().get(0);
                System.out.printf("  天気: %s, 平均気温: %.1f℃%n", day.description(), day.temp());
            } else {
                System.out.println("  有効なデータが見つかりませんでした。");
            }
        }, () -> System.out.println("  データの取得に失敗しました。"));
    }

    /**
     * 4. Tomorrow.io から「現在」の天気を取得
     */
    private static void getTomorrowIoWeather(String lat, String lon) {
        System.out.println("\n[4. Tomorrow.io (リアルタイム)]");
        String url = "https://api.tomorrow.io/v4/weather/realtime?location=" + lat + "," + lon + "&apikey=" + TOMORROW_IO_KEY;
        fetch(url).ifPresentOrElse(body -> {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            JsonObject values = data.getAsJsonObject("values");
            double temp = values.get("temperature").getAsDouble();
            int weatherCode = values.get("weatherCode").getAsInt();
            System.out.printf("  現在の気温: %.1f℃ (天候コード: %d)%n", temp, weatherCode);
        }, () -> System.out.println("  データの取得に失敗しました。"));
    }

    /**
     * 指定されたURLからデータを取得し、レスポンスボディを返す汎用メソッド。
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