import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class gemini {
    public static void main(String[] args) {
        // !!! ご自身のAPIキーに置き換えてください !!!
        // 環境変数から読み込むことを推奨します: final String API_KEY = System.getenv("GEMINI_API_KEY");
        // この "YOUR_API_KEY_HERE" の部分を、ご自身で取得したAPIキーに置き換えてください。
        final String API_KEY = "AIzaSyD33plKDTDcWA_MtiPF5W5nrxlEaejJBGI";
        // APIキーが設定されているかどうかのチェック。あなたのキーではなく、初期状態の文字列をチェックするように修正します。
        if (API_KEY == null || API_KEY.equals("YOUR_API_KEY_HERE") || API_KEY.isEmpty()) {
            System.err.println("エラー: Gemini APIキーが設定されていません。");
            System.err.println("gemini.java ファイル内の API_KEY をご自身のキーに置き換えるか、環境変数 GEMINI_API_KEY を設定してください。");
            return;
        }

        // APIエンドポイントのモデルを最新のものに更新します (gemini-pro -> gemini-1.5-flash-latest)
        // この変更により "models/gemini-pro is not found" エラーが解消されます。
        final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + API_KEY;

        // コンソールの文字コードをUTF-8に指定して、文字化けを防ぎます。
        // これに合わせて、実行前にコンソールで `chcp 65001` を実行する必要があります。
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            System.out.print("翻訳したいテキストを入力してください: ");
            String inputText = scanner.nextLine().trim();

            // 入力されたテキストが空でないかチェックします
            if (inputText.isEmpty()) {
                System.out.println("テキストが入力されていません。プログラムを終了します。");
                return;
            }

        // Gemini API用のリクエストボディ（英語→日本語翻訳プロンプト）
        // AIが翻訳対象のテキストを確実に認識できるよう、プロンプトの形式をより明確にします。
        String prompt = "Translate the following English text to Japanese: \"" + inputText + "\"";
        JSONObject requestBody = new JSONObject()
            .put("contents", new JSONArray()
                .put(new JSONObject()
                    .put("parts", new JSONArray()
                        .put(new JSONObject().put("text", prompt))
                    )
                )
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                // 安全なオプショナルチェーンによる値の取得
                String translation = json.optJSONArray("candidates")
                                         .optJSONObject(0)
                                         .optJSONObject("content")
                                         .optJSONArray("parts")
                                         .optJSONObject(0)
                                         .optString("text", "翻訳結果が取得できませんでした。");
                System.out.println("\n翻訳結果: " + translation);
            } else {
                // エラーレスポンスをパースして分かりやすく表示
                System.err.println("APIエラー: " + response.statusCode());
                try {
                    JSONObject errorJson = new JSONObject(response.body());
                    String errorMessage = errorJson.optJSONObject("error").optString("message", "詳細不明");
                    System.err.println("エラー詳細: " + errorMessage);
                } catch (Exception parseEx) {
                    System.err.println("エラーレスポンスの解析に失敗しました。");
                    System.err.println(response.body());
                }
            }
        } catch (Exception e) {
            System.err.println("エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
