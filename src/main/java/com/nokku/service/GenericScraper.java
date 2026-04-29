package com.nokku.service;

import com.nokku.model.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GenericScraper {

    private final ScraperConfigService configService;

    public GenericScraper(ScraperConfigService configService) {
        this.configService = configService;
    }

    public List<Product> search(String query, String source) {

        List<Product> results = new ArrayList<>();

        Map<String, String> cfg = configService.getConfig(source);
        if (cfg == null) return results;

        String baseSearchUrl = cfg.get("searchUrl");

        if (baseSearchUrl == null || baseSearchUrl.isEmpty()) {
            System.out.println("❌ Missing searchUrl for source: " + source);
            return results;
        }

     //   String searchUrl = baseSearchUrl + query.replace(" ", "+");
        String searchUrl;

        //String fixed = cfg.get("fixedUrl");
        Object fixedObj = ((Map<?, ?>) cfg).get("fixedUrl");

        boolean isFixedUrl = false;

        if (fixedObj instanceof Boolean) {
            isFixedUrl = (Boolean) fixedObj;
        } else if (fixedObj instanceof String) {
            isFixedUrl = Boolean.parseBoolean((String) fixedObj);
        }

        if (isFixedUrl) {
            searchUrl = baseSearchUrl;
        } else {
            searchUrl = baseSearchUrl + query.replace(" ", "+");
        }
        System.out.println("🔍 Using URL: " + searchUrl);

        try {
//            Document doc = Jsoup.connect(searchUrl)
//                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
//                    .timeout(20000)
//                    .get();
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
                    .header("Connection", "keep-alive")
                    .timeout(20000)
                    .get();

            if (doc.title().toLowerCase().contains("robot")) {
                System.out.println("🚫 Blocked by " + source);
                return results;
            }

            Elements items = selectFirstAvailable(doc, cfg, "itemSelectors");

            System.out.println("🔍 Items found: " + items.size());

            for (Element item : items) {

                try {
                    String name = extractFirst(item, cfg, "nameSelectors");
                    if (name.isEmpty()) continue;

                    // ✅ FIXED PRICE EXTRACTION
                    double price = 0;

                    String priceText = item.select(cfg.get("priceSelector")).text();
                    if (!priceText.isEmpty()) {
                        price = parse(priceText);
                    }

                    // ✅ Amazon split price fallback
                    if (price == 0) {
                        String whole = item.select(".a-price-whole").text();
                        String fraction = item.select(".a-price-fraction").text();

                        if (!whole.isEmpty()) {
                            try {
                                price = Double.parseDouble(
                                        whole.replaceAll("[^0-9]", "") + "." +
                                                (fraction.isEmpty() ? "0" : fraction)
                                );
                            } catch (Exception ignored) {}
                        }
                    }

                    // ❌ skip invalid products
                    if (price <= 0) continue;

                    String link = "";
                    String image = "";

                    if ("amazon".equalsIgnoreCase(source)) {

                        String asin = item.attr("data-asin");
                        if (asin == null || asin.isEmpty()) continue;

                        link = cfg.get("baseUrl") + "/dp/" + asin;

                        Element imgEl = item.select("img.s-image").first();
                        if (imgEl != null) {
                            image = imgEl.attr("src");
                        }

                    } else {

                        link = item.select(cfg.get("linkSelector")).attr("href");

                        if (!link.startsWith("http")) {
                            link = cfg.get("baseUrl") + link;
                        }

                        image = extractImage(item, cfg.get("imageSelector"));
                    }

                    if (link.isEmpty() || image.isEmpty()) continue;

                    // ✅ FIXED RATING EXTRACTION
                    double rating = 0;

                    String ratingText = item.select("span.a-icon-alt").text();

                    if (!ratingText.isEmpty()) {
                        try {
                            rating = Double.parseDouble(ratingText.split(" ")[0]);
                        } catch (Exception ignored) {}
                    }

                    // fallback rating
                    if (rating <= 0) {
                        rating = 3.5;
                    }

                    // 🔍 DEBUG
                    System.out.println("🟢 " + source + " Product: " + name);
                    System.out.println("💰 Price: " + price);
                    System.out.println("⭐ Rating: " + rating);

                    results.add(new Product(
                            name,
                            price,
                            rating,
                            source,
                            link,
                            image
                    ));

                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            System.out.println("⚠️ " + source + " failed");
            e.printStackTrace();
        }

        return results;
    }

    private String extractImage(Element item, String selector) {
        Element img = item.select(selector).first();
        if (img == null) return "";
        return img.attr("src");
    }

    private double parse(String text) {
        try {
            String cleaned = text.replaceAll("[^0-9.]", "");
            return cleaned.isEmpty() ? 0 : Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    private Elements selectFirstAvailable(Document doc,
                                          Map<String, String> cfg,
                                          String key) {

        Object selectorsObj = ((Map<?, ?>) cfg).get(key);

        if (selectorsObj instanceof List<?>) {
            List<String> selectors = (List<String>) selectorsObj;

            for (String selector : selectors) {
                Elements elements = doc.select(selector);
                if (!elements.isEmpty()) {
                    System.out.println("✅ Using selector: " + selector);
                    return elements;
                }
            }
        } else if (selectorsObj instanceof String) {
            return doc.select((String) selectorsObj);
        }

        return new Elements();
    }

    private String extractFirst(Element item,
                                Map<String, String> cfg,
                                String key) {

        Object selectorsObj = ((Map<?, ?>) cfg).get(key);

        if (selectorsObj instanceof List<?>) {
            for (String selector : (List<String>) selectorsObj) {
                String val = item.select(selector).text();
                if (!val.isEmpty()) return val;
            }
        } else if (selectorsObj instanceof String) {
            return item.select((String) selectorsObj).text();
        }

        return "";
    }
}