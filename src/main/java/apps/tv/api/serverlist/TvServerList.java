package apps.tv.api.serverlist;

import com.google.gson.Gson;
import configs.RuntimeConfig;
import driver.TestContext;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.openqa.selenium.NoSuchElementException;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;

/**
 * Server-list provider for the Android TV app (Server list API v7, {@code /be-data/v7/server_list}).
 * <p>
 * TV specifics vs the phone client: the {@code app} query param is the package with a {@code _tvos}
 * suffix, and TV offers <b>VIP servers only</b> — so this exposes only {@link #vipServers}.
 * The response is encrypted; it's decoded with the {@code serverListKey} (see {@link ResponseDecoder}).
 * <p>
 * Config keys (System props / local.properties): {@code serverListKey} (required), {@code region}
 * (default {@code UA}). Base URL comes from the environment ({@code dev-api.mobilejump.mobi}).
 */
public class TvServerList {

    private static final String TVOS_APP_SUFFIX = "_tvos";

    public final List<ServerV7> vipServers;

    public TvServerList(TestContext testContext) throws Exception {
        String response = fetchAndDecode(testContext);
        this.vipServers = parseVipServers(response).stream()
                .filter(ServerV7::hasAllRequiredFields)
                .peek(server -> server.setStatus("vip"))
                .collect(Collectors.toList());
        removeCountries("Kazakhstan");
        removeCountries("Russia");
    }

    private static final String DEFAULT_SERVERLIST_URL = "https://dev-api.mobilejump.mobi";

    private String fetchAndDecode(TestContext testContext) throws Exception {
        RestAssured.baseURI = RuntimeConfig.getOptional("serverlistUrl", DEFAULT_SERVERLIST_URL);
        String region = RuntimeConfig.getOptional("region", "UA");
        String key = RuntimeConfig.getRequired("serverListKey");
        String tvApp = testContext.getDevice().app.appPackage + TVOS_APP_SUFFIX;

        System.out.println("✅ TV server list: region=" + region + ", app=" + tvApp);

        Response response = requestServerList(region, tvApp);
        if (response.getBody().asString().isEmpty()) {
            System.out.println("Server list response body is empty; retrying in 30 seconds...");
            TimeUnit.SECONDS.sleep(30);
            response = requestServerList(region, tvApp);
            if (response.getBody().asString().isEmpty()) {
                throw new IllegalStateException("Server returned an empty response body after two attempts");
            }
        }
        return new ResponseDecoder(response, key).decode();
    }

    private Response requestServerList(String region, String tvApp) {
        return given()
                .queryParam("platform", "android")
                .queryParam("vip", "true")
                .queryParam("region", region)
                .queryParam("app", tvApp)
                .when()
                .get("/be-data/v7/server_list")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    private List<ServerV7> parseVipServers(String response) {
        ServerResponseV7 parsed = new Gson().fromJson(response, ServerResponseV7.class);
        List<ServerV7> servers = parsed != null ? parsed.getVipServers() : null;
        return servers != null ? servers : List.of();
    }

    public void removeCountries(String blockCountry) {
        vipServers.removeIf(server -> blockCountry.equals(server.getCountryName()));
    }

    /** Random VIP server, optionally restricted to countries whose name contains one of {@code includeCountries}. */
    public ServerV7 getRandomServer(List<String> includeCountries) {
        if (vipServers.isEmpty()) {
            throw new NoSuchElementException("No VIP servers available");
        }
        List<ServerV7> pool = (includeCountries == null || includeCountries.isEmpty())
                ? vipServers
                : vipServers.stream()
                .filter(s -> s.getCountryName() != null)
                .filter(s -> includeCountries.stream().anyMatch(i -> s.getCountryName().contains(i)))
                .toList();

        if (pool.isEmpty()) {
            throw new NoSuchElementException("No VIP servers matched " + includeCountries);
        }

        // Pick a random country from the pool, then a random server in it.
        List<String> countries = pool.stream().map(ServerV7::getCountryName).distinct().toList();
        String country = countries.get(new Random().nextInt(countries.size()));
        List<ServerV7> countryServers = pool.stream()
                .filter(s -> country.equals(s.getCountryName()))
                .toList();

        ServerV7 server = countryServers.get(new Random().nextInt(countryServers.size()));
        System.out.println("🎯 Picked VIP server: " + server);
        return server;
    }

    public ServerV7 getRandomServer() {
        return getRandomServer(List.of());
    }

    /**
     * Random VIP server excluding US clusters. US is special-cased in the UI as "US - City", which
     * diverges from the plain country name — exclude it for country-name-based flows like search.
     */
    public ServerV7 getRandomNonUsServer() {
        List<ServerV7> pool = vipServers.stream().filter(s -> !s.isUS()).collect(Collectors.toList());
        if (pool.isEmpty()) {
            throw new NoSuchElementException("No non-US VIP servers available");
        }
        List<String> countries = pool.stream().map(ServerV7::getCountryName).distinct().toList();
        String country = countries.get(new Random().nextInt(countries.size()));
        List<ServerV7> countryServers = pool.stream()
                .filter(s -> country.equals(s.getCountryName()))
                .toList();
        ServerV7 server = countryServers.get(new Random().nextInt(countryServers.size()));
        System.out.println("🎯 Picked non-US VIP server: " + server);
        return server;
    }

    /** Finds a server by its UI alias name (e.g. {@code "Belgium - 1"}). */
    public ServerV7 getServerByAlias(String aliasName) {
        return vipServers.stream()
                .filter(s -> aliasName != null && aliasName.equals(s.getAliasName()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Server not found: " + aliasName));
    }
}
