import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

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

    // --- Gsonでマッピングするためのデータクラス(record) ---

    // レシピランキングAPIのレスポンス
    public record RecipeRankingResponse(List<Recipe> result, @SerializedName("error_description") String errorDescription) {}
    public record Recipe(
        int rank,
        String recipeTitle,
        String recipeDescription,
        String recipeUrl
    ) {}

    // カテゴリ一覧APIのレスポンス
    public record CategoryListResponse(CategoryResult result) {}
    public record CategoryResult(
        List<ApiCategory> large,
        List<ApiCategory> medium,
        List<ApiCategory> small
    ) {}
    public record ApiCategory(
        String categoryId,
        String categoryName,
        String parentCategoryId,
        String categoryUrl
    ) {}

    // --- クラスのフィールド ---
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private final String appId;

    // コンストラクタ
    public RakutenRecipeApiExample(String appId) {
        this.appId = appId;
    }

    /**
     * 指定されたカテゴリIDの楽天レシピランキングを取得します。
     * @param appId アプリケーションID
     * @param categoryId カテゴリID
     * @param categoryName カテゴリ名
     */
    public void getRecipeRanking(String categoryId, String categoryName) {
        String url = String.format(
            "https://app.rakuten.co.jp/services/api/Recipe/CategoryRanking/20170426?applicationId=%s&categoryId=%s&formatVersion=2",
            this.appId, categoryId
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        try {
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                RecipeRankingResponse rankingResponse = gson.fromJson(response.body(), RecipeRankingResponse.class);
                if (rankingResponse.result() != null && !rankingResponse.result().isEmpty()) {
                    System.out.println("\n--- 「" + categoryName + "」の人気レシピ トップ" + rankingResponse.result().size() + " ---");
                    rankingResponse.result().forEach(recipe -> {
                        System.out.println("\n【" + recipe.rank() + "位】 " + recipe.recipeTitle());
                        System.out.println("  説明: " + recipe.recipeDescription());
                        System.out.println("  URL: " + recipe.recipeUrl());
                    });
                } else if (rankingResponse.errorDescription() != null) {
                    System.err.println("APIからエラーが返されました: " + rankingResponse.errorDescription());
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
        } catch (JsonSyntaxException e) {
            System.err.println("レスポンスデータ(JSON)の解析に失敗しました: " + e.getMessage());
        }
    }

    /**
     * 楽天レシピカテゴリ一覧APIを呼び出し、カテゴリの階層構造データを返します。
     * @param appId アプリケーションID
     * @return カテゴリデータ
     */
    public CategoryData getCategoryData() {
        Map<String, Category> allCategories = new HashMap<>();
        Map<String, List<Category>> hierarchy = new HashMap<>();
        String url = "https://app.rakuten.co.jp/services/api/Recipe/CategoryList/20170426?applicationId=" + this.appId + "&formatVersion=2";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        try {
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                CategoryListResponse listResponse = gson.fromJson(response.body(), CategoryListResponse.class);
                if (listResponse != null && listResponse.result() != null) {
                    List<ApiCategory> allApiCategories = new ArrayList<>();
                    if (listResponse.result().large() != null) allApiCategories.addAll(listResponse.result().large());
                    if (listResponse.result().medium() != null) allApiCategories.addAll(listResponse.result().medium());
                    if (listResponse.result().small() != null) allApiCategories.addAll(listResponse.result().small());

                    for (ApiCategory apiCategory : allApiCategories) {
                        String id = apiCategory.categoryId();
                        String name = apiCategory.categoryName();
                        String parentId = (apiCategory.parentCategoryId() != null) ? apiCategory.parentCategoryId() : "0";

                        String categoryUrl = apiCategory.categoryUrl();
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
        } catch (IOException | InterruptedException e) {
            System.err.println("カテゴリ一覧の取得に失敗しました: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (JsonSyntaxException e) {
            System.err.println("カテゴリ一覧のJSON解析に失敗しました: " + e.getMessage());
        }
        return new CategoryData(allCategories, hierarchy);
    }

    public static void main(String[] args) {
        String APP_ID = "1021850898621735419";

        RakutenRecipeApiExample app = new RakutenRecipeApiExample(APP_ID);

        System.out.println("レシピカテゴリを取得中...");
        CategoryData categoryData = app.getCategoryData();
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
                            app.getRecipeRanking(currentCategory.fullIdForSearch, currentCategory.name);
                        } else {
                            System.out.println("トップレベルカテゴリは検索できません。");
                        }
                    } else {
                        System.out.println("不正な入力です。");
                    }
                } else {
                    // サブカテゴリがある場合の処理
                    // 各カテゴリ名の表示上の最大幅を計算（全角=2, 半角=1）
                    int maxNameWidth = 0;
                    for (Category child : children) {
                        int width = 0;
                        for (char c : child.name.toCharArray()) {
                            // 全角文字を2、半角文字を1としてカウントする簡易的な判定
                            width += (String.valueOf(c).getBytes(StandardCharsets.UTF_8).length > 1) ? 2 : 1;
                        }
                        if (width > maxNameWidth) {
                            maxNameWidth = width;
                        }
                    }

                    // 2列で整形して表示
                    final int columns = 2;
                    for (int i = 0; i < children.size(); i++) {
                        Category child = children.get(i);
                        // 番号(2桁)とカテゴリ名を出力
                        System.out.printf("  %2d: %s", i + 1, child.name);

                        // 行の最後の項目か、全項目の最後の項目でなければ、パディングを追加
                        if ((i + 1) % columns != 0 && i < children.size() - 1) {
                            int currentNameWidth = 0;
                            for (char c : child.name.toCharArray()) {
                                currentNameWidth += (String.valueOf(c).getBytes(StandardCharsets.UTF_8).length > 1) ? 2 : 1;
                            }
                            int padding = maxNameWidth - currentNameWidth + 4; // 4は項目間の固定スペース
                            System.out.print(" ".repeat(Math.max(0, padding)));
                        } else {
                            System.out.println(); // 行末または全項目の末尾で改行
                        }
                    }
                    System.out.println("\n[操作] 番号: 下へ | s 番号: 検索 | u: 上へ | q: 終了");
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
                                    app.getRecipeRanking(selectedCategory.fullIdForSearch, selectedCategory.name);
                                } else { // 番号のみでドリルダウン
                                    // ドリルダウンする前に、選択したカテゴリにサブカテゴリがあるかチェック
                                    List<Category> grandChildren = categoryData.hierarchy.getOrDefault(selectedCategory.id, Collections.emptyList());
                                    if (grandChildren.isEmpty()) {
                                        // サブカテゴリがない場合は、ドリルダウンせずに直接検索する
                                        System.out.println("\n「" + selectedCategory.name + "」にはサブカテゴリがありません。レシピを検索します...");
                                        app.getRecipeRanking(selectedCategory.fullIdForSearch, selectedCategory.name);
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
