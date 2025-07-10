
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ShiftJISToUTF8 {
    public static void main(String[] args) {
        // このプログラムを実行するターミナルの文字コードがShift_JISに設定されている必要があります。
        // 例: Windowsのコマンドプロンプトの場合、「chcp 932」を実行します。
        System.out.println("Shift_JISで文字列を入力してください:");

        // System.in (標準入力) を "Shift_JIS" として読み込むScannerを作成します。
        // "Shift_JIS"の代わりに、より一般的に利用可能な "MS932" を使用します。
        // 対応していない文字コードが指定された場合、IllegalArgumentExceptionがスローされるため、
        // これをキャッチします。
        try (Scanner scanner = new Scanner(System.in, "MS932")) {

            if (scanner.hasNextLine()) {
                // Shift_JISとして解釈された文字列を取得します。
                // この時点で、文字列はJavaの内部表現(UTF-16)になっています。
                String line = scanner.nextLine();

                System.out.println("----------------------------------------");
                System.out.println("読み取った文字列: " + line);

                // JavaのStringをUTF-8のバイト配列に変換します。
                byte[] utf8Bytes = line.getBytes(StandardCharsets.UTF_8);

                // 確認のために、UTF-8のバイト配列から文字列を再構築して表示します。
                String utf8String = new String(utf8Bytes, StandardCharsets.UTF_8);
                System.out.println("UTF-8として再構築した文字列: " + utf8String);

                System.out.println("変換後のバイト数 (UTF-8): " + utf8Bytes.length);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("エラー: 文字エンコーディング 'MS932' がサポートされていません。");
            System.err.println("Javaの実行環境が、拡張文字セットを含むフルバージョンであるか確認してください。");
            e.printStackTrace();
        }
    }
}