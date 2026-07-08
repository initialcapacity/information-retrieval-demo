package io.ic.starter.websupport;

import io.javalin.http.Context;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlashMessaging {
    public static final String flashKey = "flash";

    public static void addFlash(Context context, String message) {
        String currentFlash = context.sessionAttribute(flashKey);
        if (currentFlash == null || currentFlash.isBlank()) {
            context.sessionAttribute(flashKey, message);
        } else {
            context.sessionAttribute(flashKey, currentFlash + "," + message);
        }
    }

    public static Context renderWithFlash(Context context, String filePath, Map<String, Object> model) {
        var modelWithFlash = new HashMap<>(model);
        modelWithFlash.put(flashKey, consumeFlash(context));
        return context.render(filePath, modelWithFlash);
    }

    public static Context renderWithFlash(Context context, String filePath) {
        return renderWithFlash(context, filePath, Map.of());
    }

    private static List<String> consumeFlash(Context context) {
        String flash = context.consumeSessionAttribute(flashKey);
        if (flash == null || flash.isBlank()) {
            return List.of();
        }
        return Arrays.stream(flash.split(",")).toList();
    }
}
