package com.nokku.orchestrator;

import com.nokku.model.Product;
import com.nokku.orchestrator.model.AgentResponse;
import com.nokku.orchestrator.model.Plan;
import com.nokku.service.AiRankingService;
import com.nokku.service.IntentService;
import com.nokku.service.PostProcessingService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShoppingAgent {

    private final IntentService intentService;
    private final Planner planner;
    private final Executor executor;
    private final PostProcessingService postProcessor;
    private final AiRankingService aiRankingService;

    public ShoppingAgent(IntentService intentService,
                         Planner planner,
                         Executor executor,
                         PostProcessingService postProcessor,
                         AiRankingService aiRankingService) {
        this.intentService = intentService;
        this.planner = planner;
        this.executor = executor;
        this.postProcessor = postProcessor;
        this.aiRankingService = aiRankingService;
    }

    public AgentResponse handle(String query) {

        // 🔥 Step 1: Intent
        Map<String, Object> intent = intentService.parseIntent(query);

        // 🔥 Step 2: Plan (sources)
        Plan plan = planner.createPlan(intent);

        // 🔥 Step 3: Execute scraping
        List<Product> raw = executor.execute(plan.getSources(), intent);

        // 🔥 Step 4: Post-processing pipeline
        List<Product> deduped = postProcessor.dedupe(raw);

        List<Product> filtered = postProcessor.tokenFilter(deduped, query);

        List<Product> noJunk = postProcessor.removeJunk(filtered);

        List<Product> balanced = postProcessor.balance(noJunk);

        // 🔥 Step 5: AI ranking (existing)
        Map<String, Object> aiResult = aiRankingService.rankWithMeta(query, balanced);

        List<Product> ranked = (List<Product>) aiResult.get("ranked");
        return new AgentResponse(ranked, aiResult);
    }
}