package com.nokku.adapter;

import com.nokku.model.Product;
import com.nokku.service.GoogleShoppingScraper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleShoppingApiAdapter implements ProductScraperAdapter {

    private final GoogleShoppingScraper googleShoppingScraper;

    public GoogleShoppingApiAdapter(GoogleShoppingScraper googleShoppingScraper) {
        System.out.println("🔥 GoogleShoppingApiAdapter created");
        this.googleShoppingScraper = googleShoppingScraper;
    }

    @Override
    public String getType() {
        return "google-shopping-api";
    }

    @Override
    public List<Product> search(String query, String source) {
        return googleShoppingScraper.search(query);
    }
}