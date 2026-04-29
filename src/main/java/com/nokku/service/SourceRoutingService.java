package com.nokku.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SourceRoutingService {

    public List<String> resolveSources(String brand) {

        if (brand == null) return List.of("amazon", "walmart");

        return switch (brand.toLowerCase()) {
            case "apple", "iphone" -> List.of("apple", "amazon", "walmart");
            case "samsung" -> List.of("samsung", "amazon", "walmart");
            default -> List.of("amazon", "walmart");
        };
    }
}