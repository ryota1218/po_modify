import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class INaturalistSwingSlideshow extends JFrame {
    private static final String API_URL = "https://api.inaturalist.org/v1/observations";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private List<String> imageUrls = new ArrayList<>();
    private int currentIndex = 0;
    private JLabel imageLabel = new JLabel();
    private JLabel infoLabel = new JLabel();
    private Timer timer;

    public INaturalistSwingSlideshow() {
        setTitle("iNaturalist 画像スライドショー");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        JTextField searchField = new JTextField(20);
        JButton searchBtn = new JButton("検索");
        topPanel.add(new JLabel("生物名: "));
        topPanel.add(searchField);
        topPanel.add(searchBtn);
        add(topPanel, BorderLayout.NORTH);

        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        add(imageLabel, BorderLayout.CENTER);
        infoLabel.setHorizontalAlignment(JLabel.CENTER);
        add(infoLabel, BorderLayout.SOUTH);

        searchBtn.addActionListener(e -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) {
                JOptionPane.showMessageDialog(this, "生物名を入力してください。", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                searchAndShowImages(query);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "検索中にエラーが発生しました。", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void searchAndShowImages(String query) throws IOException, InterruptedException {
        imageUrls.clear();
        currentIndex = 0;
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String requestUrl = API_URL + "?q=" + encodedQuery + "&per_page=10";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject obj = gson.fromJson(response.body(), JsonObject.class);
            JsonArray results = obj.getAsJsonArray("results");
            if (results == null || results.size() == 0) {
                imageLabel.setIcon(null);
                infoLabel.setText("該当する観察データが見つかりませんでした。");
                if (timer != null) timer.stop();
                return;
            }
            List<String> infoList = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JsonObject obs = results.get(i).getAsJsonObject();
                String speciesGuess = (obs.has("species_guess") && !obs.get("species_guess").isJsonNull()) ? obs.get("species_guess").getAsString() : "-";
                String place = (obs.has("place_guess") && !obs.get("place_guess").isJsonNull()) ? obs.get("place_guess").getAsString() : "-";
                String observedOn = (obs.has("observed_on") && !obs.get("observed_on").isJsonNull()) ? obs.get("observed_on").getAsString() : "-";
                if (obs.has("photos") && obs.get("photos").isJsonArray()) {
                    JsonArray photos = obs.getAsJsonArray("photos");
                    for (int j = 0; j < photos.size(); j++) {
                        JsonObject photoObj = photos.get(j).getAsJsonObject();
                        if (photoObj.has("url") && !photoObj.get("url").isJsonNull()) {
                            String url = photoObj.get("url").getAsString();
                            // サムネイルURLをlarge.jpgに置換して高解像度画像を取得
                            if (url.endsWith("square.jpg")) {
                                url = url.replace("square.jpg", "large.jpg");
                            } else if (url.endsWith("small.jpg")) {
                                url = url.replace("small.jpg", "large.jpg");
                            } else if (url.endsWith("medium.jpg")) {
                                url = url.replace("medium.jpg", "large.jpg");
                            }
                            imageUrls.add(url);
                            infoList.add("種名: " + speciesGuess + " / 場所: " + place + " / 観察日: " + observedOn);
                        }
                    }
                }
            }
            if (imageUrls.isEmpty()) {
                imageLabel.setIcon(null);
                infoLabel.setText("画像が見つかりませんでした。");
                if (timer != null) timer.stop();
                return;
            }
            // スライドショー開始
            showImage(infoList);
            if (timer != null) timer.stop();
            timer = new Timer(2500, new ActionListener() {
                int idx = 0;
                @Override
                public void actionPerformed(ActionEvent e) {
                    currentIndex = (currentIndex + 1) % imageUrls.size();
                    showImage(infoList);
                }
            });
            timer.start();
        } else {
            imageLabel.setIcon(null);
            infoLabel.setText("APIエラー: " + response.statusCode());
            if (timer != null) timer.stop();
        }
    }

    private void showImage(List<String> infoList) {
        try {
            ImageIcon icon = new ImageIcon(new java.net.URL(imageUrls.get(currentIndex)));
            Image img = icon.getImage().getScaledInstance(400, 300, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(img));
            infoLabel.setText(infoList.get(currentIndex));
        } catch (Exception e) {
            imageLabel.setIcon(null);
            infoLabel.setText("画像の取得に失敗しました。");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            INaturalistSwingSlideshow frame = new INaturalistSwingSlideshow();
            frame.setVisible(true);
        });
    }
}
