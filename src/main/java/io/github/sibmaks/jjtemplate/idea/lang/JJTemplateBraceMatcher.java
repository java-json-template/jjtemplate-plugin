package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JJTemplateBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] PAIRS = {
            new BracePair(JJTemplateTokenTypes.OPEN_EXPR, JJTemplateTokenTypes.CLOSE, false),
            new BracePair(JJTemplateTokenTypes.OPEN_COND, JJTemplateTokenTypes.CLOSE, false),
            new BracePair(JJTemplateTokenTypes.OPEN_SPREAD, JJTemplateTokenTypes.CLOSE, false),
            new BracePair(JJTemplateTokenTypes.LBRACE, JJTemplateTokenTypes.RBRACE, true),
            new BracePair(JJTemplateTokenTypes.LBRACKET, JJTemplateTokenTypes.RBRACKET, true),
            new BracePair(JJTemplateTokenTypes.LPAREN, JJTemplateTokenTypes.RPAREN, false)
    };

    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
