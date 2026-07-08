package io.ic.starter.starterenv;

public record Environment(
        String databaseUrl,
        int port,
        String cookieSecret,
        String openAiApiKey
) {
    public static Environment fromEnv() {
        return new Environment(
                read("DATABASE_URL"),
                readInt("PORT", 8888),
                read("COOKIE_SECRET", "local-dev-secret"),
                read("OPENAI_API_KEY", "")
        );
    }

    private static String read(String name) {
        var value = read(name, null);
        if (value == null) {
            throw new RuntimeException("Missing environment variable: " + name);
        }
        return value;
    }

    private static String read(String name, String defaultValue) {
        return System.getenv(name) != null ? System.getenv(name) : defaultValue;
    }

    private static int readInt(String name, int defaultValue) {
        try {
            return Integer.parseInt(read(name, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid environment variable: " + name);
        }
    }
}
