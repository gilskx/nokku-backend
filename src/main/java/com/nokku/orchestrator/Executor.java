package com.nokku.orchestrator;

import com.nokku.listner.StageListener;
import com.nokku.model.Product;
import com.nokku.service.ScraperRouter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class Executor {

    private final ScraperRouter scraperRouter;

    public Executor(ScraperRouter scraperRouter) {
        this.scraperRouter = scraperRouter;
    }

    public List<Product> execute(List<String> sources,
                                 Map<String, Object> intent,
                                 StageListener listener) {

        List<Product> results = new ArrayList<>();

        String query = intent.getOrDefault("rawQuery", "").toString();

        listener.onStage("🔍 Searching across stores...");



        List<CompletableFuture<List<Product>>> futures = new ArrayList<>();

        for (String source : sources) {

            // 🔥 Stage message (same as before)
            listener.onStage("🔍 Searching " + source + "...");

            CompletableFuture<List<Product>> future =
                    CompletableFuture.<List<Product>>supplyAsync(() -> {

                                try {
                                    long start = System.currentTimeMillis();

                                    System.out.println("🚀 Executor calling source: " + source);

                                    List<Product> sourceResults = scraperRouter.scrape(query, source);

                                    System.out.println("⏱ " + source + " took: " +
                                            (System.currentTimeMillis() - start) + " ms");

                                    // 🔥 success message
                                    listener.onStage("✅ " + source + " results loaded");

                                    return sourceResults;

                                } catch (Exception e) {
                                    listener.onStage("⚠️ Failed: " + source);
                                   // e.printStackTrace();
                                    System.out.println("Error in source: " + source + " -> " + e.getMessage());
                                    return Collections.emptyList();
                                }

                            })
                            .orTimeout(5, TimeUnit.SECONDS)   // ⏱️ HARD TIMEOUT
                            .handle((result, ex) -> {
                                if (ex != null) {
                                    listener.onStage("⚠️ Timeout: " + source);
                                    return Collections.<Product>emptyList();
                                }
                                return result;
                            });

            futures.add(future);
        }

// 🔥 WAIT FOR ALL (merge results)
        for (CompletableFuture<List<Product>> future : futures) {
            try {
                results.addAll(future.join());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
// ✅ STEP 1: Group by source (stability)
        Map<String, List<Product>> grouped = results.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Product::getSource));

// ✅ STEP 2: Balance results (avoid Amazon domination / missing sources)
        List<Product> balanced = new ArrayList<>();

        int perSourceLimit = 5; // tweak (3–5 ideal)

        for (Map.Entry<String, List<Product>> entry : grouped.entrySet()) {

            List<Product> list = entry.getValue();

            balanced.addAll(
                    list.stream()
                            .filter(p -> p.getPrice() > 0)// avoid bad data
                            .sorted(
                                    Comparator.comparing(Product::getPrice)
                                            .thenComparing(Product::getRating,
                                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            )
                            .limit(perSourceLimit)
                            .toList()
            );
        }

// ✅ STEP 3: Stable sorting (deterministic input to AI)
        balanced = balanced.stream()
                .sorted(
                        Comparator.comparing(Product::getPrice)
                                .thenComparing(Product::getRating,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                )
                .toList();

// 👉 Replace results with balanced list
        results = balanced;

//        for (String source : sources) {
//
//            try {
//                // 🔥 REAL-TIME STAGE PER SOURCE
//                listener.onStage("🔍 Searching " + source + "...");
//
//                System.out.println("🚀 Executor calling source: " + source);
//
//                List<Product> sourceResults = scraperRouter.scrape(query, source);
//
//                results.addAll(sourceResults);
//
//                // 🔥 OPTIONAL SUCCESS MESSAGE
//                listener.onStage("✅ " + source + " results loaded");
//
//            } catch (Exception e) {
//                listener.onStage("⚠️ Failed: " + source);
//
//                System.out.println("⚠️ Failed source Executor: " + source);
//                e.printStackTrace();
//            }
//        }

        // 🔥 POST PROCESSING STAGES
        listener.onStage("💰 Comparing prices...");
        listener.onStage("⭐ Checking ratings...");
        listener.onStage("🧠 Finding best value...");
        listener.onStage("⚡ Finalizing results...");

        return results.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<Product> execute(List<String> sources, Map<String, Object> intent) {
        return execute(sources, intent, stage -> {});
    }
}
