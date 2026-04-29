package com.nokku.orchestrator.model;

import java.util.List;
public class Plan {

    private List<String> sources;
    private double minPrice;
    private double maxPrice;

    public Plan(List<String> sources, double minPrice, double maxPrice) {
        this.sources = sources;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public List<String> getSources() { return sources; }
    public double getMinPrice() { return minPrice; }
    public double getMaxPrice() { return maxPrice; }
}