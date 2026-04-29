package com.nokku.adapter;

import com.nokku.model.Product;
import com.nokku.service.EbayApiService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EbayApiAdapter implements ProductScraperAdapter {

    private final EbayApiService service;

    public EbayApiAdapter(EbayApiService service) {
        System.out.println("🔥 EbayApiAdapter created");
        this.service = service;
    }

    @Override
    public String getType() {
        return "api";
    }

    @Override
    public List<Product> search(String query, String source) {
        return service.search(query);
    }
}