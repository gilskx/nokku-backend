package com.nokku.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScraperConfigService {

    private Map<String, Object> config;

    public ScraperConfigService() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getResourceAsStream("/scraper-config.json");

            if (is == null) {
                throw new RuntimeException("❌ scraper-config.json not found in resources");
            }

            config = mapper.readValue(is, Map.class);

            System.out.println("🔥 Loaded sources: " + config.keySet());

        } catch (Exception e) {
            throw new RuntimeException("Failed to load scraper config", e);
        }
    }

    // 🔥 Get config for a specific source
    public Map<String, String> getConfig(String source) {

        Object obj = config.get(source);

        if (!(obj instanceof Map)) return null;

        Map<String, Object> cfg = new HashMap<>((Map<String, Object>) obj);

        // 🔥 Apply fallback if missing
        Map<String, List<String>> defaults = DEFAULT_SELECTORS.get(source);

        if (defaults != null) {

            for (Map.Entry<String, List<String>> entry : defaults.entrySet()) {

                String key = entry.getKey();

                Object value = cfg.get(key);

                if (value == null || (value instanceof List && ((List<?>) value).isEmpty())) {

                    System.out.println("⚠️ Applying default " + key + " for: " + source);

                    cfg.put(key, entry.getValue());
                }
            }
        }

        return (Map<String, String>) (Map) cfg;
    }

    // 🔥 Get all valid sources (exclude non-sources like selectionRules)
    public Set<String> getAllSources() {
        return config.keySet().stream()
                .filter(key -> !"selectionRules".equals(key))
                .collect(Collectors.toSet());
    }

    // 🔥 Get type safely (IMPORTANT FIX)
    public String getType(String source) {

        Map<String, String> cfg = getConfig(source);

        if (cfg == null) return "generic"; // safe fallback

        String type = cfg.get("type");

        // 🔥 normalize types (handles old configs)
        if (type == null || type.equalsIgnoreCase("html")) {
            return "generic";
        }

        return type.toLowerCase();
    }

    // 🔥 Get selection rules safely
    public List<Map<String, Object>> getSelectionRules() {

        Object obj = config.get("selectionRules");

        if (obj instanceof List) {
            return (List<Map<String, Object>>) obj;
        }

        return Collections.emptyList();
    }
    @PostConstruct
    public void validateConfig() {

        System.out.println("🔍 Validating scraper config...");

        for (String source : getAllSources()) {

            Map<String, String> cfg = getConfig(source);

            if (cfg == null) {
                throw new RuntimeException("❌ Config missing for source: " + source);
            }

            String type = cfg.get("type");

            if (type == null) {
                throw new RuntimeException("❌ Missing type for source: " + source);
            }

            // =========================================================
            // 🔥 GENERIC SCRAPER VALIDATION
            // =========================================================
            if ("generic".equalsIgnoreCase(type)) {

                validateField(cfg, source, "searchUrl");

                validateSelector(cfg, source, "itemSelector", "itemSelectors");
                validateSelector(cfg, source, "nameSelector", "nameSelectors");
                validateSelector(cfg, source, "priceSelector", "priceSelectors");
                validateSelector(cfg, source, "imageSelector", "imageSelectors");
                validateSelector(cfg, source, "linkSelector", "linkSelectors");
            }

            // =========================================================
            // 🔥 API / JSON INFO (optional logging)
            // =========================================================
            if ("api".equalsIgnoreCase(type)) {
                System.out.println("ℹ️ API source: " + source);
            }

            if ("json".equalsIgnoreCase(type)) {
                System.out.println("ℹ️ JSON source: " + source);
            }
        }

        System.out.println("✅ Scraper config validation completed");
    }
    private void validateField(Map<String, String> cfg, String source, String field) {

        String value = cfg.get(field);

        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("❌ Missing " + field + " for: " + source);
        }
    }
    private void validateSelector(Map<String, String> cfg,
                                  String source,
                                  String singleKey,
                                  String arrayKey) {

        Object single = ((Map<?, ?>) cfg).get(singleKey);
        Object array = ((Map<?, ?>) cfg).get(arrayKey);

        // ❌ both missing
        if (single == null && array == null) {
          //  throw new RuntimeException("❌ Missing " + singleKey + "/" + arrayKey + " for: " + source);
            System.out.println("⚠️ Missing " + singleKey + "/" + arrayKey + " for: " + source + " → will fallback");

        }

        // 🔥 validate array if present
        if (array instanceof List<?>) {
            List<?> list = (List<?>) array;

            if (list.isEmpty()) {
                //throw new RuntimeException("❌ Empty " + arrayKey + " for: " + source);
                System.out.println("⚠️ Empty " + arrayKey + " for: " + source + " → will fallback");

            }
        }

        // 🔥 validate string if present
        if (single instanceof String) {
            if (((String) single).trim().isEmpty()) {
               // throw new RuntimeException("❌ Empty " + singleKey + " for: " + source);
                System.out.println("⚠️ Empty " + singleKey + " for: " + source + " → will fallback");

            }
        }
    }

    private static final Map<String, Map<String, List<String>>> DEFAULT_SELECTORS = Map.of(

            "amazon", Map.of(
                    "itemSelectors", List.of(
                            "div[data-component-type=s-search-result]",
                            "div.s-result-item[data-asin]"
                    ),
                    "nameSelectors", List.of("h2 span"),
                    "priceSelectors", List.of(".a-price-whole", ".a-offscreen"),
                    "imageSelectors", List.of("img.s-image"),
                    "linkSelectors", List.of("h2 a.a-link-normal")
            ),

            "target", Map.of(
                    "itemSelectors", List.of("div[data-test='product-card']"),
                    "nameSelectors", List.of("a[data-test='product-title']"),
                    "priceSelectors", List.of("span[data-test='current-price']"),
                    "imageSelectors", List.of("img"),
                    "linkSelectors", List.of("a[data-test='product-title']")
            )
    );
}