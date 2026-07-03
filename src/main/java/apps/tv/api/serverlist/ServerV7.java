package apps.tv.api.serverlist;

import java.util.List;
import java.util.Objects;

/**
 * Server item of the Server list API v7 response. v7 carries only machine data
 * ({@code location.country} ISO code, optional city, {@code server_number}, {@code type}, {@code host},
 * {@code password}, {@code load}) + a {@code services} array. Human-readable names are reconstructed
 * from {@link ServerListLocalization} so they match the UI exactly.
 */
public class ServerV7 {

    private static final String COUNTRY_CODE_US = "US";

    private String status;
    private Location location;
    private String type;
    private String server_number;
    private String host;
    private String password;
    private int load;
    private List<ServiceV7> services;

    public static class Location {
        private String country;
        private String city;

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }

    /** True if all required fields (location.country, host, password) are present. */
    public boolean hasAllRequiredFields() {
        return location != null && isNonBlank(location.getCountry())
                && isNonBlank(host) && isNonBlank(password);
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** ISO 3166-1 alpha-2 country code, e.g. {@code "ES"}, {@code "US"}. */
    public String getCountry() {
        return location != null ? location.getCountry() : null;
    }

    public String getCity() {
        return location != null ? location.getCity() : "";
    }

    public boolean isUS() {
        return COUNTRY_CODE_US.equals(getCountry());
    }

    public String getLocalizedCountryName() {
        return ServerListLocalization.countryName(getCountry());
    }

    public String getLocalizedCityName() {
        return ServerListLocalization.cityName(getCountry(), getCity());
    }

    /**
     * Cluster / country label as shown in the UI (mirrors {@code Server.localizedCountryNameCompat}):
     * US with a city -> {@code "US - <City>"}, otherwise the country name; non-blank {@code type} appended.
     */
    public String getCountryName() {
        String cityName = getLocalizedCityName();
        StringBuilder sb = new StringBuilder();
        if (isUS() && isNonBlank(cityName)) {
            sb.append(getCountry()).append(" - ").append(cityName);
        } else {
            sb.append(getLocalizedCountryName());
        }
        if (isNonBlank(type)) {
            sb.append(" (").append(type).append(')');
        }
        return sb.toString();
    }

    /**
     * Per-server name as shown in the UI (mirrors {@code Server.localizedAliasName}):
     * most specific available name (city, then country) + {@code " - <server_number>"}.
     */
    public String getAliasName() {
        String name = firstNonBlank(
                getLocalizedCityName(),
                getCity(),
                getLocalizedCountryName(),
                getCountry());
        StringBuilder sb = new StringBuilder(name == null ? "" : name);
        if (isNonBlank(server_number)) {
            sb.append(" - ").append(server_number);
        }
        return sb.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (isNonBlank(v)) {
                return v;
            }
        }
        return "";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getServerNumber() {
        return server_number;
    }

    public void setServerNumber(String server_number) {
        this.server_number = server_number;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getLoad() {
        return load;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    public List<ServiceV7> getServices() {
        return services;
    }

    public void setServices(List<ServiceV7> services) {
        this.services = services;
    }

    public boolean hasV2Services() {
        return services != null && services.stream().anyMatch(ServiceV7::isV2Instance);
    }

    @Override
    public String toString() {
        return "status = '" + status + '\'' +
                ", country = '" + getCountryName() + '\'' +
                ", server = '" + getAliasName() + '\'' +
                ", host = '" + host + '\'';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerV7 server = (ServerV7) o;
        return Objects.equals(getCountryName(), server.getCountryName()) &&
                Objects.equals(getAliasName(), server.getAliasName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, getCountryName(), getAliasName());
    }
}
