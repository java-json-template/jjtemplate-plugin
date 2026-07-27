package io.github.sibmaks.jjtemplate.idea.lang;

import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnusedDefinitionAnalyzerTest {

    @Test
    void reportsOnlyDefinitionsWithoutReferences() {
        var source = """
                {
                  "definitions": [
                    {
                      "used": 1,
                      "unused": 2,
                      "dependent": "{{ .used }}"
                    }
                  ],
                  "template": "{{ .dependent }}"
                }
                """;

        assertEquals(List.of("unused"), findUnusedNames(source));
    }

    @Test
    void reportsUnusedSwitchAndRangeDefinitionsAtTheirNames() {
        var source = """
                {
                  "definitions": [
                    {
                      "{{ unusedSwitch switch .enabled }}": {
                        "{{ true }}": "yes",
                        "{{ else }}": "no"
                      },
                      "{{ unusedRange range item,index of .items }}": "{{ .item }}"
                    }
                  ],
                  "template": "constant"
                }
                """;

        var definitions = findUnused(source);

        assertEquals(List.of("unusedSwitch", "unusedRange"), definitions.stream()
                .map(UnusedDefinitionAnalyzer.UnusedDefinition::name)
                .toList());
        assertEquals("unusedSwitch", source.substring(definitions.get(0).start(), definitions.get(0).end()));
        assertEquals("unusedRange", source.substring(definitions.get(1).start(), definitions.get(1).end()));
    }

    @Test
    void rangeBindingsDoNotCountAsDefinitionReferences() {
        var source = """
                {
                  "definitions": [
                    {
                      "item": "unused",
                      "{{ values range item,index of .items }}": "{{ .item }}"
                    }
                  ],
                  "template": "{{ .values }}"
                }
                """;

        assertEquals(List.of("item"), findUnusedNames(source));
    }

    @Test
    void ignoresInvalidJsonWhileDocumentIsBeingEdited() {
        var source = """
                {
                  "definitions": [{"unused": true}],
                """;

        assertEquals(List.of(), findUnused(source));
    }

    @Test
    void usesTemplateLexerGrammarForPlainDefinitionNames() {
        var source = """
                {
                  "definitions": [
                    {
                      "_valid123": true,
                      "not-referenceable": true,
                      "switch": true
                    }
                  ],
                  "template": "constant"
                }
                """;

        assertEquals(List.of("_valid123"), findUnusedNames(source));
    }

    private static List<String> findUnusedNames(String source) {
        return findUnused(source).stream()
                .map(UnusedDefinitionAnalyzer.UnusedDefinition::name)
                .toList();
    }

    private static List<UnusedDefinitionAnalyzer.UnusedDefinition> findUnused(String source) {
        var tokens = new TemplateLexer(source).tokens();
        return UnusedDefinitionAnalyzer.findUnused(source, tokens);
    }
}
