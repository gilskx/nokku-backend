package com.nokku.controller;

import com.nokku.service.LinkResolverService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/resolve-link")
public class LinkResolverController {

    private final LinkResolverService linkResolverService;

    public LinkResolverController(LinkResolverService linkResolverService) {
        this.linkResolverService = linkResolverService;
    }

    @PostMapping
    public Map<String, String> resolve(@RequestBody Map<String, String> request) {

        String url = request.get("url");

        String resolvedUrl = linkResolverService.resolve(url);

        return Map.of(
                "originalUrl", url,
                "resolvedUrl", resolvedUrl
        );
    }
}