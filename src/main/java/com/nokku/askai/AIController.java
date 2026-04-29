package com.nokku.askai;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AIController {

    @PostMapping("/ask-product")
    public Map<String, String> askProduct(@RequestBody Map<String, String> req) {

        String question = req.get("question");
        String product = req.get("product");

        // 🔥 SIMPLE PROMPT
        String prompt = "Product: " + product + "\nQuestion: " + question +
                "\nAnswer clearly with pros, cons, and recommendation.";

        // 👉 call OpenAI (replace with your service)
        String answer = callOpenAI(prompt);

        Map<String, String> res = new HashMap<>();
        res.put("answer", answer);

        return res;
    }

    private String callOpenAI(String prompt) {
        // 👉 plug your existing OpenAI service here
        return "Sample AI response: Good for daily use, average for gaming.";
    }
}