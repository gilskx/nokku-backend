package com.nokku.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokku.client.OpenAIClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class IntentService {

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentService(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    public Map<String, Object> parseIntent(String query) {

        try {
            String prompt = """
            Extract structured shopping intent from the user query.

            Return ONLY valid JSON.

            Guidelines:
            - Identify what the user wants to buy
            - Identify the domain
            - Extract brand if mentioned
            - Extract keywords if relevant
            - Extract price constraints if present
            - Do NOT force fields

            ALSO classify intentType:
            - "budget" → cheap, under, low price
            - "premium" → best, top, high quality
            - "balanced" → default

            Examples:

            Query: buy iphone under 1000
            → {"category":"phone","brand":"apple","domain":"electronics","maxPrice":1000,"intentType":"budget"}

            Query: best gaming headset
            → {"category":"headset","domain":"electronics","intentType":"premium"}

            Query: buy banana
            → {"category":"banana","domain":"grocery","intentType":"balanced"}

            Query: %s
            """.formatted(query);

            String rawResponse = openAIClient.getIntent(prompt);

            JsonNode root = objectMapper.readTree(rawResponse);

            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            content = content.trim();

            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");

            if (start != -1 && end != -1) {
                content = content.substring(start, end + 1);
            }

            Map<String, Object> result = objectMapper.readValue(content, Map.class);

            // 🔥 SAFE DEFAULTS
            result.putIfAbsent("category", "unknown");
            result.putIfAbsent("brand", "");
            result.putIfAbsent("domain", "other");
            result.putIfAbsent("minPrice", 0);
            result.putIfAbsent("maxPrice", Double.MAX_VALUE);

            // 🔥 Ensure intentType exists (fallback logic)
            result.putIfAbsent("intentType", deriveIntentType(query));

            // 🔥 Always keep raw query
            result.put("rawQuery", query);

            return result;


        } catch (Exception e) {
            System.out.println("⚠️ Intent AI failed, using fallback intent: " + e.getMessage());

            Map<String, Object> fallback = new HashMap<>();
            fallback.put("rawQuery", query);
            fallback.put("category", "unknown");
            fallback.put("brand", "");
            fallback.put("domain", "other");
            fallback.put("minPrice", 0);
            fallback.put("maxPrice", Double.MAX_VALUE);
            fallback.put("intentType", deriveIntentType(query));
            e.printStackTrace();
            return fallback;
        }
    }

    // 🔥 Fallback intent classifier (used if LLM misses it)
    private String deriveIntentType(String query) {

        String q = query.toLowerCase();

        if (q.contains("cheap") || q.contains("under") || q.contains("budget") || q.contains("low price")) {
            return "budget";
        }

        if (q.contains("best") || q.contains("premium") || q.contains("top") || q.contains("high quality")) {
            return "premium";
        }

        return "balanced";
    }
}