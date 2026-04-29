package com.nokku.adapter;

import com.nokku.model.Product;
import com.nokku.service.WalmartJsonScraper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WalmartJsonAdapter implements ProductScraperAdapter {

    private final WalmartJsonScraper scraper;

    public WalmartJsonAdapter(WalmartJsonScraper scraper) {
        System.out.println("🔥 WalmartJsonAdapter created");
        this.scraper = scraper;
    }

    @Override
    public String getType() {
        return "json";
    }

    @Override
    public List<Product> search(String query, String source) {
        return scraper.search(query);
    }
}