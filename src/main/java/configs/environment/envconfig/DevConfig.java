package configs.environment.envconfig;

import configs.environment.Environment;
import configs.environment.EnvironmentConfig;

public class DevConfig implements EnvironmentConfig {

    @Override
    public Environment getEnvironment() {
        return Environment.DEV;
    }

    @Override
    public String getBaseUrl() {
        return "https://web-frontend-staging.frontend-qaaccount.superuntest.net";
    }

    @Override
    public String getServerlistUrl() {
        return "https://dev-api.mobilejump.mobi";
    }
}
