package com.nokku.decision;

import com.nokku.model.Product;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BestProductSelector {

    public Map<String, Object> select(List<Product> products, Map<String, Object> intent) {

        Map<String, Object> result = new HashMap<>();

        if (products == null || products.isEmpty()) {
            return result;
        }

        // =========================================================
        // 🔥 AI BEST PICK (PRIMARY)
        // =========================================================


        // =========================================================
        // 🔽 FALLBACK LOGIC (SAFE)
        // =========================================================
        Product bestPick = products.get(0); // AI ranked first
        final Product finalBestPick = bestPick;
        Product cheapest = products.stream()
                .filter(p -> p.getPrice() > 0)
                .filter(p -> !p.getName().equalsIgnoreCase(finalBestPick.getName()))
                .min(Comparator.comparing(Product::getPrice))
                .orElse(null);

        Product topRated = products.stream()
                .filter(p -> p.getRating() > 0)
                .max(Comparator.comparing(Product::getRating))
                .orElse(null);

        // =========================================================
        // 🔥 SMART FALLBACK (if AI result looks weak)
        // =========================================================
        if (bestPick == null || bestPick.getPrice() == 0) {

            if (topRated != null) {
                bestPick = topRated;
            } else if (cheapest != null) {
                bestPick = cheapest;
            }
        }

        // =========================================================
        // 🔥 TOP 3 (SAFE)
        // =========================================================
        List<Product> top3 = products.stream()
                .limit(3)
                .collect(Collectors.toList());

        // =========================================================
        // 🔥 FINAL RESPONSE
        // =========================================================
        result.put("bestPick", bestPick);
        result.put("top3", top3);
        result.put("cheapest", cheapest);
        result.put("topRated", topRated);

        return result;
    }
}