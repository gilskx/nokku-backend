package com.nokku.decision;

import com.nokku.model.Product;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RecommendationService {

    public String explain(Map<String, Object> selection) {

        Product best = (Product) selection.get("bestDeal");

        if (best == null) return "No recommendation available";

        return "Best deal is " + best.getName() +
                " priced at $" + best.getPrice() +
                " from " + best.getSource() +
                " because it offers lowest price.";
    }
}
