package com.nokku.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class OpenAIClient {

    private final WebClient webClient;

    public OpenAIClient(@Value("${openai.api.key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String getIntent(String prompt) {

        Map<String, Object> request = Map.of(
                "model", "gpt-4.1-mini",
                "messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                }
        );

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}