package com.nokku.service;

import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

@Service
public class LinkResolverService {

    public String resolve(String inputUrl) {

        if (inputUrl == null || inputUrl.isBlank()) {
            return "";
        }

        try {
            String currentUrl = inputUrl;

            for (int i = 0; i < 5; i++) {

                URL url = URI.create(currentUrl).toURL();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                int status = connection.getResponseCode();

                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 ||
                        status == 308) {

                    String location = connection.getHeaderField("Location");

                    if (location == null || location.isBlank()) {
                        return currentUrl;
                    }

                    if (!location.startsWith("http")) {
                        URI base = URI.create(currentUrl);
                        location = base.resolve(location).toString();
                    }

                    currentUrl = location;
                } else {
                    return currentUrl;
                }
            }

            return currentUrl;

        } catch (Exception e) {
            System.out.println("⚠️ Link resolve failed: " + e.getMessage());
            return inputUrl;
        }
    }
}