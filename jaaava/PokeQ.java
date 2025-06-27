import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;

public class PokeQ extends JFrame {

    // --- UI Components ---
    private JLabel pokemonImageLabel;
    private JLabel instructionLabel;
    private JTextField answerField;
    private JButton submitButton;
    private JLabel resultLabel;
    private JProgressBar progressBar;

    // --- Game Logic ---
    private final PokeApiClient apiClient = new PokeApiClient();
    private String correctName;
    private int hintLevel;

    public PokeQ() {
        // --- Window Setup ---
        setTitle("ポケモン名前当てクイズ (Swing版)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 500);
        setLocationRelativeTo(null); // Center the window

        // --- UI Initialization ---
        instructionLabel = new JLabel("このポケモンの名前はなーんだ？", SwingConstants.CENTER);
        instructionLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        pokemonImageLabel = new JLabel();
        pokemonImageLabel.setPreferredSize(new Dimension(300, 300));
        pokemonImageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true); // Loading animation
        progressBar.setVisible(false);

        answerField = new JTextField(15);
        answerField.setFont(new Font("SansSerif", Font.PLAIN, 16));

        submitButton = new JButton("こたえあわせ");
        submitButton.setFont(new Font("SansSerif", Font.PLAIN, 16));

        resultLabel = new JLabel(" ", SwingConstants.CENTER);
        resultLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        // --- Layout (using GridBagLayout for flexibility) ---
        Container contentPane = getContentPane();
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER; // End of row
        gbc.anchor = GridBagConstraints.CENTER;

        // instructionLabel はデフォルトの weighty=0.0 でOK
        contentPane.add(instructionLabel, gbc);

        // pokemonImageLabel に垂直方向の重みを与え、ウィンドウのリサイズを吸収させる
        gbc.weighty = 1.0;
        contentPane.add(pokemonImageLabel, gbc);
        gbc.weighty = 0.0; // 他のコンポーネントのためにリセット

        contentPane.add(progressBar, gbc);
        // answerField は水平方向に埋めるように設定
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(answerField, gbc);
        gbc.fill = GridBagConstraints.NONE; // 他のコンポーネントのためにリセット
        gbc.insets = new Insets(10, 10, 1, 10); // top, left, bottom, right
        contentPane.add(submitButton, gbc);
        gbc.insets = new Insets(10, 10, 10, 10); // 次のコンポーネントのために余白を元に戻す
        contentPane.add(resultLabel, gbc);

        // --- Event Listeners ---
        ActionListener checkAnswerAction = e -> checkAnswer();
        submitButton.addActionListener(checkAnswerAction);
        answerField.addActionListener(checkAnswerAction); // For Enter key

        // --- Start Game ---
        loadNextQuestion();
    }

    private void loadNextQuestion() {
        setLoadingState(true);
        this.hintLevel = 0; // 新しい問題のためにヒントレベルをリセット

        // SwingWorker for background API calls
        SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
            private String fetchedName;

            @Override
            protected ImageIcon doInBackground() throws Exception {
                // 1. Fetch Pokémon data
                PokemonData.PokemonResponse pokemon = apiClient.fetchRandomPokemon()
                        .orElseThrow(() -> new IOException("ポケモンの取得に失敗しました。"));

                // 2. Fetch Japanese name
                PokemonData.PokemonSpeciesResponse species = apiClient.fetchPokemonSpecies(pokemon.species().url())
                        .orElseThrow(() -> new IOException("ポケモンの日本語名の取得に失敗しました。"));

                fetchedName = findJapaneseName(species)
                        .orElseThrow(() -> new IOException("ポケモンの日本語名が見つかりませんでした。"));

                // 3. Load image
                ImageIcon originalIcon = new ImageIcon(new URL(pokemon.sprites().frontDefault()));
                Image originalImage = originalIcon.getImage();

                // 画像を新しいサイズにスケーリングする
                int newSize = 300;
                Image scaledImage = originalImage.getScaledInstance(newSize, newSize, Image.SCALE_SMOOTH);

                return new ImageIcon(scaledImage);
            }

            @Override
            protected void done() {
                try {
                    ImageIcon image = get(); // Get result from doInBackground
                    correctName = fetchedName;
                    pokemonImageLabel.setIcon(image);
                    setLoadingState(false);
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause();
                    resultLabel.setForeground(Color.RED);
                    resultLabel.setText("エラー: " + cause.getMessage());
                    setLoadingState(false);
                }
            }
        };

