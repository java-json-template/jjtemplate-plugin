package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILeafElementType;
import org.jetbrains.annotations.NotNull;

public final class JJTemplateTokenTypes {
    public static final IElementType TEXT = type("TEXT");
    public static final IElementType OPEN_EXPR = type("OPEN_EXPR");
    public static final IElementType OPEN_COND = type("OPEN_COND");
    public static final IElementType OPEN_SPREAD = type("OPEN_SPREAD");
    public static final IElementType CLOSE = type("CLOSE");
    public static final IElementType PIPE = type("PIPE");
    public static final IElementType DOT = type("DOT");
    public static final IElementType SAFE_DOT = type("SAFE_DOT");
    public static final IElementType COMMA = type("COMMA");
    public static final IElementType COLON = type("COLON");
    public static final IElementType QUESTION = type("QUESTION");
    public static final IElementType LPAREN = type("LPAREN");
    public static final IElementType RPAREN = type("RPAREN");
    public static final IElementType LBRACE = type("LBRACE");
    public static final IElementType RBRACE = type("RBRACE");
    public static final IElementType LBRACKET = type("LBRACKET");
    public static final IElementType RBRACKET = type("RBRACKET");
    public static final IElementType STRING = type("STRING");
    public static final IElementType NUMBER = type("NUMBER");
    public static final IElementType BOOLEAN = type("BOOLEAN");
    public static final IElementType NULL = type("NULL");
    public static final IElementType IDENT = type("IDENT");
    public static final IElementType KEYWORD = type("KEYWORD");

    private JJTemplateTokenTypes() {
    }

    private static IElementType type(String name) {
        return new ReferenceAwareElementType(name);
    }

    private static final class ReferenceAwareElementType extends IElementType implements ILeafElementType {
        private ReferenceAwareElementType(String debugName) {
            super(debugName, JJTemplateLanguage.INSTANCE);
        }

        @Override
        public @NotNull ASTNode createLeafNode(@NotNull CharSequence leafText) {
            return new ReferenceAwareLeaf(this, leafText);
        }
    }

    private static final class ReferenceAwareLeaf extends LeafPsiElement {
        private ReferenceAwareLeaf(IElementType type, CharSequence text) {
            super(type, text);
        }

        @Override
        public PsiReference @NotNull [] getReferences() {
            return ReferenceProvidersRegistry.getReferencesFromProviders(this);
        }
    }
}
