package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;

class JjtemplateAnnotatorTest {

    @Test
    void highlightsRangeBindingAsLocalVariableInIsolatedValueLookup() {
        var tokens = new TemplateLexer("{{ .item }}-{{ .index }}").tokens();

        assertSame(
                JjtemplateSyntaxHighlighter.TEMPLATE_VARIABLE,
                identifierAttributes(tokens, "item", Set.of("item", "index"))
        );
        assertSame(
                JjtemplateSyntaxHighlighter.TEMPLATE_VARIABLE,
                identifierAttributes(tokens, "index", Set.of("item", "index"))
        );
    }

    @Test
    void highlightsUnboundRootVariableAsContextVariable() {
        var tokens = new TemplateLexer("{{ .external }}").tokens();

        assertSame(
                JjtemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE,
                identifierAttributes(tokens, "external", Set.of())
        );
    }

    private static TextAttributesKey identifierAttributes(
            List<Token> tokens,
            String name,
            Set<String> rangeBindings
    ) {
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type == TokenType.IDENT && name.equals(token.lexeme)) {
                return JjtemplateAnnotator.identifierTextAttributes(tokens, i, Set.of(), rangeBindings);
            }
        }
        throw new AssertionError("Identifier not found: " + name);
    }
}
