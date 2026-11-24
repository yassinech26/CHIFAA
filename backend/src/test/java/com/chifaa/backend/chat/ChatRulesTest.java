package com.chifaa.backend.chat;

import com.chifaa.backend.safety.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatRulesTest {
    @Test
    void distressRecognizesDemonstrationPhrasesAcrossLanguagesAndHonorsPriority() {
        var properties = new SafetyProperties(List.of(
            new SafetyProperties.Rule("Scénario détresse démo","DEMO_DISTRESS","Medium"),
            new SafetyProperties.Rule("اختبار ضيق تجريبي","DEMO_DISTRESS","High"),
            new SafetyProperties.Rule("ما نجم اختبار","DEMO_DISTRESS","Critical")), List.of());
        var detector = new DistressDetectionService(properties);
        assertEquals("Medium",detector.detect(TopicDetectionService.normalize("SCÉNARIO, détresse démo !")).orElseThrow().severity());
        assertEquals("High",detector.detect(TopicDetectionService.normalize("اختبار ضيق تجريبي")).orElseThrow().severity());
        assertEquals("Critical",detector.detect(TopicDetectionService.normalize("اختبار ضيق تجريبي، ما نجم اختبار")).orElseThrow().severity());
        assertTrue(detector.detect(TopicDetectionService.normalize("Personne n'a configuré ce scénario.")).isEmpty());
    }

    @Test
    void detectsLanguageAndBlocksMedicalAdviceRequests() {
        var language = new LanguageDetectionService();
        assertEquals("French",language.detect("Bonjour"));
        assertEquals("MSA",language.detect("موعد المراجعة"));
        assertEquals("Darija",language.detect("شنو الموعد"));
        assertEquals("Mixed",language.detect("Bonjour موعد"));
        var boundary = new MedicalBoundaryService();
        assertTrue(boundary.blocks(TopicDetectionService.normalize("Quelle est la posologie ?")));
        assertTrue(boundary.blocks(TopicDetectionService.normalize("كم حبة")));
        assertFalse(boundary.blocks(TopicDetectionService.normalize("Transport vers le rendez-vous")));
    }
}
