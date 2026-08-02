package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class JJTemplateSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey JSON_STRING =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.JSON_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey JSON_NUMBER =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.JSON_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey JSON_BOOLEAN =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.JSON_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey OBJECT_KEY =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.OBJECT_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey ROOT_OBJECT =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.ROOT_OBJECT", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey ROOT_ARRAY =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.ROOT_ARRAY", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey TEMPLATE_FUNCTION =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.TEMPLATE_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey TEMPLATE_VARIABLE =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.TEMPLATE_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey TEMPLATE_CONTEXT_VARIABLE =
            TextAttributesKey.createTextAttributesKey("JJTEMPLATE.TEMPLATE_CONTEXT_VARIABLE", DefaultLanguageHighlighterColors.METADATA);

    private static final TextAttributesKey[] BAD_CHAR = pack(HighlighterColors.BAD_CHARACTER);
    private static final TextAttributesKey[] EMPTY = TextAttributesKey.EMPTY_ARRAY;

    private static final Map<IElementType, TextAttributesKey[]> ATTRIBUTES = Map.ofEntries(
            Map.entry(JJTemplateTokenTypes.OPEN_EXPR, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JJTemplateTokenTypes.OPEN_COND, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JJTemplateTokenTypes.OPEN_SPREAD, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JJTemplateTokenTypes.CLOSE, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JJTemplateTokenTypes.KEYWORD, pack(DefaultLanguageHighlighterColors.KEYWORD)),
            Map.entry(JJTemplateTokenTypes.STRING, pack(JSON_STRING)),
            Map.entry(JJTemplateTokenTypes.NUMBER, pack(JSON_NUMBER)),
            Map.entry(JJTemplateTokenTypes.BOOLEAN, pack(JSON_BOOLEAN)),
            Map.entry(JJTemplateTokenTypes.NULL, pack(JSON_BOOLEAN)),
            Map.entry(JJTemplateTokenTypes.PIPE, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JJTemplateTokenTypes.DOT, pack(DefaultLanguageHighlighterColors.DOT)),
            Map.entry(JJTemplateTokenTypes.COMMA, pack(DefaultLanguageHighlighterColors.COMMA)),
            Map.entry(JJTemplateTokenTypes.COLON, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JJTemplateTokenTypes.QUESTION, pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)),
            Map.entry(JJTemplateTokenTypes.LPAREN, pack(DefaultLanguageHighlighterColors.PARENTHESES)),
            Map.entry(JJTemplateTokenTypes.RPAREN, pack(DefaultLanguageHighlighterColors.PARENTHESES)),
            Map.entry(JJTemplateTokenTypes.LBRACE, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JJTemplateTokenTypes.RBRACE, pack(DefaultLanguageHighlighterColors.BRACES)),
            Map.entry(JJTemplateTokenTypes.LBRACKET, pack(DefaultLanguageHighlighterColors.BRACKETS)),
            Map.entry(JJTemplateTokenTypes.RBRACKET, pack(DefaultLanguageHighlighterColors.BRACKETS)),
            Map.entry(JJTemplateTokenTypes.IDENT, pack(TEMPLATE_VARIABLE))
    );

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new JJTemplateSyntaxLexer();
    }

    @Override
    public @NotNull TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == null) {
            return EMPTY;
        }
        if (tokenType == com.intellij.psi.TokenType.BAD_CHARACTER) {
            return BAD_CHAR;
        }
        return ATTRIBUTES.getOrDefault(tokenType, EMPTY);
    }
}
