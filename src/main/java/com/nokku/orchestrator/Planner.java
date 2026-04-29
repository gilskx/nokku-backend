package com.nokku.orchestrator;

import com.nokku.orchestrator.model.Plan;
import com.nokku.service.ScraperConfigService;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Planner {

    private final ScraperConfigService configService;

    public Planner(ScraperConfigService configService) {
        this.configService = configService;
    }

    public Plan createPlan(Map<String, Object> intent) {

        double max = ((Number) intent.getOrDefault("maxPrice", Double.MAX_VALUE)).doubleValue();

        List<Map<String, Object>> rules = configService.getSelectionRules();

        List<String> sources = new ArrayList<>();
        List<String> defaultSources = new ArrayList<>();

        for (Map<String, Object> rule : rules) {

            Object conditionObj = rule.get("condition");

            if (!(conditionObj instanceof Map)) continue;

            Map<String, Object> condition = (Map<String, Object>) conditionObj;

            // 🔥 Handle default separately
            if (Boolean.TRUE.equals(condition.get("default"))) {
                defaultSources = (List<String>) rule.get("sources");
                continue;
            }

            // 🔥 Match maxPrice rule
            if (condition.containsKey("maxPrice")) {
                try {
                    double ruleMax = Double.parseDouble(condition.get("maxPrice").toString());

                    if (max <= ruleMax) {
                        sources = (List<String>) rule.get("sources");
                        break;
                    }

                } catch (Exception ignored) {}
            }
        }

        // 🔥 FIX: fallback to default rules
        if (sources.isEmpty() && !defaultSources.isEmpty()) {
            sources = defaultSources;
        }

        // 🔥 FIX: ultimate fallback → all sources
        if (sources.isEmpty()) {
            sources = new ArrayList<>(configService.getAllSources());
        }

        System.out.println("🧠 Planner selected sources: " + sources);

        return new Plan(sources, 0, max);
    }
}