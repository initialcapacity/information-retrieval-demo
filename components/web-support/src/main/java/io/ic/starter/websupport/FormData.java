package io.ic.starter.websupport;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class FormData {
    private final Map<String, String> formData = new HashMap<>();

    public static FormData parse(String formData) {
        var result = new FormData();
        for (var entry : formData.split("&")) {
            var parts = entry.split("=");
            result.add(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            );
        }
        return result;
    }

    public FormData add(String key, String value) {
        formData.put(key, value);
        return this;
    }

    public String get(String key) {
        return formData.get(key);
    }

    public String toString() {
        return formData.entrySet().stream()
                .map(entry ->
                        URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                                + "="
                                + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FormData formData1 = (FormData) o;
        return Objects.equals(formData, formData1.formData);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(formData);
    }
}
