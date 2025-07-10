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
import java.util.Scanner;

public class ShiftJISToUTF8 {

    // --- API関連の定数とクラスフィールドをstaticに ---
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

        // URLエンコード
        String encodedText = URLEncoder.encode(japaneseText, StandardCharsets.UTF_8);
        // APIリクエストURLを構築 (langpair=ja|en)
        String requestUrl = String.format("%s?q=%s&langpair=ja%%7Cen", TRANSLATE_API_URL, encodedText);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.printf("翻訳APIリクエスト失敗: Status=%d, Body=%s%n", response.statusCode(), response.body());
            return Optional.empty();
        }

        try {
            MyMemoryResponse translated = gson.fromJson(response.body(), MyMemoryResponse.class);
            // Optionalを使って、nullチェックとステータス検証をチェーンする
            return Optional.ofNullable(translated)
                    .filter(res -> res.responseStatus() == 200)
                    .map(MyMemoryResponse::responseData)
                    .map(ResponseData::translatedText)
                    .or(() -> { // 条件に合わない場合（レスポンス不正など）
                        System.err.println("翻訳APIのレスポンス形式が不正か、API内部でエラーが発生しました。Body: " + response.body());
                        return Optional.empty();
                    });
        } catch (JsonSyntaxException e) {
            System.err.println("翻訳APIのJSONパースに失敗しました。Body: " + response.body());
            return Optional.empty();
        }
    }


    public static void main(String[] args) {
        // このプログラムを実行するターミナルの文字コードがShift_JISに設定されている必要があります。
        // 例: Windowsのコマンドプロンプトの場合、「chcp 932」を実行します。
        System.out.println("Shift_JISで日本語の文章を入力してください:");

        // System.in (標準入力) を "Shift_JIS" として読み込むScannerを作成します。
        // "Shift_JIS"の代わりに、より一般的に利用可能な "MS932" を使用します。
        try (Scanner scanner = new Scanner(System.in, "MS932")) {
            if (!scanner.hasNextLine()) {
                return;
            }
            String line = scanner.nextLine();
            if (line.isBlank()) {
                System.out.println("入力が空です。");
                return;
            }

            System.out.println("----------------------------------------");
            System.out.println("読み取った文字列: " + line);
            System.out.println("英語に翻訳中...");

            // 翻訳処理を直接呼び出す
            translateToEnglish(line).ifPresentOrElse(
                english -> System.out.println("翻訳結果 (英語): " + english),
                () -> System.out.println("翻訳に失敗しました。")
            );

        } catch (IllegalArgumentException e) {
            System.err.println("エラー: 文字エンコーディング 'MS932' がサポートされていません。");
            System.err.println("Javaの実行環境が、拡張文字セットを含むフルバージョンであるか確認してください。");
            e.printStackTrace();
        } catch (IOException | InterruptedException e) {
            System.err.println("翻訳リクエスト中にエラーが発生しました。");
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
}
