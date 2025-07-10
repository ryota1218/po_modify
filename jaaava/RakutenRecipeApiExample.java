import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class RakutenRecipeApiExample {

    // カテゴリ情報を保持するためのクラス
    static class Category {
        final String id;
        final String name;
        final String parentId;
        final String fullIdForSearch; // API検索に使うID (例: "10-123")

        Category(String id, String name, String parentId, String fullIdForSearch) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
            this.fullIdForSearch = fullIdForSearch;
        }
    }

    // APIから取得したカテゴリデータをまとめて保持するためのクラス
    static class CategoryData {
        final Map<String, Category> allCategories; // IDからカテゴリ情報を引くためのマップ
        final Map<String, List<Category>> hierarchy; // 親IDから子のリストを引くためのマップ

        CategoryData(Map<String, Category> allCategories, Map<String, List<Category>> hierarchy) {
            this.allCategories = allCategories;
            this.hierarchy = hierarchy;
        }
    }

    /**
     * 指定されたカテゴリIDの楽天レシピランキングを取得します。
     * @param appId アプリケーションID
     * @param categoryId カテゴリID
     * @param categoryName カテゴリ名
     */
    public static void getRecipeRanking(String appId, String categoryId, String categoryName) {
        HttpClient client = HttpClient.newHttpClient();
        String url = String.format(
            "https://app.rakuten.co.jp/services/api/Recipe/CategoryRanking/20170426?applicationId=%s&categoryId=%s&formatVersion=2",
            appId, categoryId
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject data = new JSONObject(response.body());
                if (data.has("result")) {
                    JSONArray recipesJson = data.getJSONArray("result");
                    System.out.println("\n--- 「" + categoryName + "」の人気レシピ トップ" + recipesJson.length() + " ---");

                    for (int i = 0; i < recipesJson.length(); i++) {
                        JSONObject recipeJson = recipesJson.getJSONObject(i);

                        System.out.println("\n【" + recipeJson.getString("rank") + "位】 " + recipeJson.getString("recipeTitle"));
                        System.out.println("  説明: " + recipeJson.getString("recipeDescription"));
                        System.out.println("  URL: " + recipeJson.getString("recipeUrl"));
                    }
                    return;
                } else if (data.has("error")) {
                     System.err.println("APIからエラーが返されました: " + data.getString("error_description"));
                } else {
                    System.err.println("レシピが見つかりませんでした。");
                }
            } else {
                System.err.println("エラーが発生しました。ステータスコード: " + response.statusCode());
                System.err.println("レスポンス: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("リクエスト中にエラーが発生しました: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (org.json.JSONException e) {
            System.err.println("レスポンスデータ(JSON)の解析に失敗しました: " + e.getMessage());
        }
    }

    /**
     * 楽天レシピカテゴリ一覧APIを呼び出し、カテゴリの階層構造データを返します。
     * @param appId アプリケーションID
     * @return カテゴリデータ
     */
    public static CategoryData getCategoryData(String appId) {
        Map<String, Category> allCategories = new HashMap<>();
        Map<String, List<Category>> hierarchy = new HashMap<>();
        HttpClient client = HttpClient.newHttpClient();
        String url = "https://app.rakuten.co.jp/services/api/Recipe/CategoryList/20170426?applicationId=" + appId + "&formatVersion=2";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject data = new JSONObject(response.body());
                if (data.has("result")) {
                    String[] categoryLevels = {"large", "medium", "small"};
                    for (String level : categoryLevels) {
                        if (data.getJSONObject("result").has(level)) {
                            JSONArray categories = data.getJSONObject("result").getJSONArray(level);
                            for (int i = 0; i < categories.length(); i++) {
                                JSONObject categoryJson = categories.getJSONObject(i);
                                
                                String id = String.valueOf(categoryJson.get("categoryId"));
                                String name = categoryJson.getString("categoryName");
                                // largeカテゴリにはparentCategoryIdが存在しないため、optStringで安全に取得する
                                String parentId = categoryJson.optString("parentCategoryId", "0");
                                
                                String categoryUrl = categoryJson.getString("categoryUrl");
                                int queryIndex = categoryUrl.indexOf('?');
                                if (queryIndex != -1) {
                                    categoryUrl = categoryUrl.substring(0, queryIndex);
                                }
                                if (categoryUrl.endsWith("/")) {
                                    categoryUrl = categoryUrl.substring(0, categoryUrl.length() - 1);
                                }
                                String fullIdForSearch = categoryUrl.substring(categoryUrl.lastIndexOf('/') + 1);

                                Category category = new Category(id, name, parentId, fullIdForSearch);
                                allCategories.put(id, category);
                                hierarchy.computeIfAbsent(parentId, k -> new ArrayList<>()).add(category);
                            }
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("カテゴリ一覧の取得に失敗しました: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (org.json.JSONException e) {
            System.err.println("カテゴリ一覧のJSON解析に失敗しました: " + e.getMessage());
        }
        return new CategoryData(allCategories, hierarchy);
    }

    public static void main(String[] args) {
        String APP_ID = "1021850898621735419";

        System.out.println("レシピカテゴリを取得中...");
        CategoryData categoryData = getCategoryData(APP_ID);
        if (categoryData.allCategories.isEmpty()) {
            System.out.println("カテゴリの取得に失敗しました。");
            return;
        }
        System.out.println("カテゴリの取得が完了しました。");

        // 仮想的なトップレベルから開始
        Category currentCategory = new Category("0", "トップレベル", null, null);

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n--- 現在のカテゴリ: " + currentCategory.name + " ---");
                List<Category> children = categoryData.hierarchy.getOrDefault(currentCategory.id, Collections.emptyList());
                
                if (children.isEmpty()) {
                    // サブカテゴリがない場合の処理
                    System.out.println("このカテゴリにはサブカテゴリがありません。");
                    System.out.println("\n[操作] s: このカテゴリを検索 | u: 上へ | q: 終了");
                    System.out.print("> ");
                    String input = scanner.nextLine().trim();

                    if (input.equalsIgnoreCase("q")) {
                        System.out.println("プログラムを終了します。");
                        break;
                    } else if (input.equalsIgnoreCase("u")) {
                        if (currentCategory.parentId != null) {
                            currentCategory = categoryData.allCategories.get(currentCategory.parentId);
                        } else {
                            System.out.println("すでにトップレベルです。");
                        }
                    } else if (input.equalsIgnoreCase("s")) {
                        if (currentCategory.fullIdForSearch != null) {
                            System.out.println("\n「" + currentCategory.name + "」を検索します...");
                            getRecipeRanking(APP_ID, currentCategory.fullIdForSearch, currentCategory.name);
                        } else {
                            System.out.println("トップレベルカテゴリは検索できません。");
                        }
                    } else {
                        System.out.println("不正な入力です。");
                    }
                } else {
                    // サブカテゴリがある場合の処理
                    for (int i = 0; i < children.size(); i++) {
                        System.out.printf("  %d: %s  ", i + 1, children.get(i).name);
                        if ((i + 1) % 2 == 0) {
                            System.out.println(); // 2つごとに改行
                        }
                    }
                    if (children.size() % 2 != 0) {
                        System.out.println(); // 奇数個の場合、最後に改行
                    }
                    System.out.println("\n[操作] 番号: ドリルダウン | s <番号>: 検索 | u: 上へ | q: 終了");
                    System.out.print("> ");
                    String input = scanner.nextLine().trim();

                    if (input.equalsIgnoreCase("q")) {
                        System.out.println("プログラムを終了します。");
                        break;
                    } else if (input.equalsIgnoreCase("u")) {
                        if (currentCategory.parentId != null) {
                            currentCategory = categoryData.allCategories.get(currentCategory.parentId);
                        } else {
                            System.out.println("すでにトップレベルです。");
                        }
                    } else {
                        String command = input.startsWith("s ") ? "s" : "drill";
                        String numberStr = input.startsWith("s ") ? input.substring(2).trim() : input;

                        try {
                            int choice = Integer.parseInt(numberStr);
                            if (choice > 0 && choice <= children.size()) {
                                Category selectedCategory = children.get(choice - 1);
                                if (command.equals("s")) {
                                    // s <番号> で検索
                                    System.out.println("\n「" + selectedCategory.name + "」を検索します...");
                                    getRecipeRanking(APP_ID, selectedCategory.fullIdForSearch, selectedCategory.name);
                                } else { // 番号のみでドリルダウン
                                    // ドリルダウンする前に、選択したカテゴリにサブカテゴリがあるかチェック
                                    List<Category> grandChildren = categoryData.hierarchy.getOrDefault(selectedCategory.id, Collections.emptyList());
                                    if (grandChildren.isEmpty()) {
                                        // サブカテゴリがない場合は、ドリルダウンせずに直接検索する
                                        System.out.println("\n「" + selectedCategory.name + "」にはサブカテゴリがありません。レシピを検索します...");
                                        getRecipeRanking(APP_ID, selectedCategory.fullIdForSearch, selectedCategory.name);
                                    } else {
                                        // サブカテゴリがある場合は、通常通りドリルダウン
                                        currentCategory = selectedCategory;
                                    }
                                }
                            } else {
                                System.out.println("無効な番号です。");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("不正な入力です。");
                        }
                    }
                }
            }
        }
    }
}
