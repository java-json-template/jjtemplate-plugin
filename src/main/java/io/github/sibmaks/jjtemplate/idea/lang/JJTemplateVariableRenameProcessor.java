package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public final class JJTemplateVariableRenameProcessor extends RenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return element instanceof JJTemplateVariableElement;
    }

    @Override
    public @NotNull Collection<PsiReference> findReferences(
            @NotNull PsiElement element,
            @NotNull SearchScope searchScope,
            boolean searchInCommentsAndStrings
    ) {
        var variable = (JJTemplateVariableElement) element;
        var file = variable.getContainingFile();
        var references = new ArrayList<PsiReference>();
        for (var occurrence : JJTemplateVariableAnalyzer.findOccurrences(file.getText())) {
            if (occurrence.declaration()
                    || occurrence.declarationStart() != variable.declarationStart()) {
                continue;
            }
            var host = file.findElementAt(occurrence.start());
            if (host == null) {
                continue;
            }
            var hostStart = host.getTextRange().getStartOffset();
            references.add(new JJTemplateVariableReference(
                    host,
                    TextRange.create(occurrence.start() - hostStart, occurrence.end() - hostStart),
                    occurrence.declarationStart(),
                    occurrence.declarationEnd()
            ));
        }
        return references;
    }

    @Override
    public void renameElement(
            @NotNull PsiElement element,
            @NotNull String newName,
            UsageInfo @NotNull [] usages,
            @Nullable RefactoringElementListener listener
    ) throws IncorrectOperationException {
        var variable = (JJTemplateVariableElement) element;
        var file = (JJTemplateFile) variable.getContainingFile();
        var document = file.getViewProvider().getDocument();
        if (document == null) {
            throw new IncorrectOperationException("JJTemplate document is unavailable");
        }
        var occurrences = JJTemplateVariableAnalyzer.findOccurrences(file.getText()).stream()
                .filter(occurrence -> occurrence.declarationStart() == variable.declarationStart())
                .sorted((left, right) -> Integer.compare(right.start(), left.start()))
                .toList();
        for (var occurrence : occurrences) {
            document.replaceString(occurrence.start(), occurrence.end(), newName);
        }
        if (listener != null) {
            listener.elementRenamed(new JJTemplateVariableElement(
                    file,
                    variable.declarationStart(),
                    variable.declarationStart() + newName.length()
            ));
        }
    }
}
