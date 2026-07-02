package configs;

public final class RuntimeConfig {
    private RuntimeConfig() {
    }

    public static String getRequired(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required runtime property: " + key);
        }
        return value;
    }

    public static String getOptional(String key, String defaultValue) {
        String value = System.getProperty(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static String getOptional(String key) {
        String value = System.getProperty(key);
        return (value == null || value.isBlank()) ? null : value;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        return (value == null || value.isBlank()) ? defaultValue : Boolean.parseBoolean(value);
    }

    public static <E extends Enum<E>> E getEnum(String key, Class<E> enumClass, E defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid value for runtime property '" + key + "': " + value, e);
        }
    }
}
