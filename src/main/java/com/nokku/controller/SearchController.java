package com.nokku.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokku.decision.BestProductSelector;
import com.nokku.decision.RecommendationService;
import com.nokku.model.Product;
import com.nokku.orchestrator.ShoppingAgent;
import com.nokku.orchestrator.model.AgentResponse;
import com.nokku.service.IntentService;
import com.nokku.service.ProductService;
import com.nokku.service.ScoringService;
import org.springframework.web.bind.annotation.*;
import com.nokku.service.AiRankingService;
import java.util.*;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import com.nokku.orchestrator.model.Plan;
import com.nokku.listner.StageListener;
import com.nokku.orchestrator.Executor;
import com.nokku.orchestrator.Planner;
import reactor.core.publisher.FluxSink;
import com.nokku.service.AiRankingService;
//@CrossOrigin(
//        origins = "http://localhost:3000",
//        allowedHeaders = "*",
//        methods = {RequestMethod.POST, RequestMethod.GET, RequestMethod.OPTIONS}
//)

@RestController
@RequestMapping("/search")
public class SearchController {

    private final IntentService intentService;
    private final ProductService productService;
    private final ScoringService scoringService;
    private final ShoppingAgent shoppingAgent;
    private final BestProductSelector selector;
    private final RecommendationService recommendationService;
    private final Planner planner;
    private final Executor executor;
    private final AiRankingService aiRankingService;
    public SearchController(IntentService intentService,
                            ProductService productService,
                            ScoringService scoringService,
                            ShoppingAgent shoppingAgent,
                            BestProductSelector selector,
                            RecommendationService recommendationService,
                            Planner planner,
                            Executor executor, AiRankingService aiRankingService) {

        this.intentService = intentService;
        this.productService = productService;
        this.scoringService = scoringService;
        this.shoppingAgent = shoppingAgent;
        this.selector = selector;
        this.recommendationService = recommendationService;

        this.planner = planner;
        this.executor = executor;
        this.aiRankingService = aiRankingService;
    }

