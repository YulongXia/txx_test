package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dialog.AccessorRepositoryImpl;
import ai.hual.labrador.dialog.accessors.RelatedQuestionAccessor;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.Execution;
import ai.hual.labrador.dm.ExecutionResult;
import ai.hual.labrador.kg.KnowledgeStatus;
import ai.hual.labrador.kg.KnowledgeStatusAccessor;
import responses.FAQResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FAQExecution implements Execution {

    private FAQResponse faqResponse;

    @Override
    public void setUp(Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        // TODO remove after real knowledge status accessor utilized
        if (accessorRepository.getKnowledgeStatusAccessor() == null && accessorRepository instanceof AccessorRepositoryImpl) {
            ((AccessorRepositoryImpl) accessorRepository).withKnowledgeStatusAccessor(new KnowledgeStatusAccessor() {
                @Override
                public KnowledgeStatus instanceStatus(String s) {
                    return KnowledgeStatus.ENABLED;
                }

                @Override
                public KnowledgeStatus propertyStatus(String s, String s1) {
                    return KnowledgeStatus.ENABLED;
                }
            });
        }
        if (accessorRepository.getRelatedQuestionAccessor() == null && accessorRepository instanceof AccessorRepositoryImpl) {
            ((AccessorRepositoryImpl) accessorRepository).withRelatedQuestionAccessor(new RelatedQuestionAccessor() {
                @Override
                public List<String> relatedQuestionByFAQ(int i) {
                    return Collections.emptyList();
                }

                @Override
                public List<String> relatedQuestionByKG(String s, String s1) {
                    return Collections.emptyList();
                }
            });
        }
        faqResponse = new FAQResponse(accessorRepository);
    }

    @Override
    public ExecutionResult execute(Context context) {
        return faqResponse.faq(context, true);
    }

}
