package com.nokku.service;

import com.nokku.model.Product;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PostProcessingService {

    // 🔥 Deduplication
    public List<Product> dedupe(List<Product> products) {

        Map<String, Product> uniqueMap = new LinkedHashMap<>();

        for (Product p : products) {

            String key = p.getName().toLowerCase()
                    .replaceAll("[^a-z0-9]", "") + "_" + p.getSource();

            if (uniqueMap.containsKey(key)) {
                Product existing = uniqueMap.get(key);

                if (existing.getImage() != null && !existing.getImage().isEmpty()) {
                    continue;
                }

                if (p.getImage() != null && !p.getImage().isEmpty()) {
                    uniqueMap.put(key, p);
                }

            } else {
                uniqueMap.put(key, p);
            }
        }

        return new ArrayList<>(uniqueMap.values());
    }

    // 🔥 Token filtering
    public List<Product> tokenFilter(List<Product> products, String rawQuery) {

        Set<String> tokens = Arrays.stream(rawQuery.split("\\s+"))
                .map(String::toLowerCase)
                .filter(w -> w.length() > 2 && !w.equals("buy"))
                .collect(Collectors.toSet());

        return products.stream()
                .filter(p -> {
                    String name = p.getName() == null ? "" : p.getName().toLowerCase();
                    return tokens.stream().anyMatch(name::contains);
                })
                .toList();
    }

    // 🔥 Price filtering
    public List<Product> priceFilter(List<Product> products, double min, double max) {

        return products.stream()
                .filter(p -> p.getPrice() >= min && p.getPrice() <= max)
                .toList();
    }

    // 🔥 Junk filter
    public List<Product> removeJunk(List<Product> products) {

        return products.stream()
                .filter(p -> {
                    String n = p.getName().toLowerCase();
                    return !(n.contains("case") ||
                            n.contains("cover") ||
                            n.contains("charger") ||
                            n.contains("cable"));
                })
                .toList();
    }

    // 🔥 Source balancing
    public List<Product> balance(List<Product> products) {

        Map<String, List<Product>> grouped = new LinkedHashMap<>();

        for (Product p : products) {
            grouped.computeIfAbsent(p.getSource(), k -> new ArrayList<>()).add(p);
        }

        List<Product> result = new ArrayList<>();

        int maxPerSource = 10;

        for (int i = 0; i < maxPerSource; i++) {
            for (List<Product> list : grouped.values()) {
                if (i < list.size()) {
                    result.add(list.get(i));
                }
            }
        }

        return result;
    }
}