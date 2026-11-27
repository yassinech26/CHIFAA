package tn.chifaa.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("chifaa.chat")
record ChatPolicy(String kbVersion, List<Trigger> triggers, Map<String,String> resources) {
    enum Severity { Low, Medium, High, Critical }
    record Trigger(String phrase, String type, Severity severity) {}
    ChatPolicy {
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        resources = resources == null ? Map.of() : Map.copyOf(resources);
        for (Trigger trigger : triggers) {
            if (trigger.phrase() == null || ChatText.normalize(trigger.phrase()).isEmpty()
                    || trigger.type() == null || trigger.type().isBlank() || trigger.type().length() > 30
                    || trigger.severity() == null) throw new IllegalArgumentException("Invalid CHIFAA distress rule");
        }
    }
    boolean ready() { return !triggers.isEmpty() && kbVersion != null && kbVersion.matches("KB-[0-9]{8}"); }

    String resource(boolean arabic) {
        String preferred = resources.get(arabic ? "ar" : "fr");
        return preferred == null || preferred.isBlank() ? resources.get(arabic ? "fr" : "ar") : preferred;
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatPolicy.class)
class ChatPolicyConfiguration {}
