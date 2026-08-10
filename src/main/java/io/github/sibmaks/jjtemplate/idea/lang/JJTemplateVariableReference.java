package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

final class JJTemplateVariableReference extends PsiReferenceBase<PsiElement> {
    private final int declarationStart;
    private final int declarationEnd;

    JJTemplateVariableReference(
            PsiElement element,
            TextRange rangeInElement,
            int declarationStart,
            int declarationEnd
    ) {
        super(element, rangeInElement, false);
        this.declarationStart = declarationStart;
        this.declarationEnd = declarationEnd;
    }

    int declarationStart() {
        return declarationStart;
    }

    @Override
    public PsiElement resolve() {
        var file = getElement().getContainingFile();
        if (!(file instanceof JJTemplateFile jjTemplateFile)) {
            return null;
        }
        return new JJTemplateVariableElement(jjTemplateFile, declarationStart, declarationEnd);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        var element = getElement();
        var file = element.getContainingFile();
        var document = file.getViewProvider().getDocument();
        if (document == null) {
            throw new IncorrectOperationException("JJTemplate document is unavailable");
        }
        var absoluteRange = getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
        document.replaceString(absoluteRange.getStartOffset(), absoluteRange.getEndOffset(), newElementName);
        return element;
    }
}
