package com.nokku.service;

import com.nokku.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class EbayApiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final EbayOAuthService oauthService;

    public EbayApiService(EbayOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    // 🔥 cache + rate protection
    private final Map<String, List<Product>> cache = new HashMap<>();
    private long lastCallTime = 0;

    public List<Product> search(String query) {

        List<Product> results = new ArrayList<>();

        try {

            // 🔥 cache
            if (cache.containsKey(query)) {
                System.out.println("⚡ eBay cache hit");
                return cache.get(query);
            }

            // 🔥 rate limit protection
            long now = System.currentTimeMillis();
            if (now - lastCallTime < 1500) {
                System.out.println("⏳ Skipping eBay (rate limit)");
                return Collections.emptyList();
            }
            lastCallTime = now;

            String token = oauthService.getAccessToken();

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            String url = "https://api.ebay.com/buy/browse/v1/item_summary/search?q=" + encodedQuery + "&limit=20";

            System.out.println("🚀 Calling eBay Browse API");

            Map response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(
                            new org.springframework.http.HttpHeaders() {{
                                set("Authorization", "Bearer " + token);
                                set("X-EBAY-C-MARKETPLACE-ID", "EBAY_US");
                            }}
                    ),
                    Map.class
            ).getBody();

            if (response == null || response.get("itemSummaries") == null) {
                return results;
            }

            List<Map> items = (List<Map>) response.get("itemSummaries");

            for (Map item : items) {

                try {
                    String name = item.getOrDefault("title", "").toString();

                    double price = Double.parseDouble(
                            ((Map) item.get("price")).get("value").toString()
                    );

                    String link = item.getOrDefault("itemWebUrl", "").toString();

                    String image = ((Map) item.get("image")).getOrDefault("imageUrl", "").toString();

                    results.add(new Product(
                            name,
                            price,
                            0,
                            "ebay",
                            link,
                            image
                    ));

                } catch (Exception e) {
                    System.out.println("⚠️ Skipping eBay item");
                }
            }

            cache.put(query, results);

            System.out.println("✅ eBay OAuth results: " + results.size());

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ eBay OAuth API failed: " + e.getMessage());
        }

        return results;
    }
}