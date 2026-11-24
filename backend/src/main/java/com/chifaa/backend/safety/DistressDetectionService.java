package com.chifaa.backend.safety;

import java.util.*;
import com.chifaa.backend.chat.TopicDetectionService;
import org.springframework.stereotype.Service;

@Service
public class DistressDetectionService {
    public record Trigger(String type, String severity) {}
    private record Rule(String phrase, Trigger trigger) {}
    private final List<Rule> rules;

    public DistressDetectionService(SafetyProperties properties) {
        this.rules = properties.rules().stream().map(rule -> new Rule(TopicDetectionService.normalize(rule.phrase()),
                new Trigger(rule.type(),rule.severity()))).filter(rule -> !rule.phrase().isEmpty()).toList();
    }

    public Optional<Trigger> detect(String normalizedMessage) {
        String padded = " " + normalizedMessage + " ";
        var priorities = List.of("Low","Medium","High","Critical");
        return rules.stream().filter(rule -> padded.contains(" " + rule.phrase() + " "))
                .map(Rule::trigger).max(Comparator.comparingInt(trigger -> priorities.indexOf(trigger.severity())));
    }
}