    @PostMapping
    public Object search(@RequestBody Map<String, Object> request){

        // ✅ FIX 1: Safe query handling
        String query = Optional.ofNullable((String) request.get("query"))
                .orElse("")
                .trim()
                .toLowerCase();
        // 🌍 NEW: USER CONTEXT EXTRACTION
        Map<String, String> userContext = null;

        if (request.get("userContext") instanceof Map) {
            userContext = (Map<String, String>) request.get("userContext");
        }

        String country = userContext != null ? userContext.get("country") : "unknown";
        String city = userContext != null ? userContext.get("city") : "unknown"
                ;
        String ip = userContext != null ? userContext.get("ip") : "unknown";

        System.out.println("🌍 User Context → " + country + " | " + city + " | " + ip);

        System.out.println("🔍 Query: " + query);

        Map<String, Object> intent = intentService.parseIntent(query);

        List<Product> finalProducts = new ArrayList<>();
        Map<String, Object> aiMeta = new HashMap<>();

        // =========================================================
        // 🔥 STEP 1: AGENT FLOW
        // =========================================================
        try {
            System.out.println("🤖 Using ShoppingAgent...");

            AgentResponse agentResponse = shoppingAgent.handle(query);
            List<Product> agentResults = agentResponse.getProducts();

            if (agentResponse.getAiMeta() != null) {
                aiMeta = agentResponse.getAiMeta();
            }

            // ✅ FIX 2: relevance filter
            if (agentResults != null) {
                List<Product> matchedAgentResults = agentResults.stream()
                        .filter(p -> basicMatch(p, query))
                        .toList();

                if (matchedAgentResults.size() >= 5) {
                    agentResults = matchedAgentResults;
                } else {
                    System.out.println("⚠️ Agent basicMatch too strict, keeping original agent results");
                }
            }

            // ✅ FIX 3: price filter
            double minPrice = 0;
            double maxPrice = Double.MAX_VALUE;

            try {
                String numbers = query.replaceAll("[^0-9 ]", " ").trim();
                String[] parts = numbers.split("\\s+");

                if (query.contains("to") && parts.length >= 2) {
                    minPrice = Double.parseDouble(parts[0]);
                    maxPrice = Double.parseDouble(parts[1]);
                } else if (query.contains("under") && parts.length >= 1) {
                    maxPrice = Double.parseDouble(parts[0]);
                } else if (query.contains("above") && parts.length >= 1) {
                    minPrice = Double.parseDouble(parts[0]);
                }
            } catch (Exception ignored) {}

            double finalMinPrice = minPrice;
            double finalMaxPrice = maxPrice;
            if (agentResults != null) {
                List<Product> matchedAgentResults = agentResults.stream()
                        .filter(p -> basicMatch(p, query))
                        .toList();

                if (matchedAgentResults.size() >= 5) {
                    agentResults = matchedAgentResults;
                } else {
                    System.out.println("⚠️ Agent basicMatch too strict, keeping original agent results");
                }
            }

            if (agentResults != null && !agentResults.isEmpty()) {
                System.out.println("✅ Agent returned valid results");
                finalProducts = agentResults;
            } else {
                System.out.println("⚠️ Agent results invalid → fallback");
            }
            // 🌍 BOOST BASED ON LOCATION


        } catch (Exception e) {
            System.out.println("⚠️ Agent failed → fallback");
            e.printStackTrace();
        }

        // =========================================================
        // 🔽 STEP 2: PRODUCT SERVICE
        // =========================================================
        if (finalProducts.isEmpty()) {

            List<Product> products = productService.getProducts(intent);

            if (!products.isEmpty()) {
                System.out.println("✅ ProductService returned results");
                finalProducts = products;
            }
        }

        // =========================================================
        // 🔽 STEP 3: FINAL FALLBACK
        // =========================================================
        if (finalProducts.isEmpty()) {

            System.out.println("⚠️ Final fallback triggered");

            finalProducts = productService.getProductsWithoutPriceFilter(intent);

            if (finalProducts.isEmpty()) {
                return Map.of(
                        "message", "No products found",
                        "confidence", "LOW"
                );
            }
        }

        // =========================================================
        // 🔥 DECISION ENGINE
        // =========================================================
        // 🌍 LOCATION-AWARE ADJUSTMENT
        if ("US".equalsIgnoreCase(country)) {
            for (Product p : finalProducts) {
                if ("walmart".equalsIgnoreCase(p.getSource())) {
                    //  p.setTrustScore(p.getTrustScore() + 5); // boost
                }
            }
        }
        // 🌍 LOCATION-AWARE SCORING (BEST PLACE)


        Map<String, Object> selection = selector.select(finalProducts, intent);
        String explanation = recommendationService.explain(selection);

        double confidence = 0.5;
        String reason = explanation;
        Product cheaperAlternative = null;

        if (aiMeta != null) {

            if (aiMeta.get("confidence") instanceof Number) {
                confidence = ((Number) aiMeta.get("confidence")).doubleValue();
            }

            if (aiMeta.get("reason") instanceof String) {
                reason = (String) aiMeta.get("reason");
            }

            Product aiCheaper = null;

            if (aiMeta.get("cheaper") instanceof Product) {
                aiCheaper = (Product) aiMeta.get("cheaper");
            }

            if (aiCheaper != null &&
                    selection.get("bestPick") instanceof Product &&
                    aiCheaper.getPrice() > 0 &&
                    aiCheaper.getPrice() < ((Product) selection.get("bestPick")).getPrice()) {

                cheaperAlternative = aiCheaper;

            } else {
                cheaperAlternative = (Product) selection.get("cheapest");
            }

            if (cheaperAlternative == null && selection.get("cheapest") != null) {
                cheaperAlternative = (Product) selection.get("cheapest");
            }
        }

        Map<String, Object> response = new HashMap<>();

        response.put("products", finalProducts);
        response.put("recommendation", selection);
        response.put("explanation", explanation);
        response.put("confidence", confidence);
        response.put("reason", reason);
        response.put("cheaperAlternative", cheaperAlternative);

// 🔥 ADD THIS (CRITICAL FIX)
        if (aiMeta != null && !aiMeta.isEmpty()) {
            response.putAll(aiMeta);
        }

        return response;
    }

