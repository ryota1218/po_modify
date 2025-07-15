import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Scanner;

/**
 * 文字エンコーディングの変換や、特定のエンコーディングでの読み取りを行うユーティリティクラス。
 */
public class EncodingConverter {

    /**
     * 指定された文字エンコーディングで標準入力から1行読み取ります。
     *
     * @param encoding 読み取りに使用する文字エンコーディング名 (例: "MS932", "UTF-8")
     * @return 読み取った文字列を含むOptional。入力がない場合はempty。
     * @throws IllegalArgumentException サポートされていないエンコーディングが指定された場合
     */
    public static Optional<String> readLineFromTerminal(String encoding) {
        // 標準入力は一度しか読めないため、新しいScannerインスタンスを毎回生成します。
        // このメソッドを複数回呼び出す場合、最初の呼び出しでストリームが消費される点に注意が必要です。
        try (Scanner scanner = new Scanner(System.in, encoding)) {
            if (scanner.hasNextLine()) {
                return Optional.of(scanner.nextLine());
            }
        }
        return Optional.empty();
    }

    /**
     * 指定された文字列を、指定された文字エンコーディングでファイルに書き込みます。
     *
     * @param content 書き込む文字列
     * @param filePath 書き込み先のファイルパス
     * @param charset 書き込みに使用する文字エンコーディング
     * @throws IOException ファイル書き込み中にI/Oエラーが発生した場合
     */
    public static void writeStringToFile(String content, Path filePath, Charset charset) throws IOException {
        Files.writeString(filePath, content, charset);
    }

    /**
     * このクラスの機能を示すデモ用のmainメソッド。
     */
    public static void main(String[] args) {
        // このプログラムを実行するターミナルの文字コードがShift_JISに設定されている必要があります。
        // 例: Windowsのコマンドプロンプトの場合、「chcp 932」を実行します。
        System.out.println("Shift_JISで文字列を入力してください:");

        try {
            // 再利用可能なメソッドを呼び出す
            readLineFromTerminal("MS932").ifPresentOrElse(line -> {
                if (line.isBlank()) {
                    System.out.println("入力が空です。");
                    return;
                }
                System.out.println("----------------------------------------");
                System.out.println("読み取った文字列 (Java内部表現): " + line);

                try {
                    Path outputFile = Paths.get("output_utf8.txt");
                    // 読み取った文字列をUTF-8でファイルに書き込む
                    writeStringToFile(line, outputFile, StandardCharsets.UTF_8);
                    System.out.println("UTF-8に変換した文字列を " + outputFile.toAbsolutePath() + " に保存しました。");
                } catch (IOException e) {
                    System.err.println("ファイルへの書き込み中にエラーが発生しました。");
                    e.printStackTrace();
                }

            }, () -> System.out.println("入力がありませんでした。"));
        } catch (IllegalArgumentException e) {
            System.err.println("エラー: 文字エンコーディング 'MS932' がサポートされていません。");
            System.err.println("Javaの実行環境が、拡張文字セットを含むフルバージョンであるか確認してください。");
            e.printStackTrace();
        }
    }
}
