package io.ic.starter.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI text-embedding-3-small client (1536 dims). Batches inputs per request
 * and retries on rate-limit / transient errors with exponential backoff.
 */
public class OpenAiEmbeddingClient implements EmbeddingClient {
    private static final String DEFAULT_MODEL = "text-embedding-3-small";
    private static final int MAX_RETRIES = 6;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiEmbeddingClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL, "https://api.openai.com");
    }

    public OpenAiEmbeddingClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            ArrayNode inputArray = body.putArray("input");
            for (String text : texts) {
                inputArray.add(text == null || text.isBlank() ? " " : text);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/embeddings"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new RuntimeException("Unexpected OpenAI response: " + response.body());
            }

            // data entries carry an "index" that maps back to the input order.
            var result = new float[texts.size()][];
            for (JsonNode entry : data) {
                int index = entry.get("index").asInt();
                JsonNode embedding = entry.get("embedding");
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                result[index] = vector;
            }
            return new ArrayList<>(List.of(result));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return response;
                }
                if (status == 429 || status >= 500) {
                    sleepBackoff(attempt);
                    last = new RuntimeException("OpenAI HTTP " + status + ": " + response.body());
                    continue;
                }
                throw new RuntimeException("OpenAI HTTP " + status + ": " + response.body());
            } catch (java.io.IOException e) {
                last = e;
                sleepBackoff(attempt);
            }
        }
        throw new RuntimeException("OpenAI request failed after " + MAX_RETRIES + " attempts", last);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(30_000L, (long) (1000 * Math.pow(2, attempt))));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
