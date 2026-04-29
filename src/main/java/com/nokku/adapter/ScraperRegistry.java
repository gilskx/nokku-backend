package com.nokku.adapter;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScraperRegistry {

    private final Map<String, ProductScraperAdapter> adapterMap = new HashMap<>();

    public ScraperRegistry(List<ProductScraperAdapter> adapters) {

        System.out.println("🔥 Initializing ScraperRegistry...");

        for (ProductScraperAdapter adapter : adapters) {
            System.out.println("👉 Registering adapter: " + adapter.getType());
            adapterMap.put(adapter.getType(), adapter);
        }

        System.out.println("🔥 Registered adapters: " + adapterMap.keySet());
    }

    public ProductScraperAdapter getAdapter(String type) {
        return adapterMap.get(type);
    }
}