package com.nokku.adapter;

import com.nokku.model.Product;
import com.nokku.service.GenericScraper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GenericScraperAdapter implements ProductScraperAdapter {

    private final GenericScraper scraper;

    public GenericScraperAdapter(GenericScraper scraper) {
        System.out.println("🔥 GenericScraperAdapter created");
        this.scraper = scraper;
    }

    @Override
    public String getType() {
        return "generic";
    }

    @Override
    public List<Product> search(String query, String source) {
        return scraper.search(query, source);
    }
}