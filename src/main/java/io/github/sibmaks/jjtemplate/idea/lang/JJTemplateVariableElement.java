package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

final class JJTemplateVariableElement extends FakePsiElement implements PsiNamedElement {
    private final JJTemplateFile file;
    private final int declarationStart;
    private final int declarationEnd;

    JJTemplateVariableElement(JJTemplateFile file, int declarationStart, int declarationEnd) {
        this.file = file;
        this.declarationStart = declarationStart;
        this.declarationEnd = declarationEnd;
    }

    int declarationStart() {
        return declarationStart;
    }

    int declarationEnd() {
        return declarationEnd;
    }

    @Override
    public PsiElement getParent() {
        return file;
    }

    @Override
    public PsiFile getContainingFile() {
        return file;
    }

    @Override
    public TextRange getTextRange() {
        return TextRange.create(declarationStart, declarationEnd);
    }

    @Override
    public int getTextOffset() {
        return declarationStart;
    }

    @Override
    public String getName() {
        var text = file.getText();
        if (declarationStart < 0 || declarationEnd > text.length()) {
            return null;
        }
        return text.substring(declarationStart, declarationEnd);
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        var document = file.getViewProvider().getDocument();
        if (document == null) {
            throw new IncorrectOperationException("JJTemplate document is unavailable");
        }
        WriteCommandAction.runWriteCommandAction(
                file.getProject(),
                () -> document.replaceString(declarationStart, declarationEnd, name)
        );
        return this;
    }

    @Override
    public @NotNull SearchScope getUseScope() {
        return new LocalSearchScope(file);
    }

    @Override
    public boolean isValid() {
        return file.isValid() && declarationStart >= 0 && declarationEnd <= file.getTextLength();
    }

    @Override
    public String toString() {
        return "JJTemplate local variable '" + getName() + "'";
    }
}
