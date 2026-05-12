package com.nokku.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokku.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
@Service
public class AiRankingService {

    @Value("${openai.api.key}")
    private String API_KEY;

    private final ObjectMapper mapper = new ObjectMapper();

    // =========================================================
    // 🔥 SIMPLE RANK (for UI list)
    // =========================================================
    public List<Product> rank(String query, List<Product> products) {

        Map<String, Object> result = rankWithMeta(query, products);

        return (List<Product>) result.getOrDefault("ranked", products);
    }

    // =========================================================
    // 🔥 MAIN AI METHOD (WITH META)
    // =========================================================
    public Map<String, Object> rankWithMeta(String query, List<Product> products) {

        Map<String, Object> result = new HashMap<>();

        try {
            // ✅ Remove invalid price products first
            products = products.stream()
                    .filter(p -> p.getPrice() > 0)
                    .toList();

// ✅ Dynamic min/max from current dataset
            double minPrice = products.stream()
                    .mapToDouble(Product::getPrice)
                    .min()
                    .orElse(0);

            double maxPrice = products.stream()
                    .mapToDouble(Product::getPrice)
                    .max()
                    .orElse(0);

// ✅ Stable deterministic scoring
            products = products.stream()
                    .sorted((a, b) -> Double.compare(
                            calculateScore(b, minPrice, maxPrice),
                            calculateScore(a, minPrice, maxPrice)
                    ))
                    .limit(30) // optional safety limit
                    .toList();
            String prompt = buildPrompt(query, products);

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + API_KEY);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("temperature", 0.0);
            body.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = mapper.readTree(response.getBody());

            String content = root
                    .path("choices").get(0)
                    .path("message").path("content")
                    .asText("");

            System.out.println("🤖 AI RAW RESPONSE: " + content);

            return parse(products, content);

        } catch (Exception e) {
            System.out.println("⚠️ AI failed → fallback");
            return safeDefault(products);
        }
    }

    // =========================================================
    // 🔥 PROMPT
    // =========================================================
    private String buildPrompt(String query, List<Product> products){

    StringBuilder sb = new StringBuilder();

    // ✅ Intent detection (safe + clean)
    String intent = Optional.ofNullable(detectUserIntent(query))
            .filter(i -> !i.isBlank())
            .orElse("GENERAL");

        sb.append("User intent: ").append(intent).append("\n\n");

        sb.append("You are nokku, an AI shopping assistant that helps users find the best product quickly.\n");
        sb.append("nokku is fast, practical, and avoids unnecessary explanations.\n\n");
        sb.append("User query: ").append(query).append("\n\n");

        sb.append("Select the BEST product (based on user intent) and one CHEAPER alternative from the list below.\n\n");

    // ===================== BASE RULES =====================
        sb.append("Rules:\n");
        sb.append("- Prefer high rating\n");
        sb.append("- Prefer better value (price vs quality)\n");
        sb.append("- Treat all sources equally\n");
        sb.append("- Avoid unknown marketplace sellers unless rating is significantly higher or price is much lower\n");
        sb.append("- Prefer OFFICIAL / RETAIL sources over marketplace and Choose best based on price, rating, and value\n");
        sb.append("- Consider OTHER sources if competitive\n");
        sb.append("- Avoid refurbished or used items\n");
        sb.append("- For electronics, prefer better usage suitability (gaming, heavy use, etc.)\n\n");

    // ===================== INTENT GUIDANCE =====================
        sb.append("Intent-specific guidance:\n");

        switch (intent) {
        case "BUDGET":
            sb.append("- Strongly prioritize LOWER PRICE\n");
            sb.append("- Accept slightly lower rating if price difference is significant\n");
            sb.append("- Cheaper option should be very compelling\n\n");
            break;

        case "PERFORMANCE":
            sb.append("- Prioritize performance suitability and ratings\n");
            sb.append("- Price is secondary if performance gain is clear\n");
            sb.append("- Best product should clearly suit heavy usage / gaming / demanding tasks\n\n");
            break;

        case "PREMIUM":
            sb.append("- Prioritize highest quality, brand, and rating\n");
            sb.append("- Ignore small price differences\n");
            sb.append("- Best product should reflect premium choice\n\n");
            break;

        case "GENERAL_BROAD":   // 🔥 NEW
            sb.append("- Query is broad (e.g., TV, phone)\n");
            sb.append("- Choose most popular or highest rated product\n");
            sb.append("- Ensure recommendation is still useful\n\n");
            break;

        default:
            sb.append("- Balance price, rating, and value\n\n");
    }

    // ===================== HARD CONSTRAINTS =====================
        sb.append("""
HARD CONSTRAINTS:

- If intent is BUDGET:
  bestPickIndex MUST NOT be the most expensive product
  cheaperIndex should offer strong savings

- If intent is PERFORMANCE:
  bestPickIndex MUST have highest rating OR best usage suitability

- If intent is PREMIUM:
  bestPickIndex should be top-rated or premium brand

""");

    // ===================== VALIDATION =====================
        sb.append("""
VALIDATION RULES:

- cheaperIndex MUST be different from bestPickIndex
- cheaperIndex MUST have lower price than bestPickIndex
- If no cheaper product exists, pick closest lower priced option

""");

    // ===================== PRICE LOGIC =====================
        sb.append("""
PRICE COMPARISON RULES:

- Always compare price difference numerically
- Mention exact dollar difference when possible

""");

    // ===================== DATA QUALITY =====================
        sb.append("""
DATA QUALITY RULES:

- Ignore products with missing or zero price
- Ignore products with extremely low rating (< 3.0) unless no better option
- If rating is missing, treat cautiously

""");

    // ===================== SOURCE PRIORITY =====================
        sb.append("""
SOURCE PRIORITY:

- Prefer OFFICIAL / RETAIL sources over marketplace
- Marketplace only if price advantage is significant

""");

    // ===================== DIVERSITY =====================
        sb.append("""
DIVERSITY RULE:

- Ensure best product and cheaper product are meaningfully different
- Avoid selecting nearly identical products unless unavoidable

""");

    // ===================== TIE BREAK =====================
        sb.append("""
TIE-BREAKING RULE:

- If multiple products are similar:
  - Prefer higher rating
  - Then prefer lower price
  - Then prefer trusted source

""");

    // ===================== DECISION QUALITY =====================
        sb.append("""
DECISION QUALITY:

- Think step-by-step before selecting best and cheaper product
- Ensure recommendation is logical and consistent

""");

    // ===================== 🔥 NEW FIX BLOCK =====================
        sb.append("""
GENERIC QUERY HANDLING:

- If user query is broad (e.g., "tv", "phone", "laptop"):
  - STILL MUST select a bestPickIndex
  - Choose based on:
    - highest rating
    - good price-value balance
  - DO NOT return empty response

FINAL SAFETY RULE:

- NEVER return empty or invalid response
- ALWAYS return bestPickIndex and cheaperIndex

""");

    // ===================== PRODUCTS =====================
        sb.append("\nProducts:\n");

    int i = 1;
        for (Product p : products) {
        sb.append(i++).append(". ")
                .append(p.getName())
                .append(" | price: ").append(p.getPrice())
                .append(" | rating: ").append(p.getRating())
                .append(" | source: ").append(p.getSource())
                .append(" | type: ").append(getSourceType(p.getSource()))
                .append("\n");
    }

    // ===================== OUTPUT CONTROL =====================
        sb.append("""
CRITICAL OUTPUT RULES:

- Return ONLY valid JSON
- Do NOT include explanations, markdown, or extra text
- Do NOT prefix or suffix anything outside JSON
- Response must be directly parsable

""");

        sb.append("""

Now generate intelligent decision insights.

IMPORTANT:
- Keep everything SHORT and PRACTICAL
- Avoid generic statements like "good value" unless justified
- Tailor insights to the actual products

STRICT QUALITY RULES:

- DO NOT use generic phrases like:
  "good value", "high quality", "best product"

- Each bullet must reference:
  - price difference OR
  - rating OR
  - usage scenario

OUTPUT FORMAT (STRICT JSON ONLY):

{
  "bestPickIndex": 1,
  "cheaperIndex": 2,
  "why_best_product": ["..."],
  "why_cheaper_product": ["..."],
  "who_should_buy_best": ["..."],
  "who_should_buy_cheaper": ["..."],
  "price_value_signal": "...",
  "key_difference": "..."
}

""");

        return sb.toString();
}
// =========================================================
// 🔥 SOURCE TYPE
// =========================================================
private String getSourceType(String source) {

    if (source == null) return "OTHER";

    source = source.toLowerCase();

    if (source.contains("amazon") ||
            source.contains("walmart") ||
            source.contains("target") ||
            source.contains("bestbuy") ||
            source.contains("google-shopping")) {
        return "TRUSTED";
    }

    if (source.contains("ebay")) {
        return "MARKETPLACE";
    }

    return "OTHER";
}
    private boolean isRelevantProduct(String bestName, Product candidate) {

        if (candidate == null || candidate.getName() == null) {
            return false;
        }

        String name = candidate.getName().toLowerCase();

        // 🚫 Reject obvious junk
        if (name.contains("magazine") ||
                name.contains("report") ||
                name.contains("pdf") ||
                name.contains("book") ||
                name.contains("guide") ||
                name.contains("manual")) {

            return false;
        }

        // ✅ Very lightweight category similarity
        return containsLaptopKeyword(name);
    }
    private double calculateScore(Product p,
                                  double minPrice,
                                  double maxPrice) {

        // ✅ Rating weight
        double ratingScore = p.getRating() * 0.6;

        // ✅ Relative price score (normalized)
        double priceScore = 0;

        if (maxPrice > minPrice) {

            priceScore =
                    (maxPrice - p.getPrice()) /
                            (maxPrice - minPrice);

            priceScore *= 0.3;
        }

        // ✅ Source trust
        double trustScore = switch (getSourceType(p.getSource())) {
            case "TRUSTED" -> 0.1;
            case "MARKETPLACE" -> 0.03;
            default -> 0.05;
        };

        return ratingScore + priceScore + trustScore;
    }
    private boolean containsLaptopKeyword(String text) {

        return text.contains("laptop") ||
                text.contains("macbook") ||
                text.contains("notebook") ||
                text.contains("thinkpad") ||
                text.contains("chromebook");
    }

    private String querySafe(Product p) {
        return p != null && p.getName() != null
                ? p.getName().toLowerCase()
                : "";
    }
