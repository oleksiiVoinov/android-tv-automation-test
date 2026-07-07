package configs.environment;

import configs.environment.envconfig.DevConfig;
import configs.environment.envconfig.ProdConfig;

public class EnvironmentFactory {

    public EnvironmentConfig getConfig(String environment) {
        System.out.println("🌎 Environment IS " + environment.toUpperCase().trim());
        return switch (environment.toUpperCase().trim()) {
            case "DEV", "STAGING" -> new DevConfig();
            case "PROD", "PRODUCTION" -> new ProdConfig();
            default -> throw new IllegalStateException(
                    "Unexpected environment '" + environment + "' (expected 'dev' or 'prod')");
        };
    }
}
