package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JJTemplateSyntaxLexerTest {

    @Test
    void recognizesSafePropertyAndMethodAccessAsSingleTokens() {
        var source = "{{ .repository?.on }} {{ .value?.substring(1) }}";
        var lexer = new JJTemplateSyntaxLexer();
        lexer.start(source, 0, source.length(), 0);

        var safeAccessLexemes = new ArrayList<String>();
        var tokenTypes = new ArrayList<IElementType>();
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() == JJTemplateTokenTypes.SAFE_DOT) {
                safeAccessLexemes.add(source.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
            }
            tokenTypes.add(lexer.getTokenType());
            lexer.advance();
        }

        assertEquals(List.of("?.", "?."), safeAccessLexemes);
        assertEquals(2, tokenTypes.stream()
                .filter(JJTemplateTokenTypes.SAFE_DOT::equals)
                .count());
    }
}
