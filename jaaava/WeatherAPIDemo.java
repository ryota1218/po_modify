import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherAPIDemo {
    public static void main(String[] args) throws IOException, InterruptedException {
        String lat = "34.6937"; // 大阪の緯度
        String lon = "135.5023"; // 大阪の経度

        // 1. WeatherAPI.com
        String weatherApiKey = "415daaf76e124a4a96044142250407";
        String weatherApiUrl = "https://api.weatherapi.com/v1/current.json?key=" + weatherApiKey + "&q=" + lat + ","
                + lon + "&lang=ja";
        String weatherApiResult = fetch(weatherApiUrl);
        System.out.println("WeatherAPI.com: " + weatherApiResult);

        // 2. Open-Meteo
        String openMeteoUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current_weather=true";
        String openMeteoResult = fetch(openMeteoUrl);
        System.out.println("Open-Meteo: " + openMeteoResult);

        // 3. Visual Crossing Weather
        String visualCrossingKey = "Z3G2W5MBQ6UN2SQM3RETULC6Q";
        String visualCrossingUrl = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/"
                + lat + "," + lon + "?unitGroup=metric&key=" + visualCrossingKey + "&lang=ja";
        String visualCrossingResult = fetch(visualCrossingUrl);
        System.out.println("Visual Crossing: " + visualCrossingResult);

        // 4. Tomorrow.io
        String tomorrowKey = "mVjmcBBh9TBDaFfH1x9VQNYvWLKHiLTb";
        String tomorrowUrl = "https://api.tomorrow.io/v4/weather/realtime?location=" + lat + "," + lon + "&apikey="
                + tomorrowKey;
        String tomorrowResult = fetch(tomorrowUrl);
        System.out.println("Tomorrow.io: " + tomorrowResult);
    }

    private static String fetch(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        return res.body();
    }
}