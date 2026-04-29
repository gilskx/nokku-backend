package com.nokku.service;

import com.nokku.adapter.ProductScraperAdapter;
import com.nokku.adapter.ScraperRegistry;
import com.nokku.model.Product;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScraperRouter {

    private final ScraperRegistry registry;
    private final ScraperConfigService configService;

    public ScraperRouter(ScraperRegistry registry,
                         ScraperConfigService configService) {
        this.registry = registry;
        this.configService = configService;
    }

    public List<Product> scrape(String query, String source) {

        String type = configService.getType(source);

        ProductScraperAdapter adapter = registry.getAdapter(type);

        if (adapter == null) {
            System.out.println("❌ No adapter for type: " + type);
            return List.of();
        }

        return adapter.search(query, source);
    }
}