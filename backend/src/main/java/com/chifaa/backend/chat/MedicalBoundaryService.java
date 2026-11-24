package com.chifaa.backend.chat;
import org.springframework.stereotype.Service;
@Service
public class MedicalBoundaryService {
    boolean blocks(String message) {
        return message.matches(".*(?:diagnostic|diagnostiqu|posologie|dosage|dose|prescri|combien.*(?:comprime|mg)|"
                + "quel medicament|modifier.*traitement|جرع|تشخيص|وصف دواء|كم حبة).*");
    }
}
