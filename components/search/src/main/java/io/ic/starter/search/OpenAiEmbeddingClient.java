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
    private static final String DEFAULT_MODEL = RetrievalConfig.EMBEDDING_MODEL;
    private static final int DEFAULT_MAX_ATTEMPTS = 6;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 1_000;
    private static final long DEFAULT_MAX_BACKOFF_MILLIS = 30_000;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final int maxAttempts;
    private final Duration requestTimeout;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiEmbeddingClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL, "https://api.openai.com");
    }

    public OpenAiEmbeddingClient(String apiKey, String model, String baseUrl) {
        this(apiKey, model, baseUrl, DEFAULT_MAX_ATTEMPTS, DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_REQUEST_TIMEOUT, DEFAULT_INITIAL_BACKOFF_MILLIS, DEFAULT_MAX_BACKOFF_MILLIS);
    }

    private OpenAiEmbeddingClient(String apiKey, String model, String baseUrl, int maxAttempts,
                                  Duration connectTimeout, Duration requestTimeout,
                                  long initialBackoffMillis, long maxBackoffMillis) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.maxAttempts = maxAttempts;
        this.requestTimeout = requestTimeout;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    public static OpenAiEmbeddingClient forInteractiveRequests(String apiKey) {
        return new OpenAiEmbeddingClient(
                apiKey, DEFAULT_MODEL, "https://api.openai.com", 2,
                Duration.ofSeconds(3), Duration.ofSeconds(10), 500, 2_000);
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
                    .timeout(requestTimeout)
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
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return response;
                }
                if (status == 429 || status >= 500) {
                    last = new RuntimeException("OpenAI HTTP " + status + ": " + response.body());
                    if (attempt + 1 < maxAttempts) {
                        sleepBackoff(attempt);
                    }
                    continue;
                }
                throw new RuntimeException("OpenAI HTTP " + status + ": " + response.body());
            } catch (java.io.IOException e) {
                last = e;
                if (attempt + 1 < maxAttempts) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new RuntimeException("OpenAI request failed after " + maxAttempts + " attempts", last);
    }

    private void sleepBackoff(int attempt) {
        try {
            long multiplier = 1L << Math.min(attempt, 20);
            Thread.sleep(Math.min(maxBackoffMillis, initialBackoffMillis * multiplier));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI request interrupted", ie);
        }
    }
}
