package com.chifaa.backend.safety;

import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SafetyResourceService {
    public record Resource(String name, URI url) {}
    private final SafetyProperties properties;

    public SafetyResourceService(SafetyProperties properties) { this.properties = properties; }

    public List<Resource> resources(String language) {
        return properties.resources().stream()
                .filter(resource -> resource.language().equals(language))
                .map(resource -> new Resource(resource.name(),resource.url())).toList();
    }
}
