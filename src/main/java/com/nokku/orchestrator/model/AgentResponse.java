package com.nokku.orchestrator.model;

import com.nokku.model.Product;
import java.util.List;
import java.util.Map;

public class AgentResponse {

    private List<Product> products;
    private Map<String, Object> aiMeta;
    public AgentResponse(List<Product> products, Map<String, Object> aiMeta) {
        this.products = products;
        this.aiMeta = aiMeta;
    }

    public List<Product> getProducts() {
        return products;
    }
    public Map<String, Object> getAiMeta() {
        return aiMeta;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }
}
