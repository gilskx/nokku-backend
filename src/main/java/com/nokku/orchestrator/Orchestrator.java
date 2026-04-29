package com.nokku.orchestrator;

import com.nokku.model.Product;
import com.nokku.orchestrator.model.AgentResponse;
import com.nokku.service.IntentService;
import com.nokku.service.ProductService;
import com.nokku.service.ScoringService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Orchestrator {

    private final IntentService intentService;
    private final ProductService productService;
    private final ScoringService scoringService;

    public Orchestrator(IntentService intentService,
                        ProductService productService,
                        ScoringService scoringService) {
        this.intentService = intentService;
        this.productService = productService;
        this.scoringService = scoringService;
    }

    public AgentResponse handle(String query) {

        // ✅ Intent
        Map<String, Object> intent = intentService.parseIntent(query);

        // ✅ Product fetch
        List<Product> products = productService.getProducts(intent);

        // ⚠️ Future: scoring (unchanged)
        // products = scoringService.rank(products, intent);

        // 🔥 FIX: pass empty AI meta (no AI here)
        return new AgentResponse(products, new HashMap<>());
    }
}