package com.nokku.compapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAIServiceCompare {

    @Value("${openai.api.key}")
    private String apiKey;

    private final String URL = "https://api.openai.com/v1/chat/completions";

    public String call(String prompt) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");

        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "You are a product comparison expert."
        ));

        messages.add(Map.of(
                "role", "user",
                "content", prompt
        ));

        body.put("messages", messages);
        body.put("temperature", 0.3);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(URL, request, Map.class);

        Map responseBody = response.getBody();

        // 🔥 Extract actual AI content
        List choices = (List) responseBody.get("choices");
        Map firstChoice = (Map) choices.get(0);
        Map message = (Map) firstChoice.get("message");

        return (String) message.get("content");
    }
}