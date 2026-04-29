package com.nokku.service;

import com.nokku.model.Product;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ScoringService {

    public Map<String, Product> score(List<Product> products) {

        Map<String, Product> result = new HashMap<>();

        if (products == null || products.isEmpty()) {
            return result;
        }

        double maxPrice = products.stream().mapToDouble(Product::getPrice).max().orElse(1);
        double minPrice = products.stream().mapToDouble(Product::getPrice).min().orElse(1);

        Product best = null;
        double bestScore = -1;

        for (Product p : products) {

// ✅ FIX: Normalize rating
            double rating = p.getRating();

// If rating missing or invalid → assign default
            if (rating <= 0) {
                rating = 3.0;  // neutral fallback
                p.setRating(rating);  // 🔥 IMPORTANT: send to frontend
            }
            double priceScore = 1 - ((p.getPrice() - minPrice) / (maxPrice - minPrice + 1));
          //  double ratingScore = p.getRating() / 5.0;
            double ratingScore = rating / 5.0;
            double trustScore =  p.getTrustScore();
            double deliveryScore = getDeliveryScore(p.getDeliveryType());
        System.out.println("Rating "+ratingScore);
            String name = p.getName().toLowerCase();

            double score =
                    (priceScore * 0.25) +
                            (ratingScore * 0.35) +
                            (trustScore * 0.20) +
                            (deliveryScore * 0.20);

            // 🔥 NEW: Version-aware scoring
            int version = extractVersion(name);
            if (version > 0) {
                score += version * 0.5; // newer models preferred
            }

            // 🔥 NEW: Strong penalty for renewed
            if (name.contains("renewed") ||
                    name.contains("refurbished") ||
                    name.contains("used")) {

                score -= 20;
            }

            // 🔥 Bonus for premium keywords
            if (name.contains("pro") || name.contains("max") || name.contains("ultra")) {
                score += 2;
            }

            // =========================================================
            // 🚀 NEW FIX: DEVICE AWARE INTELLIGENT SCORING
            // =========================================================

            if (isDevice(name)) {

                double performanceScore = 0;

                // GPU scoring (MOST IMPORTANT for gaming laptops)
                if (name.contains("rtx 4090")) performanceScore += 100;
                else if (name.contains("rtx 4080")) performanceScore += 95;
                else if (name.contains("rtx 4070")) performanceScore += 90;
                else if (name.contains("rtx 4060")) performanceScore += 85;
                else if (name.contains("rtx 4050")) performanceScore += 75;
                else if (name.contains("rtx 3050")) performanceScore += 60;

                // RAM scoring
                if (name.contains("32gb")) performanceScore += 30;
                else if (name.contains("16gb")) performanceScore += 20;
                else if (name.contains("8gb")) performanceScore += 10;

                // Storage scoring
                if (name.contains("1tb")) performanceScore += 20;
                else if (name.contains("512gb")) performanceScore += 10;

                // Normalize performance
                performanceScore = performanceScore / 150.0;

                // 🔥 FINAL BOOST: combine performance + value
                score += (performanceScore * 0.30);

                // 🔥 Smart “good quality + low price” behavior
                score += (priceScore * 0.20);   // cheaper = better
                score += (ratingScore * 0.20);  // quality = better
            }

            // =========================================================

            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }

        Product cheapest = products.stream()
                .min(Comparator.comparing(Product::getPrice))
                .orElse(null);

        Product topRated = products.stream()
                .max(Comparator.comparing(Product::getRating))
                .orElse(null);

        // 🔥 Ensure uniqueness
        Set<Product> used = new HashSet<>();

        if (best != null) {
            result.put("best", best);
            used.add(best);
        }

        if (cheapest != null) {
            if (used.contains(cheapest)) {
                cheapest = products.stream()
                        .filter(p -> !used.contains(p))
                        .min(Comparator.comparing(Product::getPrice))
                        .orElse(cheapest);
            }
            result.put("cheapest", cheapest);
            used.add(cheapest);
        }

        if (topRated != null) {
            if (used.contains(topRated)) {
                topRated = products.stream()
                        .filter(p -> !used.contains(p))
                        .max(Comparator.comparing(Product::getRating))
                        .orElse(topRated);
            }
            result.put("topRated", topRated);
        }

        return result;
    }

    // =========================================================
    // 🚀 NEW: Detect electronics/device category
    // =========================================================
    private boolean isDevice(String name) {
        return name.contains("laptop") ||
                name.contains("notebook") ||
                name.contains("gaming") ||
                name.contains("rtx") ||
                name.contains("intel") ||
                name.contains("amd") ||
                name.contains("ssd");
    }

    // 🔥 Extract version from product name
    private int extractVersion(String name) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+)")
                    .matcher(name);

            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private double getDeliveryScore(String deliveryType) {

        if (deliveryType == null) return 0.5;

        switch (deliveryType.toUpperCase()) {
            case "SAME_DAY": return 1.0;
            case "NEXT_DAY": return 0.8;
            default: return 0.5;
        }
    }
}