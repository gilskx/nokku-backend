package com.nokku.service;

import com.nokku.adapter.ProductScraperAdapter;
import com.nokku.adapter.ScraperRegistry;
import com.nokku.model.Product;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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

        String rawQuery = intent.getOrDefault("rawQuery", "").toString().toLowerCase();
        String brand = intent.getOrDefault("brand", "").toString();
        String domain = intent.getOrDefault("domain", "").toString();

        System.out.println("FINAL QUERY: " + rawQuery);

        // ===================== STEP 1: SCRAPE =====================
        List<Product> all = new ArrayList<>();

        List<String> sources = routingService.resolveSources(brand);

        for (String source : configService.getAllSources()) {

            if (sources != null && !sources.isEmpty() && !sources.contains(source)) {
                continue;
            }

            try {
                Map<String, String> config = configService.getConfig(source);
                if (config == null) continue;

                String type = config.getOrDefault("type", "generic");

                ProductScraperAdapter adapter = scraperRegistry.getAdapter(type);
                if (adapter == null) continue;

                System.out.println("🚀 Calling source: " + source);

                List<Product> results = adapter.search(rawQuery, source);

                all.addAll(results);

            } catch (Exception e) {
                System.out.println("⚠️ Failed source: " + source);
            }
        }

        System.out.println("ALL PRODUCTS: " + all.size());
        System.out.println("🧪 [SCRAPED SOURCES]");
        all.stream()
                .collect(Collectors.groupingBy(Product::getSource, Collectors.counting()))
                .forEach((k,v) -> System.out.println("SRC=" + k + " COUNT=" + v));
        if (all.isEmpty()) return List.of();

        // ===================== STEP 2: DEDUP =====================
        Map<String, Product> unique = new LinkedHashMap<>();

        for (Product p : all) {
            String key = (p.getName() + "_" + p.getSource())
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]", "");

            unique.putIfAbsent(key, p);
        }

        List<Product> deduped = new ArrayList<>(unique.values());

        // ===================== STEP 3: CLEANUP =====================
        List<Product> cleaned = deduped.stream()
                .filter(p -> p.getPrice() > 0)
                .filter(p -> !containsJunk(p.getName()))
                .toList();
// 🔥 RESTORE OLD LOGIC (CRITICAL)
        List<Product> filtered = cleaned.stream()
                .filter(p -> basicMatch(p, rawQuery))
                .toList();

// fallback if too strict
        if (filtered.size() < 10) {
            System.out.println("⚠️ basicMatch too strict, fallback to cleaned");
            filtered = cleaned;
        }
        if (cleaned.isEmpty()) cleaned = deduped;

        // ===================== STEP 4: BALANCE =====================
      //  List<Product> balanced = balanceSources(cleaned);
        List<Product> balanced = balanceSources(filtered);
        // ===================== STEP 5: AI RANK =====================
        System.out.println("🧪 [AFTER BALANCE]");
        balanced.stream().limit(20)
                .forEach(p -> System.out.println("SRC=" + p.getSource()));
        return aiRankingService.rank(rawQuery, balanced);
    }

    // ===================== REMOVE JUNK =====================
    private boolean containsJunk(String name) {
        if (name == null) return true;
        String n = name.toLowerCase();
        return n.contains("case") || n.contains("cover") || n.contains("charger")
                || n.contains("cable") || n.contains("keyboard");
    }

    // ===================== BALANCE SOURCES =====================
    private List<Product> balanceSources(List<Product> products) {

        // 1. Group products by source
        Map<String, List<Product>> grouped = new HashMap<>();

        for (Product p : products) {
            grouped.computeIfAbsent(p.getSource(), k -> new ArrayList<>()).add(p);
        }

// 2. Convert to list of lists
        List<List<Product>> lists = new ArrayList<>(grouped.values());

// 3. (Optional) shuffle OR better → sort for fairness
// Collections.shuffle(lists);   ❌ avoid random
        lists.sort(Comparator.comparingInt(List::size));  // ✅ stable + fair

// 4. Iterate (example: round robin)
        List<Product> result = new ArrayList<>();

        int i = 0;
        boolean added;

        do {
            added = false;

            for (List<Product> list : lists) {
                if (i < list.size()) {
                    result.add(list.get(i));
                    added = true;
                }
            }

            i++;

        } while (added);

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
    private boolean basicMatch(Product p, String query) {

        String name = p.getName() == null ? "" : p.getName().toLowerCase();

        String[] words = query.split("\\s+");

        int matchCount = 0;

        for (String word : words) {
            if (word.length() < 3) continue;
            if (name.contains(word)) {
                matchCount++;
            }
        }

        if (matchCount >= 1) {
            return true;
        }

        return p.getPrice() > 0 && p.getRating() >= 3.5;
    }
}