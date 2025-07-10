import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.LinkedHashMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


public class ApiNinjasExercisesClient {

    // API NinjasのURLと自分のAPIキーを設定
    private static final String API_URL = "https://api.api-ninjas.com/v1/exercises";
    // !!! 必ずご自身で取得したAPIキーに置き換えてください !!!
    // APIキーは https://api-ninjas.com/ から無料で取得できます。
    private static final String NINJAS_API_KEY = "UaixcjZGQWmp5xEEYc8hWA==YclDO90PdF4FpoNZ";

    // MyMemory Translation API (APIキー不要)
    private static final String TRANSLATE_API_URL = "https://api.mymemory.translated.net/get";
    // DeepL APIのURLとAPIキー
    private static final String DEEPL_API_URL = "https://api-free.deepl.com/v2/translate";
    private static final String DEEPL_API_KEY = "3a37df0b-08c3-4e98-8182-13bf06175705:fx";

    // 検索可能な筋肉リスト（日本語キー、英語バリュー）
    // LinkedHashMapを使い、挿入順を保持して表示を安定させる
    private static final Map<String, String> MUSCLE_MAP = new LinkedHashMap<>();
    static {
        MUSCLE_MAP.put("腹筋", "abdominals");
        MUSCLE_MAP.put("外転筋", "abductors");
        MUSCLE_MAP.put("内転筋", "adductors");
        MUSCLE_MAP.put("上腕二頭筋", "biceps");
        MUSCLE_MAP.put("ふくらはぎ", "calves");
        MUSCLE_MAP.put("胸筋", "chest");
        MUSCLE_MAP.put("前腕", "forearms");
        MUSCLE_MAP.put("臀部", "glutes");
        MUSCLE_MAP.put("ハムストリング", "hamstrings");
        MUSCLE_MAP.put("広背筋", "lats");
        MUSCLE_MAP.put("腰", "lower_back");
        MUSCLE_MAP.put("背中中部", "middle_back");
        MUSCLE_MAP.put("首", "neck");
        MUSCLE_MAP.put("大腿四頭筋", "quadriceps");
        MUSCLE_MAP.put("僧帽筋", "traps");
        MUSCLE_MAP.put("上腕三頭筋", "triceps");
    }

    // 表示用に筋肉を部位ごとにカテゴリ分けしたMap
    private static final Map<String, List<String>> CATEGORIZED_MUSCLES = new LinkedHashMap<>();
    static {
        CATEGORIZED_MUSCLES.put("【腕の筋肉】", List.of("上腕二頭筋", "上腕三頭筋", "前腕"));
        CATEGORIZED_MUSCLES.put("【脚・お尻の筋肉】", List.of("大腿四頭筋", "ハムストリング", "ふくらはぎ", "臀部", "外転筋", "内転筋"));
        CATEGORIZED_MUSCLES.put("【体幹の筋肉】", List.of("胸筋", "腹筋"));
        CATEGORIZED_MUSCLES.put("【背中の筋肉】", List.of("広背筋", "僧帽筋", "背中中部", "腰"));
        CATEGORIZED_MUSCLES.put("【その他の筋肉】", List.of("首"));
    }


    /**
     * APIからのJSONレスポンスをマッピングするためのデータクラス（Java 14以降のrecordを使用）
     */
    public record Exercise(
            String name,
            String type,
            String muscle,
            String equipment,
            String difficulty,
            String instructions
    ) {}

    /**
     * MyMemory APIからの翻訳結果JSONをマッピングするためのデータクラス
     */
    public record MyMemoryResponse(ResponseData responseData) {}
    public record ResponseData(String translatedText) {}

    // クラスのフィールドとしてクライアントとパーサーを保持
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    /**
     * 指定された筋肉名でエクササイズを検索する
     * @param muscle 検索する筋肉名 (例: "biceps")
     * @return エクササイズのリスト
     */
    public List<Exercise> fetchExercisesByMuscle(String muscle) throws IOException, InterruptedException {
        String encodedMuscle = URLEncoder.encode(muscle, StandardCharsets.UTF_8);
        String requestUrl = API_URL + "?muscle=" + encodedMuscle;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("X-Api-Key", NINJAS_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Type exerciseListType = new TypeToken<List<Exercise>>() {}.getType();
            return gson.fromJson(response.body(), exerciseListType);
        } else {
            System.err.println("APIからのエラー応答: " + response.statusCode());
            System.err.println("エラー内容: " + response.body());
            return List.of(); // 空のリストを返す
        }
    }