    // 🔥 NEW HELPER
    private boolean basicMatch(Product p, String query) {
        String name = p.getName() == null ? "" : p.getName().toLowerCase();

        for (String word : query.split("\\s+")) {
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }
    @GetMapping(value = "/search-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> searchStream(@RequestParam String query) {

return Flux.create(emitter -> {

            new Thread(() -> {

                try {

                    // 🔥 SAFE STAGE EMITTER
                    StageListener listener = stage -> {
                        try {
                            emitter.next(stage);
                        } catch (Exception ignored) {}
                    };

                    Map<String, Object> intent = intentService.parseIntent(query);

                    Plan plan = planner.createPlan(intent);
                    List<String> sources = plan.getSources();

                    List<Product> products = executor.execute(sources, intent, listener);

                    System.out.println("🔥 Controller PRODUCTS SIZE: " + products.size());

                    Map<String, Object> selection = new HashMap<>();
                    String explanation = "";

                    // 🔥 FIX 1: DECLARE aiMeta OUTSIDE
                    Map<String, Object> aiMeta = new HashMap<>();

                    try {
                        System.out.println("🔥 Before selector.select(products, intent");
                        selection = selector.select(products, intent);

                        if (selection == null || selection.isEmpty()) {

                            System.out.println("⚠️ Selector returned empty → fallback");

                            Product best = products.stream()
                                    .filter(p -> p.getPrice() > 0)
                                    .max(Comparator.comparingDouble(p -> p.getRating() * p.getTrustScore()))
                                    .orElse(null);

                            Product cheapest = products.stream()
                                    .filter(p -> p.getPrice() > 0)
                                    .min(Comparator.comparingDouble(Product::getPrice))
                                    .orElse(null);

                            selection = new HashMap<>();
                            selection.put("bestPick", best);
                            selection.put("cheapest", cheapest);
                        }

                        System.out.println("🔥 After selector");

                    } catch (Exception e) {
                        System.out.println("❌ Selector failed");
                        e.printStackTrace();
                    }

                    // 🔥 FINAL SAFETY
                    if (selection.get("bestPick") == null && !products.isEmpty()) {
                        selection.put("bestPick", products.get(0));
                    }

                    System.out.println("🔥 Before AI call");

                    try {

                        // 🔥 FIX 2: ASSIGN (NOT DECLARE)
                        aiMeta = aiRankingService.rankWithMeta(query, products);

                        // 🔥 override with AI
                        selection.put("bestPick", aiMeta.get("bestPick"));
                        selection.put("cheapest", aiMeta.get("cheaper"));

                        // 🔥 explanation
                        explanation = (String) aiMeta.getOrDefault(
                                "price_value_signal",
                                "Best value based on price and rating"
                        );

                        // 🔥 optional enrichment
                        selection.put("why_best_product", aiMeta.get("why_best_product"));
                        selection.put("why_cheaper_product", aiMeta.get("why_cheaper_product"));

                        System.out.println("🔥 After AI call: " + explanation);

                    } catch (Exception e) {
                        System.out.println("❌ AI failed → fallback");
                        explanation = "Best value based on price and rating";
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("products", products);
                    response.put("recommendation", selection);
                    response.put("explanation", explanation);
                    response.put("confidence", 0.8);
                    response.put("cheaperAlternative", selection.get("cheapest"));

                    // 🔥 FIX 3: ADD THESE (missing earlier)
                    response.put("who_should_buy_best", aiMeta.get("who_should_buy_best"));
                    response.put("who_should_buy_cheaper", aiMeta.get("who_should_buy_cheaper"));
                    System.out.println("🔥 Bye Cheapr"+aiMeta.get("who_should_buy_cheaper"));
                    ObjectMapper mapper = new ObjectMapper();
                    String json = mapper.writeValueAsString(response);

                    // 🔥 FINAL STAGE
                    emitter.next("⚡ Finalizing results...");

                    System.out.println("🔥 Controller DONE|" + json);

                    // 🔥 DONE EVENT
                    emitter.next("DONE|" + json);

                    System.out.println("🔥 DONE SENT");
                    System.out.println("🔥 SELECTION: " + selection);

                    emitter.complete();

                } catch (Exception e) {
                    e.printStackTrace();
                    emitter.error(e);
                }

            }).start();

        }, FluxSink.OverflowStrategy.BUFFER);
    }
}