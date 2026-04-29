package com.nokku.compapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class CompareAIController {

    private final OpenAIServiceCompare openAIServiceCompare;

    public CompareAIController(OpenAIServiceCompare openAIServiceCompare) {
        this.openAIServiceCompare = openAIServiceCompare;
    }

    @PostMapping("/compare-ai")
    public Map<String, Object> compareAI(@RequestBody Map<String, Object> request) {

        // ✅ SAFE extraction
        List<Map<String, Object>> products = new ArrayList<>();
        Object obj = request.get("products");

        if (obj instanceof List<?>) {
            for (Object item : (List<?>) obj) {
                if (item instanceof Map<?, ?>) {
                    products.add((Map<String, Object>) item);
                }
            }
        }

        String prompt = buildPrompt(products);

        String aiResponse = openAIServiceCompare.call(prompt);

        return parseJson(aiResponse);
    }

    // 🔥 FINAL PROMPT (VERY IMPORTANT)
    private String buildPrompt(List<Map<String, Object>> products) {

        return """
                You are an expert product comparison assistant.
                
                         Compare the given products and generate a structured and practical comparison.
                
                         IMPORTANT RULES:
                         - Works for ANY product category (laptop, phone, furniture, fruits, grocery etc.)
                         - Identify relevant attributes dynamically
                         - If products are NOT directly comparable, clearly mention it
                         - Keep explanation simple and useful
                         - Do NOT hallucinate unknown specs
                
                         🧠 REAL USER COMPARISON LOGIC (VERY IMPORTANT):
                         - Think like a real buyer comparing products
                         - Include what people usually care about:
                           - Price vs value
                           - Quality / durability
                           - Performance (if applicable)
                           - Size / usability
                           - Brand trust / popularity
                           - Pros and cons in simple terms
                         - Avoid technical-only comparison — make it practical and decision-focused
                         - Use simple, human-friendly language (like how a person explains to a friend)
                
                         🔴 LINK HANDLING RULES (VERY IMPORTANT):
                         - DO NOT include any URLs, links, or image links inside the comparison table
                         - DO NOT include attributes like "link", "url", "product link", "image", or similar in the table
                         - Ignore any long or encoded URLs (e.g., Google shopping links, tracking URLs)
                         - Table must contain ONLY clean, human-readable values (price, rating, size, features, etc.)
                
                         - Each product MAY still have a valid purchase link separately (used for Buy Now button)
                         - Ensure product links (if present in input) remain valid and unchanged
                         - DO NOT modify, shorten, or remove valid product URLs
                         - DO NOT include tracking-heavy or search-result links as product URLs (prefer direct product pages)
                
                         OUTPUT FORMAT (STRICT JSON ONLY):
                
                         {
                           "comparison_type": "direct | different_category",
                           "table": [
                             { "attribute": "...", "product1": "...", "product2": "..." }
                           ],
                           "takeaways": [
                             "..."
                           ],
                           "which_should_you_choose": [
                             "If your need is ... → go with Product 1",
                             "If your need is ... → go with Product 2"
                           ],
                           "verdict": "..."
                         }
                
                         PRODUCTS:  
""" + products.toString();
    }

    // ✅ REAL JSON PARSER
    private Map<String, Object> parseJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {

            // fallback (important for debugging)
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("error", "AI response parsing failed");
            fallback.put("raw", json);

            return fallback;
        }
    }
}