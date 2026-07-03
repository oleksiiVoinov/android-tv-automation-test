package apps.tv.api.serverlist;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * English-only mirror of the app's server list localization (v7 no longer returns human-readable
 * names). Loads {@code localization/server_list_localization.json} from the classpath and exposes
 * the English values, so server names match what the user sees in the UI.
 * Re-copy the file from the app repo when Ops add new countries.
 */
public final class ServerListLocalization {

    private static final String RESOURCE_PATH = "localization/server_list_localization.json";
    private static final String LANGUAGE_CODE_ENGLISH = "en";

    private static volatile Map<String, String> englishNames;

    private ServerListLocalization() {
    }

    public static String countryName(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return countryCode;
        }
        return names().getOrDefault(composeKey(countryCode, null), countryCode);
    }

    public static String cityName(String countryCode, String city) {
        if (city == null || city.isBlank()) {
            return "";
        }
        return names().getOrDefault(composeKey(countryCode, city), city);
    }

    /** Mirrors the app's {@code composeKey}: {@code "COUNTRY::CITY::"}. */
    private static String composeKey(String country, String city) {
        String delimiter = "::";
        return (country == null ? "" : country) + delimiter + (city == null ? "" : city) + delimiter;
    }

    private static Map<String, String> names() {
        Map<String, String> local = englishNames;
        if (local == null) {
            synchronized (ServerListLocalization.class) {
                local = englishNames;
                if (local == null) {
                    local = load();
                    englishNames = local;
                }
            }
        }
        return local;
    }

    private static Map<String, String> load() {
        Map<String, String> result = new HashMap<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                System.out.println("⚠️ Server list localization file not found on classpath: " + RESOURCE_PATH
                        + " — falling back to raw ISO codes / city names.");
                return result;
            }
            JsonObject root = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject strings = root.getAsJsonObject("strings");
            if (strings == null) {
                return result;
            }
            for (Map.Entry<String, JsonElement> entry : strings.entrySet()) {
                String value = englishValue(entry.getValue());
                if (value != null) {
                    result.put(entry.getKey(), value);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Failed to read server list localization: " + e.getMessage());
        }
        return result;
    }

    /** Extracts {@code localizations.en.stringUnit.value}. */
    private static String englishValue(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject localizations = element.getAsJsonObject().getAsJsonObject("localizations");
        if (localizations == null) {
            return null;
        }
        JsonObject en = localizations.getAsJsonObject(LANGUAGE_CODE_ENGLISH);
        if (en == null) {
            return null;
        }
        JsonObject stringUnit = en.getAsJsonObject("stringUnit");
        if (stringUnit == null || stringUnit.get("value") == null || stringUnit.get("value").isJsonNull()) {
            return null;
        }
        return stringUnit.get("value").getAsString();
    }
}
