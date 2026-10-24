package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;

class JJTemplateAnnotatorTest {

    @Test
    void highlightsRangeBindingAsLocalVariableInIsolatedValueLookup() {
        var tokens = new TemplateLexer("{{ .item }}-{{ .index }}").tokens();

        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_VARIABLE,
                identifierAttributes(tokens, "item", Set.of("item", "index"))
        );
        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_VARIABLE,
                identifierAttributes(tokens, "index", Set.of("item", "index"))
        );
    }

    @Test
    void highlightsUnboundRootVariableAsContextVariable() {
        var tokens = new TemplateLexer("{{ .external }}").tokens();

        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE,
                identifierAttributes(tokens, "external", Set.of())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{{ .attachements.get(0).sub }}",
            "{{ .attachements.get(indexOf(.indices, .selected)).sub }}",
            "{{ .attachements.get(0).children.get(1).sub }}",
            "{{ .attachements?.get(0).sub }}",
            "{{ .attachements.get(0)?.sub }}"
    })
    void highlightsPropertyAfterMethodCallAsMemberVariable(String source) {
        var tokens = new TemplateLexer(source).tokens();

        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE,
                identifierAttributes(tokens, "attachements", Set.of())
        );
        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_FUNCTION,
                identifierAttributes(tokens, "get", Set.of())
        );
        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_VARIABLE,
                identifierAttributes(tokens, "sub", Set.of())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{{ cast:str .external }}",
            "{{ .value | default .external }}",
            "{{ .attachements.get(.external).sub }}"
    })
    void highlightsRootVariableInFunctionArgumentAsContextVariable(String source) {
        var tokens = new TemplateLexer(source).tokens();

        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE,
                identifierAttributes(tokens, "external", Set.of())
        );
    }

    @Test
    void highlightsReferenceBeforeDefinitionAsContextVariable() {
        var source = """
                {
                  "definitions": [
                    {"usage": "{{ .definition }}"},
                    {"definition": 42}
                  ],
                  "template": "{{ .usage }}"
                }
                """;
        var tokens = new TemplateLexer(source).tokens();
        var definitions = UnusedDefinitionAnalyzer.findDefinitions(source);
        var usageOffset = source.indexOf(".definition") + 1;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.start != usageOffset) {
                continue;
            }
            assertSame(
                    JJTemplateSyntaxHighlighter.TEMPLATE_CONTEXT_VARIABLE,
                    JJTemplateAnnotator.identifierTextAttributes(
                            tokens,
                            i,
                            definitions,
                            Set.of(),
                            token.start
                    )
            );
            return;
        }
        throw new AssertionError("Definition reference not found");
    }

    @Test
    void highlightsSafePropertyAsMemberVariable() {
        var tokens = new TemplateLexer("{{ .repository?.on }}").tokens();

        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_VARIABLE,
                identifierAttributes(tokens, "on", Set.of())
        );
    }

    @Test
    void highlightsSafeMethodAsFunction() {
        var tokens = new TemplateLexer("{{ .value?.substring(1) }}").tokens();

        assertSame(
                JJTemplateSyntaxHighlighter.TEMPLATE_FUNCTION,
                identifierAttributes(tokens, "substring", Set.of())
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
                return JJTemplateAnnotator.identifierTextAttributes(tokens, i, Set.of(), rangeBindings);
            }
        }
        throw new AssertionError("Identifier not found: " + name);
    }
}
