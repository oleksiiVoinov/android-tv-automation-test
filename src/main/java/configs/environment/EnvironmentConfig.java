package configs.environment;

/** Per-environment backend endpoints — mirrors the phone framework's EnvironmentConfig. */
public interface EnvironmentConfig {
    Environment getEnvironment();

    /** Account backend (device-code sign-in). */
    String getBaseUrl();

    /** Server-list API base. */
    String getServerlistUrl();
}
