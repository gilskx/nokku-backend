package com.nokku.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Service
public class EbayOAuthService {

    @Value("${ebay.client.id}")
    private String clientId;

    @Value("${ebay.client.secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    private String accessToken;
    private long expiryTime = 0;

    public String getAccessToken() {

        // 🔥 reuse token if valid
        if (accessToken != null && System.currentTimeMillis() < expiryTime) {
            return accessToken;
        }

        String auth = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        String url = "https://api.ebay.com/identity/v1/oauth2/token";

        String body = "grant_type=client_credentials&scope=https://api.ebay.com/oauth/api_scope";

        Map<String, Object> response = restTemplate.postForObject(
                url,
                new org.springframework.http.HttpEntity<>(
                        body,
                        new org.springframework.http.HttpHeaders() {{
                            set("Authorization", "Basic " + auth);
                            set("Content-Type", "application/x-www-form-urlencoded");
                        }}
                ),
                Map.class
        );

        accessToken = response.get("access_token").toString();

        int expiresIn = Integer.parseInt(response.get("expires_in").toString());

        expiryTime = System.currentTimeMillis() + (expiresIn - 60) * 1000L;

        System.out.println("🔐 New eBay OAuth token generated");

        return accessToken;
    }
}