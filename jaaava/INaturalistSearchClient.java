import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * iNaturalist APIを使った生物検索CLIサンプル
 * https://api.inaturalist.org/v1/docs/ 参照
 */
public class INaturalistSearchClient {
    private static final String API_URL = "https://api.inaturalist.org/v1/observations";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    /**
     * iNaturalist APIで生物名（和名・英名）から観察データを検索
     * @param query 検索キーワード
     */
    public void searchObservations(String query) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String requestUrl = API_URL + "?q=" + encodedQuery + "&per_page=5";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
            JsonArray results = obj.getAsJsonArray("results");
            if (results == null || results.size() == 0) {
                System.out.println("該当する観察データが見つかりませんでした。");
                return;
            }
            for (int i = 0; i < results.size(); i++) {
                JsonObject obs = results.get(i).getAsJsonObject();
                String speciesGuess = (obs.has("species_guess") && !obs.get("species_guess").isJsonNull()) ? obs.get("species_guess").getAsString() : "-";
                String place = (obs.has("place_guess") && !obs.get("place_guess").isJsonNull()) ? obs.get("place_guess").getAsString() : "-";
                String observedOn = (obs.has("observed_on") && !obs.get("observed_on").isJsonNull()) ? obs.get("observed_on").getAsString() : "-";
                // 写真URL取得
                String imageUrl = "(画像なし)";
                if (obs.has("photos") && obs.get("photos").isJsonArray()) {
                    JsonArray photos = obs.getAsJsonArray("photos");
                    if (photos.size() > 0) {
                        JsonObject photoObj = photos.get(0).getAsJsonObject();
                        if (photoObj.has("url") && !photoObj.get("url").isJsonNull()) {
                            imageUrl = photoObj.get("url").getAsString();
                        }
                    }
                }
                System.out.printf("\n%d. 種名: %s\n   場所: %s\n   観察日: %s\n   画像: %s\n", (i+1), speciesGuess, place, observedOn, imageUrl);
            }
        } else {
            System.err.println("APIエラー: " + response.statusCode() + ", Body: " + response.body());
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in, "shift_jis");
        System.out.print("検索したい生物名（和名または英名）を入力してください: ");
        String query = scanner.nextLine().trim();
        if (query.isEmpty()) {
            System.out.println("生物名が入力されていません。");
            return;
        }
        INaturalistSearchClient client = new INaturalistSearchClient();
        try {
            client.searchObservations(query);
        } catch (Exception e) {
            System.err.println("検索中にエラーが発生しました。");
            e.printStackTrace();
        }
    }
}
