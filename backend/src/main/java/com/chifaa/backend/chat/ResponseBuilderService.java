package com.chifaa.backend.chat;

import com.chifaa.backend.compatibility.KnowledgeSearchProvider.Reference;
import org.springframework.stereotype.Service;

@Service
public class ResponseBuilderService {
    String build(String language, boolean distress, boolean blocked, Reference reference, boolean resourcesAvailable) {
        boolean french = language.equals("French") || language.equals("Mixed");
        if (distress) return french
                ? "Ce que vous traversez peut être difficile. Vous pouvez en parler à une personne de confiance ou à votre équipe soignante."
                    + (resourcesAvailable ? "" : " Les coordonnées officielles ne sont pas configurées.")
                : "قد يكون ما تمرين به صعبًا. يمكنك التحدث مع شخص تثقين به أو مع فريقك الطبي."
                    + (resourcesAvailable ? "" : " بيانات الاتصال الرسمية غير متاحة هنا.");
        if (blocked) return french ? "Pour un diagnostic ou un traitement, parlez-en à votre équipe soignante."
                : "للتشخيص أو العلاج، تحدثي إلى فريقك الطبي.";
        if (reference == null) return french ? "Aucune fiche admissible ne correspond. Vous pouvez consulter l’annuaire."
                : "لا توجد بطاقة مناسبة. يمكنك الاطلاع على دليل الخدمات.";
        if (!reference.fictional()) return reference.body();
        return (french ? "Contenu fictif – non validé. " : "محتوى تجريبي غير معتمد. ") + reference.body();
    }
}
