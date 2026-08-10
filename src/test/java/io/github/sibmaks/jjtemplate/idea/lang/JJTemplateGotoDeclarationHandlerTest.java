package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateGotoDeclarationHandlerTest extends LightJavaCodeInsightFixtureTestCase5 {

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

    @Test
    void resolvesSafeMemberToNestedDefinition() {
        var source = """
                {
                  "definitions": [
                    {
                      "repository": {
                        "on": true
                      }
                    }
                  ],
                  "template": "{{ .repository?.<caret>on }}"
                }
                """;
        getFixture().configureByText("safe-navigation.jjt", source);
        var targetOffset = ReadAction.compute(() -> {
            var offset = getFixture().getCaretOffset();
            var sourceElement = getFixture().getFile().findElementAt(offset);
            var targets = new JJTemplateGotoDeclarationHandler().getGotoDeclarationTargets(
                    sourceElement,
                    offset,
                    getFixture().getEditor()
            );
            assertNotNull(targets);
            assertEquals(1, targets.length);
            return targets[0].getTextOffset();
        });

        assertEquals(source.indexOf("\"on\"") + 1, targetOffset);
    }

    @Override
    protected @NotNull String getRelativePath() {
        return "";
    }

    @Override
    protected String getTestDataPath() {
        return "";
    }
}
