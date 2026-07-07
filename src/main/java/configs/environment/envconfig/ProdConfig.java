package configs.environment.envconfig;

import configs.environment.Environment;
import configs.environment.EnvironmentConfig;

public class ProdConfig implements EnvironmentConfig {

    @Override
    public Environment getEnvironment() {
        return Environment.PROD;
    }

    @Override
    public String getBaseUrl() {
        return "https://account.vpnsuper.com";
    }

    @Override
    public String getServerlistUrl() {
        return "https://prod-api.mobilejump.mobi";
    }
}