    /**
     * テキストを英語から日本語へ翻訳する（MyMemory: 500文字以下、DeepL: 500文字超）
     */
    public Optional<String> translateText(String textToTranslate) throws IOException, InterruptedException {
        if (textToTranslate.length() <= 500) {
            // MyMemory API（従来通り）
            Thread.sleep(200); // 200ミリ秒待機
            String encodedText = URLEncoder.encode(textToTranslate, StandardCharsets.UTF_8);
            String requestUrl = String.format("%s?q=%s&langpair=en%%7Cja", TRANSLATE_API_URL, encodedText);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                try {
                    MyMemoryResponse translated = gson.fromJson(response.body(), MyMemoryResponse.class);
                    if (translated != null && translated.responseData() != null && translated.responseData().translatedText() != null) {
                        return Optional.of(translated.responseData().translatedText());
                    } else {
                        System.err.println("翻訳APIのレスポンス形式が不正です。Body: " + response.body());
                        return Optional.empty();
                    }
                } catch (Exception e) {
                    System.err.println("翻訳APIのJSONパースに失敗しました。Body: " + response.body());
                    return Optional.empty();
                }
            } else {
                System.err.println("翻訳APIへのリクエストが失敗しました。ステータスコード: " + response.statusCode() + ", Body: " + response.body());
            }
            return Optional.empty();
        } else {
            // DeepL API（500文字超）
            String requestBody = "auth_key=" + DEEPL_API_KEY + "&text=" + URLEncoder.encode(textToTranslate, StandardCharsets.UTF_8) + "&source_lang=EN&target_lang=JA";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEEPL_API_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                try {
                    JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
                    JsonArray translations = obj.getAsJsonArray("translations");
                    if (translations != null && translations.size() > 0) {
                        JsonObject trans = translations.get(0).getAsJsonObject();
                        String ja = trans.get("text").getAsString();
                        return Optional.of(ja);
                    }
                } catch (Exception e) {
                    System.err.println("DeepL APIのJSONパースに失敗: " + response.body());
                }
            } else {
                System.err.println("DeepL APIへのリクエストが失敗: " + response.statusCode() + ", Body: " + response.body());
            }
            return Optional.empty();
        }
    }

    /**
     * アプリケーションのメインロジックを実行する
     */
    public void run() {
        Scanner scanner = new Scanner(System.in,"shift_jis");

        try {
            System.out.println("--- 検索可能な筋肉一覧 ---");
            for (Map.Entry<String, List<String>> entry : CATEGORIZED_MUSCLES.entrySet()) {
                System.out.println("\n" + entry.getKey());
                System.out.println("  " + String.join("、", entry.getValue()));
            }
            System.out.print("\n\n上記の中から検索したい筋肉名を入力してください (日本語で): ");
            String muscleToSearchJa = scanner.nextLine().trim();

            if (muscleToSearchJa.isEmpty()) {
                System.out.println("筋肉名が入力されていません。");
                return;
            }
            // 入力された日本語名から対応する英語名を取得
            String muscleToSearchEn = MUSCLE_MAP.get(muscleToSearchJa);

            if (muscleToSearchEn == null) {
                System.out.println("エラー: 入力された筋肉名はリストに存在しません。");
                return;
            }

            System.out.println("\nエクササイズを検索中...");
            List<Exercise> exercises = fetchExercisesByMuscle(muscleToSearchEn);

            if (exercises.isEmpty()) {
                System.out.println("\n「" + muscleToSearchJa + "」に該当するエクササイズは見つかりませんでした。");
            } else {
                System.out.println("--- 「" + muscleToSearchJa + "」のエクササイズ一覧 (翻訳中...) ---");
                for (int i = 0; i < exercises.size(); i++) {
                    Exercise ex = exercises.get(i);

                    // エクササイズ名はDeepLで翻訳
                    String translatedName = "(翻訳失敗)";
                    try {
                        if (ex.name() != null && !ex.name().isEmpty()) {
                            String requestBody = "auth_key=" + DEEPL_API_KEY + "&text=" + URLEncoder.encode(ex.name(), StandardCharsets.UTF_8) + "&source_lang=EN&target_lang=JA";
                            HttpRequest deeplRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(DEEPL_API_URL))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                    .build();
                            HttpResponse<String> deeplResponse = client.send(deeplRequest, HttpResponse.BodyHandlers.ofString());
                            if (deeplResponse.statusCode() == 200) {
                                JsonObject obj = gson.fromJson(deeplResponse.body(), JsonObject.class);
                                JsonArray translations = obj.getAsJsonArray("translations");
                                if (translations != null && translations.size() > 0) {
                                    JsonObject trans = translations.get(0).getAsJsonObject();
                                    translatedName = trans.get("text").getAsString();
                                }
                            } else {
                                System.err.println("DeepL APIへのリクエストが失敗: " + deeplResponse.statusCode() + ", Body: " + deeplResponse.body());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("DeepL APIでエクササイズ名翻訳時にエラー: " + e.getMessage());
                    }

                    // 難易度・器具はDeepLで翻訳
                    String translatedDifficulty = "(翻訳失敗)";
                    try {
                        if (ex.difficulty() != null && !ex.difficulty().isEmpty()) {
                            String requestBody = "auth_key=" + DEEPL_API_KEY + "&text=" + URLEncoder.encode(ex.difficulty(), StandardCharsets.UTF_8) + "&source_lang=EN&target_lang=JA";
                            HttpRequest deeplRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(DEEPL_API_URL))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                    .build();
                            HttpResponse<String> deeplResponse = client.send(deeplRequest, HttpResponse.BodyHandlers.ofString());
                            if (deeplResponse.statusCode() == 200) {
                                JsonObject obj = gson.fromJson(deeplResponse.body(), JsonObject.class);
                                JsonArray translations = obj.getAsJsonArray("translations");
                                if (translations != null && translations.size() > 0) {
                                    JsonObject trans = translations.get(0).getAsJsonObject();
                                    translatedDifficulty = trans.get("text").getAsString();
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("DeepL APIで難易度翻訳時にエラー: " + e.getMessage());
                    }

                    String translatedEquipment = "(翻訳失敗)";
                    try {
                        if (ex.equipment() != null && !ex.equipment().isEmpty()) {
                            String requestBody = "auth_key=" + DEEPL_API_KEY + "&text=" + URLEncoder.encode(ex.equipment(), StandardCharsets.UTF_8) + "&source_lang=EN&target_lang=JA";
                            HttpRequest deeplRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(DEEPL_API_URL))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                    .build();
                            HttpResponse<String> deeplResponse = client.send(deeplRequest, HttpResponse.BodyHandlers.ofString());
                            if (deeplResponse.statusCode() == 200) {
                                JsonObject obj = gson.fromJson(deeplResponse.body(), JsonObject.class);
                                JsonArray translations = obj.getAsJsonArray("translations");
                                if (translations != null && translations.size() > 0) {
                                    JsonObject trans = translations.get(0).getAsJsonObject();
                                    translatedEquipment = trans.get("text").getAsString();
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("DeepL APIで器具翻訳時にエラー: " + e.getMessage());
                    }

                    // 手順は従来通り500文字超はDeepL、500文字以下はMyMemory
                    String translatedInstructions = translateText(ex.instructions()).orElse("(翻訳失敗)");

                    System.out.printf("\n%d. %s%n", (i + 1), ex.name());
                    System.out.printf("   >> %s (難易度: %s)%n", translatedName, translatedDifficulty);
                    System.out.println("   - 器具: " + translatedEquipment);
                    //System.out.println("   - 手順(原文): " + ex.instructions()); // 原文は非表示に
                    System.out.println("   - 手順(翻訳): " + translatedInstructions);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("リクエストの送信中にエラーが発生しました。");
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    public static void main(String[] args) {
        // APIキーが設定されているかチェック
        if (NINJAS_API_KEY.equals("YOUR_API_KEY_HERE") || NINJAS_API_KEY.isEmpty()) {
            System.err.println("エラー: APIキーが設定されていません。");
            System.err.println("ApiNinjasExercisesClient.java ファイル内の NINJAS_API_KEY をあなたのキーに置き換えてください。");
            return;
        }

        ApiNinjasExercisesClient clientApp = new ApiNinjasExercisesClient();
        clientApp.run();
    }
}
