import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.text.Normalizer;
import java.util.Optional;

public class PokeQ extends JFrame {
    private JLabel pokemonImageLabel;
    private final PokeApiClient apiClient = new PokeApiClient();
    private String correctName;
    private int hintLevel;

    public PokeQ() {
        setTitle("このポケモンの名前は？");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(360, 420); // ウィンドウサイズも少し大きく
        setLocationRelativeTo(null);
        pokemonImageLabel = new JLabel();
        pokemonImageLabel.setPreferredSize(new Dimension(240, 240)); // 画像サイズを大きく
        pokemonImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        // Swing UI部品は画像のみ
        Container contentPane = getContentPane();
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.CENTER;
        contentPane.add(pokemonImageLabel, gbc);
    }

    private void showPokemonImage(String imageUrl) throws IOException {
        ImageIcon originalIcon = new ImageIcon(new URL(imageUrl));
        Image originalImage = originalIcon.getImage();
        int newSize = 240; // 画像サイズを大きく
        Image scaledImage = originalImage.getScaledInstance(newSize, newSize, Image.SCALE_SMOOTH);
        pokemonImageLabel.setIcon(new ImageIcon(scaledImage));
    }

    private static Optional<String> findJapaneseName(PokeApiClient.PokemonData.PokemonSpeciesResponse species) {
        return species.names().stream()
                .filter(n -> "ja-Hrkt".equals(n.language().name()))
                .map(PokeApiClient.PokemonData.NameEntry::name)
                .findFirst()
                .or(() -> species.names().stream()
                        .filter(n -> "ja".equals(n.language().name()))
                        .map(PokeApiClient.PokemonData.NameEntry::name)
                        .findFirst());
    }

    private void loadNextQuestion() {
        new Thread(() -> {
            try {
                PokeApiClient.PokemonData.PokemonResponse pokemon = apiClient.fetchRandomPokemon()
                        .orElseThrow(() -> new IOException("ポケモンの取得に失敗しました。"));
                PokeApiClient.PokemonData.PokemonSpeciesResponse species = apiClient.fetchPokemonSpecies(pokemon.species().url())
                        .orElseThrow(() -> new IOException("ポケモンの日本語名の取得に失敗しました。"));
                correctName = findJapaneseName(species)
                        .orElseThrow(() -> new IOException("ポケモンの日本語名が見つかりませんでした。"));
                // 画像表示はUIスレッドで
                SwingUtilities.invokeLater(() -> {
                    try {
                        showPokemonImage(pokemon.sprites().frontDefault());
                    } catch (IOException e) {
                        System.out.println("画像の表示に失敗: " + e.getMessage());
                    }
                });
                // ターミナル入出力はワーカースレッドで
                System.out.println("このポケモンの名前は？（カタカナで）");
                System.out.print("こたえを入力: ");
                // コンソールからの入力をUTF-8で受け取るように修正
                java.util.Scanner sc = new java.util.Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
                String userAnswer = sc.nextLine().trim();
                checkAnswerTerminal(userAnswer);
            } catch (Exception e) {
                System.out.println("エラー: " + e.getMessage());
            }
        }).start();
    }

    // ターミナル用の判定・ヒント表示
    private void checkAnswerTerminal(String userAnswer) {
        if (userAnswer.isEmpty()) return;
        String normalizedUser = toKatakana(Normalizer.normalize(userAnswer, Normalizer.Form.NFKC))
            .replaceAll("[^\u30A0-\u30FFー]", "");
        String normalizedCorrect = Normalizer.normalize(correctName, Normalizer.Form.NFC)
            .replaceAll("[^\u30A0-\u30FFー]", "");
        if (normalizedUser.equals(normalizedCorrect)) {
            System.out.println("正解！すごい！");
            // 2秒待って次の問題
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            loadNextQuestion();
        } else {
            hintLevel++;
            if (correctName.length() > 1) {
                int hintLength = Math.min(hintLevel, correctName.length() - 1);
                String hint = correctName.substring(0, hintLength);
                System.out.println("ちがうよ！ヒントは『" + hint + "』");
            } else {
                System.out.println("ちがうよ！もう一度考えてみて！");
            }
            System.out.print("こたえを入力: ");
            // こちらもStandardCharsets.UTF_8に統一
            java.util.Scanner sc = new java.util.Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);
            String nextAnswer = sc.nextLine().trim();
            checkAnswerTerminal(nextAnswer);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PokeQ app = new PokeQ();
            app.setVisible(true);
            app.loadNextQuestion();
        });
    }

    // ひらがな→カタカナ変換
    private static String toKatakana(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= '\u3041' && c <= '\u3096') {
                sb.append((char)(c + 0x60));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
