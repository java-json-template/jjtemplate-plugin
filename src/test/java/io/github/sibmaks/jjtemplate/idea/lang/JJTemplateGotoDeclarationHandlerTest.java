package io.github.sibmaks.jjtemplate.idea.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class JJTemplateGotoDeclarationHandlerTest {

    @Test
    void doesNotResolveReferenceToLaterDefinition() {
        var source = """
                {
                  "definitions": [
                    {"usage": "{{ .definition }}"},
                    {"definition": 42}
                  ],
                  "template": "{{ .usage }}"
                }
                """;

        var definitionUsage = source.indexOf(".definition") + 1;

        assertNull(JJTemplateGotoDeclarationHandler.findDefinitionsKeyDefinition(
                source,
                "definition",
                definitionUsage
        ));
    }
}
