package com.nokku.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokku.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class GoogleShoppingScraper {

    @Value("${serpapi.key}")
    private String serpApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Product> search(String query) {

        List<Product> products = new ArrayList<>();

        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://serpapi.com/search.json")
                    .queryParam("engine", "google_shopping")
                    .queryParam("q", query)
                    .queryParam("direct_link", "true")
                    .queryParam("api_key", serpApiKey)
                    .build()
                    .encode()
                    .toUriString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode shoppingResults = root.path("shopping_results");

            if (!shoppingResults.isArray()) {
                System.out.println("No Google Shopping results found");
                return products;
            }

            for (JsonNode item : shoppingResults) {

                String name = item.path("title").asText("");
                double price = item.path("extracted_price").asDouble(0.0);

                String link = item.path("direct_link").asText("");

                JsonNode offers = item.path("offers");
                if ((link == null || link.isBlank()) && offers.isArray() && offers.size() > 0) {
                    JsonNode firstOffer = offers.get(0);

                    String offerDirectLink = firstOffer.path("direct_link").asText("");
                    String offerLink = firstOffer.path("link").asText("");

                    if (offerDirectLink != null && !offerDirectLink.isBlank()) {
                        link = offerDirectLink;
                    } else if (offerLink != null && !offerLink.isBlank()) {
                        link = offerLink;
                    }
                }

                if (link == null || link.isBlank()) {
                    link = item.path("product_link").asText("");
                }

                if (link == null || link.isBlank()) {
                    link = item.path("link").asText("");
                }

                if (link != null && !link.isBlank() && !link.startsWith("http")) {
                    link = "https://" + link;
                }


                String image = item.path("thumbnail").asText("");
                double rating = item.path("rating").asDouble(3.5);
                int reviews = item.path("reviews").asInt(100);

                String providerName = item.path("source").asText(
                        item.path("store").asText("google-shopping")
                );

                if (providerName == null || providerName.isBlank()) {
                    providerName = "google-shopping";
                }

                if (name.isEmpty() || price <= 0 || link == null || link.isBlank()) {
                    System.out.println("⚠️ Skipping Google item: name=" + name + ", price=" + price + ", link=" + link);
                    continue;
                }

                System.out.println("🛒 Google item: " + providerName + " | " + name + " | " + link);

                Product product = new Product(
                        name,
                        price,
                        rating,
                        providerName,
                        link,
                        image
                );

                product.setReviewCount(reviews);

                products.add(product);
            }

            System.out.println("Google Shopping results: " + products.size());

        } catch (Exception e) {
            System.out.println("Google Shopping error: " + e.getMessage());
        }

        return products;
    }
}