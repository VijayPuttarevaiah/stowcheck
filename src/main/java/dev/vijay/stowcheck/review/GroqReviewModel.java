package dev.vijay.stowcheck.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Reviewer backed by Groq's OpenAI-compatible chat completions API.
 *
 * <p>Uses strict structured outputs: the request carries a JSON schema and the model is
 * constrained to it, so the reply always parses into {@link ModelReview}. Only created when
 * a GROQ_API_KEY is configured; without one the service runs and reports the review as off.
 */
@Component
@ConditionalOnExpression("!'${stowcheck.ai.api-key:}'.isBlank()")
public class GroqReviewModel implements ReviewModel {

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("findings", "carrierMessage"),
            "properties", Map.of(
                    "findings", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("index", "verdict", "explanation", "suggestedFix"),
                                    "properties", Map.of(
                                            "index", Map.of("type", "integer"),
                                            "verdict", Map.of("type", "string",
                                                    "enum", List.of("CONFIRMED", "LIKELY_INTENTIONAL", "NEEDS_HUMAN")),
                                            "explanation", Map.of("type", "string"),
                                            "suggestedFix", Map.of("type", "string")))),
                    "carrierMessage", Map.of("type", "string")));

    private final RestClient http;
    private final JsonMapper json;
    private final String model;

    public GroqReviewModel(@Value("${stowcheck.ai.api-key}") String apiKey,
                           @Value("${stowcheck.ai.model}") String model,
                           @Value("${stowcheck.ai.base-url}") String baseUrl,
                           JsonMapper json) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.json = json;
        this.model = model;
    }

    @Override
    public Result review(String prompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0,
                "max_completion_tokens", 8000,
                "messages", List.of(
                        Map.of("role", "system", "content", ReviewPrompt.SYSTEM),
                        Map.of("role", "user", "content", prompt)),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of("name", "finding_review", "strict", true, "schema", SCHEMA)));

        Completion completion;
        try {
            completion = http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Completion.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ReviewUnavailableException("The Groq API key was rejected", e);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ReviewUnavailableException("Groq rate limit reached, try again in a minute", e);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new ReviewUnavailableException("Groq API error " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ReviewUnavailableException("Could not reach the Groq API", e);
        }

        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new ReviewUnavailableException("Groq returned no answer");
        }
        Choice choice = completion.choices().get(0);
        if ("length".equals(choice.finishReason())) {
            throw new ReviewUnavailableException("The review was cut off; try a plan with fewer findings");
        }

        ModelReview review;
        try {
            review = json.readValue(choice.message().content(), ModelReview.class);
        } catch (JacksonException e) {
            throw new ReviewUnavailableException("Groq returned JSON that does not match the schema", e);
        }

        Usage usage = completion.usage() != null ? completion.usage() : new Usage(0, 0);
        return new Result(review, model, usage.promptTokens(), usage.completionTokens());
    }

    // Only the parts of the chat completions response this class reads

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Completion(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") long promptTokens,
                 @JsonProperty("completion_tokens") long completionTokens) {
    }
}
