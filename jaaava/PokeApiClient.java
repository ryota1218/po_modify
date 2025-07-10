import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class PokeApiClient {
    private static final String API_BASE_URL = "https://pokeapi.co/api/v2/";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private final Random random = new Random();

    public Optional<PokemonData.PokemonResponse> fetchRandomPokemon() throws IOException, InterruptedException {
        int pokemonCount = getPokemonCount().orElse(1025);
        int randomId = random.nextInt(pokemonCount) + 1;
        return fetch(API_BASE_URL + "pokemon/" + randomId, PokemonData.PokemonResponse.class);
    }
    public Optional<PokemonData.PokemonSpeciesResponse> fetchPokemonSpecies(String speciesUrl) throws IOException, InterruptedException {
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
    // --- データモデル ---
    public static class PokemonData {
        public record PokemonResponse(Sprites sprites, Species species) {}
        public record Sprites(@SerializedName("front_default") String frontDefault) {}
        public record Species(String url) {}
        public record PokemonSpeciesResponse(List<NameEntry> names) {}
        public record NameEntry(String name, Language language) {}
        public record Language(String name) {}
        public record PokemonSpeciesListResponse(int count) {}
    }
}
