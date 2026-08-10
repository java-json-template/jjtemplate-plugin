package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class JJTemplateVariableReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull com.intellij.psi.PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context
                    ) {
                        if (element.getFirstChild() != null) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        var file = element.getContainingFile();
                        if (!(file instanceof JJTemplateFile)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        var elementRange = element.getTextRange();
                        var references = new ArrayList<PsiReference>();
                        for (var occurrence : JJTemplateVariableAnalyzer.findOccurrences(file.getText())) {
                            if (occurrence.start() < elementRange.getStartOffset()
                                    || occurrence.end() > elementRange.getEndOffset()) {
                                continue;
                            }
                            references.add(new JJTemplateVariableReference(
                                    element,
                                    TextRange.create(
                                            occurrence.start() - elementRange.getStartOffset(),
                                            occurrence.end() - elementRange.getStartOffset()
                                    ),
                                    occurrence.declarationStart(),
                                    occurrence.declarationEnd()
                            ));
                        }
                        return references.toArray(PsiReference.EMPTY_ARRAY);
                    }
                }
        );
    }
}