        worker.execute();
    }

    private void checkAnswer() {
        String userAnswer = answerField.getText();
        if (userAnswer.isBlank())
            return;

        if (userAnswer.equals(correctName)) { // 正解の場合
            resultLabel.setForeground(Color.BLUE);
            resultLabel.setText("正解！すごい！");

            // 2秒待ってから次の問題へ進む
            answerField.setEditable(false); // 入力不可にする
            submitButton.setEnabled(false); // ボタンを無効にする
            Timer timer = new Timer(2000, e -> loadNextQuestion());
            timer.setRepeats(false); // 一度だけ実行
            timer.start();
        } else { // 不正解の場合
            hintLevel++; // ヒントレベルを上げる
            resultLabel.setForeground(Color.ORANGE);

            // 名前の長さが1より大きい場合のみ、ヒントを生成する
            if (correctName.length() > 1) {
                // 表示するヒントの長さを計算（最大でも名前の長さ-1）
                int hintLength = Math.min(hintLevel, correctName.length() - 1);
                String hint = correctName.substring(0, hintLength);
                resultLabel.setText("ちがうよ！ヒントは「" + hint + "」");
            } else {
                resultLabel.setText("ちがうよ！もう一度考えてみて！");
            }
            answerField.setText(""); // 入力欄をクリア
            answerField.requestFocusInWindow(); // 入力欄にフォーカスを戻す
        }
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisible(isLoading);
        pokemonImageLabel.setVisible(!isLoading);
        answerField.setEditable(!isLoading);
        submitButton.setEnabled(!isLoading);

        if (!isLoading) {
            answerField.setText("");
            resultLabel.setText(" ");
            answerField.requestFocusInWindow();
        }
    }

    private static Optional<String> findJapaneseName(PokemonData.PokemonSpeciesResponse species) {
        return species.names().stream()
                .filter(n -> "ja-Hrkt".equals(n.language().name()))
                .map(PokemonData.NameEntry::name)
                .findFirst()
                .or(() -> species.names().stream()
                        .filter(n -> "ja".equals(n.language().name()))
                        .map(PokemonData.NameEntry::name)
                        .findFirst());
    }

    public static void main(String[] args) {
        // Ensure UI is created on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            new PokeQ().setVisible(true);
        });
    }

    // --- Nested classes for API client and data models (same as before) ---
    static class PokeApiClient {
        private static final String API_BASE_URL = "https://pokeapi.co/api/v2/";
        private final HttpClient client = HttpClient.newHttpClient();
        private final Gson gson = new Gson();
        private final Random random = new Random();

        public Optional<PokemonData.PokemonResponse> fetchRandomPokemon() throws IOException, InterruptedException {
            int pokemonCount = getPokemonCount().orElse(1025);
            int randomId = random.nextInt(pokemonCount) + 1;
            return fetch(API_BASE_URL + "pokemon/" + randomId, PokemonData.PokemonResponse.class);
        }

        public Optional<PokemonData.PokemonSpeciesResponse> fetchPokemonSpecies(String speciesUrl)
                throws IOException, InterruptedException {
            return fetch(speciesUrl, PokemonData.PokemonSpeciesResponse.class);
        }

        private Optional<Integer> getPokemonCount() throws IOException, InterruptedException {
            return fetch(API_BASE_URL + "pokemon-species?limit=1", PokemonData.PokemonSpeciesListResponse.class)
                    .map(PokemonData.PokemonSpeciesListResponse::count);
        }

        private <T> Optional<T> fetch(String url, Class<T> classOfT) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.of(gson.fromJson(response.body(), classOfT));
            }
            return Optional.empty();
        }
    }

    static class PokemonData {
        public record PokemonResponse(Sprites sprites, Species species) {
        }

        public record Sprites(@SerializedName("front_default") String frontDefault) {
        }

        public record Species(String url) {
        }

        public record PokemonSpeciesResponse(List<NameEntry> names) {
        }

        public record NameEntry(String name, Language language) {
        }

        public record Language(String name) {
        }

        public record PokemonSpeciesListResponse(int count) {
        }
    }
}