// =========================================================
// 🔥 AI RESPONSE PARSER (NEW CORE)
// =========================================================
//private Map<String, Object> parseAIResponse(List<Product> products, String content) {
//
//    Map<String, Object> result = new HashMap<>();
//
//    try {
//
//        int start = content.indexOf("{");
//        int end = content.lastIndexOf("}");
//
//        if (start == -1 || end == -1) {
//            // return fallback(products);
//            return safeDefault(products);
//        }
//
//        String json = content.substring(start, end + 1);
//        JsonNode node = mapper.readTree(json);
//        int bestIndex = node.path("bestPickIndex").asInt(1) - 1;
//        int cheaperIndex = node.path("cheaperIndex").asInt(2) - 1;
//
//        if (bestIndex < 0 || bestIndex >= products.size()) bestIndex = 0;
//        if (cheaperIndex < 0 || cheaperIndex >= products.size()) cheaperIndex = 1;
//
//        Product best = products.get(bestIndex);
//        Product cheaper = products.get(cheaperIndex);
//
//// SAFETY
//        if (cheaper == null || cheaper.getPrice() >= best.getPrice()) {
//            cheaper = products.stream()
//                    .filter(p -> p.getPrice() > 0 && p.getPrice() < best.getPrice())
//                    .findFirst()
//                    .orElse(null);
//        }
//
//// BUILD RANKED
//        List<Product> ranked = new ArrayList<>();
//
//// 1. Best first
//        ranked.add(best);
//
//// 2. Group remaining by source
//        Map<String, List<Product>> grouped = products.stream()
//                .collect(Collectors.groupingBy(Product::getSource, LinkedHashMap::new, Collectors.toList()));// 3. Round-robin merge (balanced suggestions)
//        int i = 0;
//        boolean added;
//
//        do {
//            added = false;
//            for (List<Product> list : grouped.values()) {
//                if (i < list.size()) {
//                    ranked.add(list.get(i));
//                    added = true;
//                }
//            }
//            i++;
//        } while (added);
//
//
//// RESULT
//        result.put("ranked", ranked);
//        result.put("bestPick", best);
//        result.put("cheaper", cheaper);
//
//        result.put("quick_decision", node.path("quick_decision").asText(""));
//
//        result.put("why_best_product",
//                node.has("why_best_product")
//                        ? mapper.convertValue(node.get("why_best_product"), List.class)
//                        : List.of());
//
//        result.put("why_best_product",
//                node.has("why_best_product") && node.get("why_best_product").isArray()
//                        ? mapper.convertValue(node.get("why_best_product"), List.class)
//                        : List.of());
//
//        result.put("why_cheaper_product",
//                node.has("why_cheaper_product") && node.get("why_cheaper_product").isArray()
//                        ? mapper.convertValue(node.get("why_cheaper_product"), List.class)
//                        : List.of());
//
//        result.put("who_should_buy_best",
//                node.has("who_should_buy_best") && node.get("who_should_buy_best").isArray()
//                        ? mapper.convertValue(node.get("who_should_buy_best"), List.class)
//                        : List.of());
//
//        result.put("who_should_buy_cheaper",
//                node.has("who_should_buy_cheaper") && node.get("who_should_buy_cheaper").isArray()
//                        ? mapper.convertValue(node.get("who_should_buy_cheaper"), List.class)
//                        : List.of());
//
//        result.put("price_value_signal", node.path("price_value_signal").asText(""));
//        result.put("similarity_warning", node.path("similarity_warning").asText(""));
//        result.put("key_difference", node.path("key_difference").asText(""));
//
//        //Fall Back
//
//        List<String> defaultBest = List.of(
//                "Good balance of price and performance",
//                "Higher user ratings compared to alternatives",
//                "Reliable option for most users"
//        );
//
//        List<String> defaultCheaper = List.of(
//                "Lower price makes it budget friendly",
//                "Suitable for basic usage",
//                "Decent value for the price"
//        );
//
//        List<String> defaultBestUsers = List.of(
//                "Users looking for better performance",
//                "Long-term usage"
//        );
//
//        List<String> defaultCheapUsers = List.of(
//                "Budget-conscious users",
//                "Light or occasional usage"
//        );
//
//        return result;
//
//    } catch (Exception e) {
//        return fallback(products);
//    }
//}



    // =========================================================
    // 🔥 PARSER
    // =========================================================
    private Map<String, Object> parse(List<Product> products, String content) {

        Map<String, Object> result = new HashMap<>();

        try {
            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");

            if (start == -1 || end == -1) {
                return safeDefault(products);
            }

            JsonNode node = mapper.readTree(content.substring(start, end + 1));

            int bestIdx = node.path("bestPickIndex").asInt(1) - 1;
            int cheapIdx = node.path("cheaperIndex").asInt(2) - 1;

            if (bestIdx < 0 || bestIdx >= products.size()) bestIdx = 0;
            if (cheapIdx < 0 || cheapIdx >= products.size()) cheapIdx = 1;

            Product best = products.get(bestIdx);
            Product cheaper = null;

// ✅ First trust AI cheaperIndex
            if (cheapIdx >= 0 &&
                    cheapIdx < products.size() &&
                    cheapIdx != bestIdx) {

                Product aiCheap = products.get(cheapIdx);

                // ✅ Basic validation
                if (aiCheap.getPrice() > 0 &&
                        aiCheap.getPrice() < best.getPrice() &&
                        isRelevantProduct(querySafe(best), aiCheap)) {

                    cheaper = aiCheap;
                }
            }

// ✅ Fallback logic (existing behavior preserved)
            if (cheaper == null) {

                cheaper = products.stream()
                        .filter(p ->
                                p.getPrice() > 0 &&
                                        p.getPrice() < best.getPrice() &&
                                        isRelevantProduct(querySafe(best), p)
                        )
                        .min(Comparator.comparingDouble(Product::getPrice))
                        .orElse(null);
            }

            // 🔥 RANKED LIST (NO BIAS)
            List<Product> ranked = new ArrayList<>();

// 1. Best first
            ranked.add(best);

// 2. Group by source
            Map<String, List<Product>> grouped = products.stream()
                    .collect(Collectors.groupingBy(Product::getSource, LinkedHashMap::new, Collectors.toList()));
            System.out.println("🧪 [AI INPUT PRODUCTS - before list.remove(best);]");
            products.stream().limit(20)
                    .forEach(p -> System.out.println("SRC=" + p.getSource()));
// ✅ FIX 1 — remove BEST from all lists
            for (List<Product> list : grouped.values()) {
                list.remove(best);
            }

// ✅ FIX 2 — randomize source order (avoid Amazon always first)
            // Convert grouped map to list
            List<List<Product>> lists = new ArrayList<>(grouped.values());

// Sort by list size ASC (small sources first → fairness)
           // lists.sort(Comparator.comparingInt(List::size));
          //  Collections.shuffle(lists);
            lists.sort(Comparator.comparing(
                    l -> l.isEmpty() ? "" : l.get(0).getSource()
            ));
// ✅ FIX 3 — prevent one source domination
            Map<String, Integer> sourceCount = new HashMap<>();
            int MIN_PER_SOURCE = 4;
            int MAX_PER_SOURCE = 20; // keep your existing cap

            System.out.println("🧪 [AI INPUT PRODUCTS] MAX_PER_SOURCE = 20    ");
            products.stream().limit(20)
                    .forEach(p -> System.out.println("SRC=" + p.getSource()));
// 🔥 STEP 1 — Guarantee minimum per source
            for (List<Product> list : lists) {

                int count = 0;

                for (Product p : list) {

                    if (count >= MIN_PER_SOURCE) break;

                    if (!ranked.contains(p)) {
                        ranked.add(p);
                        count++;
                    }
                }
            }
// 3. Round-rorequestBody.put("temperature", 0.2);bin merge (correct)
            int i = 0;
            boolean added;

            do {
                added = false;

                for (List<Product> list : lists) {

                    if (i < list.size()) {

                        Product p = list.get(i);
                        String src = p.getSource();

                        // ✅ FIX 4 — dedup + per-source cap
                        if (!ranked.contains(p) &&
                                sourceCount.getOrDefault(src, 0) < MAX_PER_SOURCE) {

                            ranked.add(p);
                            sourceCount.put(src, sourceCount.getOrDefault(src, 0) + 1);
                        }

                        added = true;
                    }
                }

                i++;

            } while (added);

            result.put("ranked", ranked);
            result.put("bestPick", best);
            result.put("cheaper", cheaper);
            result.put("price_value_signal",
                    node.path("price_value_signal").asText("Balanced choice"));
            System.out.println("🧪 [AI RANKED OUTPUT]");
            ranked.stream().limit(20)
                    .forEach(p -> System.out.println("SRC=" + p.getSource()));

// 🔥 ADD THIS (CRITICAL FIX)

            result.put("why_best_product",
                    node.has("why_best_product") && node.get("why_best_product").isArray()
                            ? mapper.convertValue(node.get("why_best_product"), List.class)
                            : List.of());

            result.put("why_cheaper_product",
                    node.has("why_cheaper_product") && node.get("why_cheaper_product").isArray()
                            ? mapper.convertValue(node.get("why_cheaper_product"), List.class)
                            : List.of());

            result.put("who_should_buy_best",
                    node.has("who_should_buy_best") && node.get("who_should_buy_best").isArray()
                            ? mapper.convertValue(node.get("who_should_buy_best"), List.class)
                            : List.of());

            result.put("who_should_buy_cheaper",
                    node.has("who_should_buy_cheaper") && node.get("who_should_buy_cheaper").isArray()
                            ? mapper.convertValue(node.get("who_should_buy_cheaper"), List.class)
                            : List.of());


            return result;

        } catch (Exception e) {
            return safeDefault(products);
        }
    }

    // =========================================================
    // 🔥 SAFE DEFAULT
    // =========================================================
    private Map<String, Object> safeDefault(List<Product> products) {

        Map<String, Object> result = new HashMap<>();

        if (products == null || products.isEmpty()) return result;

        Product best = products.stream()
                .max(Comparator.comparingDouble(Product::getRating))
                .orElse(products.get(0));

        Product cheaper = products.stream()
                .filter(p -> p.getPrice() > 0 &&
                        p.getPrice() < best.getPrice() &&
                        !p.getSource().equalsIgnoreCase(best.getSource()))
                .min(Comparator.comparingDouble(Product::getPrice))
                .orElse(null);

        result.put("ranked", products);
        result.put("bestPick", best);
        result.put("cheaper", cheaper);
        result.put("price_value_signal", "Fallback selection");

        return result;
    }
    private String detectUserIntent(String query) {

        query = query.toLowerCase();

        if (query.contains("cheap") || query.contains("budget") || query.contains("under")) {
            return "BUDGET";
        }

        if (query.contains("gaming") || query.contains("performance") || query.contains("fast")) {
            return "PERFORMANCE";
        }

        if (query.contains("best") || query.contains("premium") || query.contains("top")) {
            return "PREMIUM";
        }
        if (query.contains("cheap") || query.contains("budget") ||
                query.contains("under") || query.contains("below") ||
                query.contains("less than")) {
            return "BUDGET";
        }
        if (query != null && query.trim().length() <= 3) {
            return "GENERAL_BROAD";
        }
        return "GENERAL";
    }
    // =========================================================
    // 🔥 FALLBACK
    // =========================================================
    private Map<String, Object> fallback(List<Product> products) {

        Map<String, Object> result = new HashMap<>();

        result.put("ranked", products);
        result.put("confidence", 0.4);
        result.put("reason", "Fallback ranking based on system logic");
        result.put("cheaper", null);

        return result;
    }
}