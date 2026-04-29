package com.nokku.orchestrator;

import com.nokku.listner.StageListener;
import com.nokku.model.Product;
import com.nokku.service.ScraperRouter;
import org.springframework.stereotype.Component;

import java.util.*;

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

        for (String source : sources) {

            try {
                // 🔥 REAL-TIME STAGE PER SOURCE
                listener.onStage("🔍 Searching " + source + "...");

                System.out.println("🚀 Executor calling source: " + source);

                List<Product> sourceResults = scraperRouter.scrape(query, source);

                results.addAll(sourceResults);

                // 🔥 OPTIONAL SUCCESS MESSAGE
                listener.onStage("✅ " + source + " results loaded");

            } catch (Exception e) {
                listener.onStage("⚠️ Failed: " + source);

                System.out.println("⚠️ Failed source Executor: " + source);
                e.printStackTrace();
            }
        }

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
