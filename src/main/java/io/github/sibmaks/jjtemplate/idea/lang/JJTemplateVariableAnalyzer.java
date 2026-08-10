package io.github.sibmaks.jjtemplate.idea.lang;

import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

final class JJTemplateVariableAnalyzer {
    private JJTemplateVariableAnalyzer() {
    }

    static List<Occurrence> findOccurrences(String source) {
        var declarations = new LinkedHashMap<Integer, Occurrence>();
        for (var definition : UnusedDefinitionAnalyzer.findDefinitions(source)) {
            var occurrence = new Occurrence(
                    definition.start(), definition.end(), definition.start(), definition.end(), true
            );
            declarations.put(definition.start(), occurrence);
        }

        List<Token> tokens;
        try {
            tokens = new TemplateLexer(source).tokens();
        } catch (Exception ignored) {
            return List.copyOf(declarations.values());
        }

        collectRangeBindingDeclarations(tokens, declarations);
        var result = new ArrayList<>(declarations.values());
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.IDENT || !JJTemplateAnnotator.isRootVariable(tokens, i)) {
                continue;
            }
            var definition = JJTemplateGotoDeclarationHandler.findLocalDefinitionAt(source, token.start);
            if (definition == null || !declarations.containsKey(definition.start())) {
                continue;
            }
            result.add(new Occurrence(
                    token.start, token.end, definition.start(), definition.end(), false
            ));
        }
        result.sort(Comparator.comparingInt(Occurrence::start));
        return List.copyOf(result);
    }

    static Occurrence findAt(String source, int offset) {
        Occurrence edgeMatch = null;
        for (var occurrence : findOccurrences(source)) {
            if (offset >= occurrence.start() && offset < occurrence.end()) {
                return occurrence;
            }
            if (offset == occurrence.end()) {
                edgeMatch = occurrence;
            }
        }
        return edgeMatch;
    }

    private static void collectRangeBindingDeclarations(
            List<Token> tokens,
            LinkedHashMap<Integer, Occurrence> declarations
    ) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!JJTemplateAnnotator.isRangeBindingName(tokens, i)) {
                continue;
            }
            var token = tokens.get(i);
            declarations.put(token.start, new Occurrence(
                    token.start, token.end, token.start, token.end, true
            ));
        }
    }

    record Occurrence(
            int start,
            int end,
            int declarationStart,
            int declarationEnd,
            boolean declaration
    ) {
    }
}
