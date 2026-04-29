package com.nokku.service;

import com.nokku.adapter.ProductScraperAdapter;
import com.nokku.adapter.ScraperRegistry;
import com.nokku.model.Product;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProductService {

    private final SourceRoutingService routingService;
    private final AiRankingService aiRankingService;
    private final ScraperRegistry scraperRegistry;
    private final ScraperConfigService configService;

    public ProductService(SourceRoutingService routingService,
                          AiRankingService aiRankingService,
                          ScraperRegistry scraperRegistry,
                          ScraperConfigService configService) {

        this.routingService = routingService;
        this.aiRankingService = aiRankingService;
        this.scraperRegistry = scraperRegistry;
        this.configService = configService;
    }

    public List<Product> getProducts(Map<String, Object> intent) {

        System.out.println("Intent: " + intent);

        String category = intent.getOrDefault("category", "").toString();
        String brand = intent.getOrDefault("brand", "").toString();
        String rawQuery = intent.getOrDefault("rawQuery", "").toString().toLowerCase();
        String domain = intent.getOrDefault("domain", "").toString();

        double minPrice = 0;
        double maxPrice = Double.MAX_VALUE;

        try {
            String numbers = rawQuery.replaceAll("[^0-9 ]", " ").trim();
            String[] parts = numbers.split("\\s+");

            if (rawQuery.contains("to") && parts.length >= 2) {
                minPrice = Double.parseDouble(parts[0]);
                maxPrice = Double.parseDouble(parts[1]);
            } else if (rawQuery.contains("under") && parts.length >= 1) {
                maxPrice = Double.parseDouble(parts[0]);
            } else if (rawQuery.contains("above") && parts.length >= 1) {
                minPrice = Double.parseDouble(parts[0]);
            }

        } catch (Exception ignored) {}

        // 🔥 FIX: use raw query only
        String query = rawQuery;
        System.out.println("FINAL SEARCH QUERY: " + query);

        List<String> sources = routingService.resolveSources(brand);

        List<Product> all = new ArrayList<>();

        for (String source : configService.getAllSources()) {

            if (sources != null && !sources.isEmpty() && !sources.contains(source)) {
                continue;
            }

            try {
                Map<String, String> config = configService.getConfig(source);

                if (config == null) continue;

                String type = config.getOrDefault("type", "generic");

                ProductScraperAdapter adapter = scraperRegistry.getAdapter(type);

                if (adapter == null) {
                    System.out.println("❌ No adapter for type: " + type);
                    continue;
                }

                System.out.println("🚀 Calling source: " + source);

                List<Product> results = adapter.search(query, source);

                all.addAll(results);

            } catch (Exception e) {
                System.out.println("⚠️ Failed source: " + source);
                e.printStackTrace();
            }
        }

        System.out.println("ALL: " + all.size());

        // 🔥 DEDUP
        Map<String, Product> uniqueMap = new LinkedHashMap<>();

        for (Product p : all) {
            String key = p.getName().toLowerCase().replaceAll("[^a-z0-9]", "") + "_" + p.getSource();

            if (uniqueMap.containsKey(key)) {
                Product existing = uniqueMap.get(key);

                if (existing.getImage() != null && !existing.getImage().isEmpty()) continue;

                if (p.getImage() != null && !p.getImage().isEmpty()) {
                    uniqueMap.put(key, p);
                }

            } else {
                uniqueMap.put(key, p);
            }
        }

        List<Product> deduped = new ArrayList<>(uniqueMap.values());

        // 🔥 LIMIT
        List<Product> limited = deduped.stream().limit(30).toList();

        // 🔥 SAFE FILTER (replaces AI filter)
        List<Product> filtered = limited.stream()
                .filter(p -> basicMatch(p, rawQuery))
                .toList();

        if (filtered.size() < 5) {
            System.out.println("⚠️ basicMatch too strict, falling back to limited results");
            filtered = limited;
        }

        if ("GROCERY".equalsIgnoreCase(domain)) {
            return balanceSources(filtered);
        }

        double finalMaxPrice = maxPrice;
        double finalMinPrice = minPrice;

        List<Product> priceFiltered = filtered.stream()
                .filter(p ->
                        p.getPrice() > 0 &&
                                (p.getPrice() >= finalMinPrice && p.getPrice() <= finalMaxPrice)
                )
                .toList();

        List<Product> finalList = priceFiltered.stream()
                .filter(p -> !containsJunk(p.getName()))
                .toList();

        if (finalList.isEmpty()) {
            return balanceSources(filtered);
        }

        List<Product> balanced = balanceSources(finalList);

        return balanced;
    }

    private boolean basicMatch(Product p, String query) {
        String name = p.getName() == null ? "" : p.getName().toLowerCase();

        for (String word : query.split("\\s+")) {
            if (name.contains(word)) return true;
        }
        return false;
    }

    private boolean containsJunk(String name) {
        String n = name.toLowerCase();
        return n.contains("case") || n.contains("cover") || n.contains("charger")
                || n.contains("cable") || n.contains("keyboard");
    }

    private List<Product> balanceSources(List<Product> products) {

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
    public List<Product> getProductsWithoutPriceFilter(Map<String, Object> intent) {

        String rawQuery = intent.getOrDefault("rawQuery", "").toString().toLowerCase();

        List<Product> all = new ArrayList<>();

        for (String source : configService.getAllSources()) {
            try {
                Map<String, String> config = configService.getConfig(source);
                String type = config.getOrDefault("type", "generic");

                ProductScraperAdapter adapter = scraperRegistry.getAdapter(type);

                if (adapter != null) {
                    all.addAll(adapter.search(rawQuery, source));
                }

            } catch (Exception ignored) {}
        }

        return balanceSources(all);
    }
}