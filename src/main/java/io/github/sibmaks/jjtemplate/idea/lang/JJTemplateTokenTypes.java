package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.psi.tree.IElementType;

public final class JJTemplateTokenTypes {
    public static final IElementType TEXT = new IElementType("TEXT", JJTemplateLanguage.INSTANCE);
    public static final IElementType OPEN_EXPR = new IElementType("OPEN_EXPR", JJTemplateLanguage.INSTANCE);
    public static final IElementType OPEN_COND = new IElementType("OPEN_COND", JJTemplateLanguage.INSTANCE);
    public static final IElementType OPEN_SPREAD = new IElementType("OPEN_SPREAD", JJTemplateLanguage.INSTANCE);
    public static final IElementType CLOSE = new IElementType("CLOSE", JJTemplateLanguage.INSTANCE);
    public static final IElementType PIPE = new IElementType("PIPE", JJTemplateLanguage.INSTANCE);
    public static final IElementType DOT = new IElementType("DOT", JJTemplateLanguage.INSTANCE);
    public static final IElementType SAFE_DOT = new IElementType("SAFE_DOT", JJTemplateLanguage.INSTANCE);
    public static final IElementType COMMA = new IElementType("COMMA", JJTemplateLanguage.INSTANCE);
    public static final IElementType COLON = new IElementType("COLON", JJTemplateLanguage.INSTANCE);
    public static final IElementType QUESTION = new IElementType("QUESTION", JJTemplateLanguage.INSTANCE);
    public static final IElementType LPAREN = new IElementType("LPAREN", JJTemplateLanguage.INSTANCE);
    public static final IElementType RPAREN = new IElementType("RPAREN", JJTemplateLanguage.INSTANCE);
    public static final IElementType LBRACE = new IElementType("LBRACE", JJTemplateLanguage.INSTANCE);
    public static final IElementType RBRACE = new IElementType("RBRACE", JJTemplateLanguage.INSTANCE);
    public static final IElementType LBRACKET = new IElementType("LBRACKET", JJTemplateLanguage.INSTANCE);
    public static final IElementType RBRACKET = new IElementType("RBRACKET", JJTemplateLanguage.INSTANCE);
    public static final IElementType STRING = new IElementType("STRING", JJTemplateLanguage.INSTANCE);
    public static final IElementType NUMBER = new IElementType("NUMBER", JJTemplateLanguage.INSTANCE);
    public static final IElementType BOOLEAN = new IElementType("BOOLEAN", JJTemplateLanguage.INSTANCE);
    public static final IElementType NULL = new IElementType("NULL", JJTemplateLanguage.INSTANCE);
    public static final IElementType IDENT = new IElementType("IDENT", JJTemplateLanguage.INSTANCE);
    public static final IElementType KEYWORD = new IElementType("KEYWORD", JJTemplateLanguage.INSTANCE);

    private JJTemplateTokenTypes() {
    }
}
