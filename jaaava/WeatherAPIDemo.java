import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class WeatherAPIDemo {

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String date;
        while (true) {
            System.out.print("日付を入力してください (例: 2023-07-07): ");
            date = scanner.nextLine();
            if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                System.out.println("日付の形式が正しくありません。yyyy-MM-dd形式で入力してください。");
                continue;
            }
            break;
        }

        String lat = "34.6937"; // 大阪の緯度
        String lon = "135.5023"; // 大阪の経度

        // 1. WeatherAPI.com（履歴APIで日付指定）
        if (date.compareTo("2010-01-01") >= 0) {
            String weatherApiKey = "415daaf76e124a4a96044142250407";
            String weatherApiUrl = "https://api.weatherapi.com/v1/history.json?key=" + weatherApiKey + "&q=" + lat + ","
                    + lon + "&dt=" + date + "&lang=ja";
            String weatherApiResult = fetch(weatherApiUrl);
            System.out.println("WeatherAPI.com: " + weatherApiResult);
        } else {
            System.out.println("WeatherAPI.com: 対応していない日付のためスキップ");
        }

        // 2. Open-Meteo（日付指定）
        if (date.compareTo("2016-01-01") >= 0 && date.compareTo("2025-07-22") <= 0) {
            String openMeteoUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&start_date=" + date + "&end_date=" + date + "&hourly=temperature_2m&current_weather=true";
            String openMeteoResult = fetch(openMeteoUrl);
            System.out.println("Open-Meteo: " + openMeteoResult);
        } else {
            System.out.println("Open-Meteo: 対応していない日付のためスキップ");
        }

        // 3. Visual Crossing Weather
        // 過去50年以上 期間、場所、項目を選ぶだけでAPIのURLを自動生成してくれるツール
        String visualCrossingKey = "Z3G2W5MBQ6UN2SQM3RETULC6Q";
        String visualCrossingUrl = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/"
                + lat + "," + lon + "/" + date + "?unitGroup=metric&key=" + visualCrossingKey + "&lang=ja";
        String visualCrossingResult = fetch(visualCrossingUrl);
        System.out.println("Visual Crossing: " + visualCrossingResult);

        // 4. Tomorrow.io
        // 分単位の短期予報 大気質花粉やPM2.5や道路状況
        String tomorrowKey = "mVjmcBBh9TBDaFfH1x9VQNYvWLKHiLTb";
        String tomorrowUrl = "https://api.tomorrow.io/v4/weather/realtime?location=" + lat + "," + lon + "&apikey="
                + tomorrowKey;
        String tomorrowResult = fetch(tomorrowUrl);
        System.out.println("Tomorrow.io: " + tomorrowResult);
        scanner.close();
    }

    private static String fetch(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        return res.body();
    }
}