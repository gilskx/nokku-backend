package com.nokku.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokku.model.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WalmartJsonScraper {

    public List<Product> search(String query) {

        List<Product> results = new ArrayList<>();

        try {
            String url = "https://www.walmart.com/search?q=" + query.replace(" ", "+");

//            Document doc = Jsoup.connect(url)
//                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
//                    .header("Accept-Language", "en-US,en;q=0.9")
//                    .header("Accept", "text/html")
//                    .timeout(20000)
//                    .maxBodySize(0)
//                    .get();
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "text/html")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(20000)
                    .maxBodySize(0)
                    .get();

            Element script = doc.selectFirst("script#__NEXT_DATA__");

            if (script == null) {
                System.out.println("❌ Walmart JSON not found");
                return results;
            }

            String json = script.data();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode stacks = root
                    .path("props")
                    .path("pageProps")
                    .path("initialData")
                    .path("searchResult")
                    .path("itemStacks");

            if (!stacks.isArray()) {
                System.out.println("⚠️ Walmart JSON structure changed");
                return results;
            }
            int count = 0;
            boolean debugPrinted = false;

            for (JsonNode stack : stacks) {
                for (JsonNode item : stack.path("items")) {

                    if (!debugPrinted) {
                        System.out.println("🔍 DEBUG ITEM:");
                        System.out.println(item.toPrettyString());
                        debugPrinted = true;
                    }

                    String name = item.path("name").asText("");
                    if (name.isEmpty()) continue;

    double price = 0;

                    JsonNode priceInfo = item.path("priceInfo");

// ✅ 1. PRIMARY: minPrice (BEST)
                    price = priceInfo.path("minPrice").asDouble(0);

// ✅ 2. FALLBACK: linePrice (string)
                    if (price == 0) {
                        String linePrice = priceInfo.path("linePrice").asText("");

                        if (!linePrice.isEmpty()) {
                            try {
                                price = Double.parseDouble(linePrice.replaceAll("[^0-9.]", ""));
                            } catch (Exception ignored) {}
                        }
                    }

                    String image = item.path("image").asText("");
                    if (image.isEmpty()) continue;

                    String canonical = item.path("canonicalUrl").asText("");
                    if (canonical.isEmpty()) continue;

                    String link = "https://www.walmart.com" + canonical;



                    // ✅ RATING
                                      double rating = 0;

                    if (item.has("averageRating")) {
                        rating = item.path("averageRating").asDouble(0);
                    }

                    if (rating <= 0) {
                        rating = 3.5;
                    }

                    // 🔍 DEBUG
                    System.out.println("🟢 Product: " + name);
                    System.out.println("💰 Price: " + price);
                    System.out.println("⭐ Rating: " + rating);

                    results.add(new Product(
                            name,
                            price,
                            rating,
                            "walmart",
                            link,
                            image
                    ));
                    count++;
                }
                if (count >= 20) break;
            }

            System.out.println("🟢 Walmart JSON results: " + results.size());

        } catch (Exception e) {
            System.out.println("⚠️ Walmart JSON scraper failed: " + e.getMessage());
        }

        return results;
    }

    // ✅ PRODUCT PAGE PRICE FETCH
//    private double fetchPriceFromProductPage(String url) {
//        try {
//            Document doc = Jsoup.connect(url)
//                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
//                    .header("Accept-Language", "en-US,en;q=0.9")
//                    .header("Accept", "text/html")
//                    .timeout(20000)
//                    .maxBodySize(0)
//                    .get();
//
//            String priceText = doc.select("[itemprop=price]").attr("content");
//
//            if (!priceText.isEmpty()) {
//                return Double.parseDouble(priceText);
//            }
//
//        } catch (Exception e) {
//            System.out.println("⚠️ Price fetch failed: " + url);
//        }
//
//        return 0;
//    }
}