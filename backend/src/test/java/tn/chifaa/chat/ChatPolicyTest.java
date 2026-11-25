package tn.chifaa.chat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;

class ChatPolicyTest {
    @Test void absentOfficialRulesDisableSendingBeforePersistingAQuestion() {
        var sessions = mock(ChatRepository.class);
        var messages = mock(MessageRepository.class);
        var search = mock(KnowledgeSearchService.class);
        UUID user = UUID.randomUUID(), session = UUID.randomUUID();
        when(sessions.owned(user,session,true)).thenReturn(new ChatModels.Session(session,"App",null,null));
        var chat = new ChatService(sessions,messages,new ChatPolicy("KB-20261124",List.of(),Map.of()),search);
        var error = assertThrows(ResponseStatusException.class,
                () -> chat.ask(user,session,new ChatModels.Question("Test",null)));
        assertEquals(503,error.getStatusCode().value());
        verifyNoInteractions(messages,search);
    }

    @Test void matchesWholeConfiguredPhrasesAndKeepsHighestSeverity() {
        var high = new ChatPolicy.Trigger("signal test","Test 1",ChatPolicy.Severity.High);
        var critical = new ChatPolicy.Trigger("test ponctué","Test 2",ChatPolicy.Severity.Critical);
        var policy = new ChatPolicy("KB-20261124",List.of(high,critical),Map.of("fr","TEST"));
        assertEquals(critical,ChatText.distress(ChatText.normalize("SIGNAL test-ponctué !"),policy).orElseThrow());
        assertTrue(ChatText.distress(ChatText.normalize("signal tester"),policy).isEmpty());
        assertEquals("TEST",policy.resource(true));
    }
}
