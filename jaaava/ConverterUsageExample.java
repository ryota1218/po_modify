import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class ConverterUsageExample {

    // --- API関連の定数とクラスフィールド ---
    private static final String TRANSLATE_API_URL = "https://api.mymemory.translated.net/get";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    // --- MyMemory APIのレスポンスをマッピングするためのデータクラス ---
    public record MyMemoryResponse(ResponseData responseData, @SerializedName("responseStatus") int responseStatus) {}
    public record ResponseData(String translatedText) {}

    /**
     * MyMemory APIを使用して、日本語のテキストを英語に翻訳します。
     * @param japaneseText 翻訳する日本語のテキスト
     * @return 翻訳された英語のテキストを含むOptional。失敗した場合はempty。
     */
    private static Optional<String> translateToEnglish(String japaneseText) throws IOException, InterruptedException {
        if (japaneseText == null || japaneseText.isBlank()) {
            return Optional.empty();
        }

        String encodedText = URLEncoder.encode(japaneseText, StandardCharsets.UTF_8);
        String requestUrl = String.format("%s?q=%s&langpair=ja%%7Cen", TRANSLATE_API_URL, encodedText);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(requestUrl)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.printf("翻訳APIリクエスト失敗: Status=%d, Body=%s%n", response.statusCode(), response.body());
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(gson.fromJson(response.body(), MyMemoryResponse.class))
                    .filter(res -> res.responseStatus() == 200)
                    .map(MyMemoryResponse::responseData)
                    .map(ResponseData::translatedText)
                    .or(() -> {
                        System.err.println("翻訳APIのレスポンス形式が不正か、API内部でエラーが発生しました。Body: " + response.body());
                        return Optional.empty();
                    });
        } catch (JsonSyntaxException e) {
            System.err.println("翻訳APIのJSONパースに失敗しました。Body: " + response.body());
            return Optional.empty();
        }
    }

    public static void main(String[] args) {
        // ターミナルの文字コードをShift_JISに設定しておく必要があります。
        // chcp 932
        System.out.println("Shift_JISで日本語の文章を入力してください:");

        // EncodingConverterクラスのスタティックメソッドを呼び出す
        EncodingConverter.readLineFromTerminal("MS932").ifPresentOrElse(input -> {
            if (input.isBlank()) {
                System.out.println("入力が空です。");
                return;
            }
            System.out.println("\n--- 翻訳処理を開始します ---");
            System.out.println("入力された日本語: " + input);
            System.out.println("英語に翻訳中...");
            try {
                translateToEnglish(input).ifPresentOrElse(
                    english -> System.out.println("翻訳結果 (英語): " + english),
                    () -> System.out.println("翻訳に失敗しました。")
                );
            } catch (IOException | InterruptedException e) {
                System.err.println("翻訳リクエスト中にエラーが発生しました。");
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }, () -> System.out.println("入力がありませんでした。"));
    }
}