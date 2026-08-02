package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateAnnotatorIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void marksEveryGlobalVariableUsageOnErrorStripe() {
        var source = """
                {
                  "definitions": [
                    {"local": 1},
                    {"localUsage": "{{ .local }}"},
                    {"futureUsage": "{{ .future }}"},
                    {"future": 2},
                    {"{{ parts range item,index of .items }}": ["{{ .item }}"]}
                  ],
                  "template": "{{ .external }} {{ .external }} {{ .localUsage }}"
                }
                """;
        getFixture().configureByText("globals.jjt", source);

        var globalHighlights = getFixture().doHighlighting().stream()
                .filter(info -> info.getDescription() != null)
                .filter(info -> info.getDescription().startsWith("Global variable '"))
                .toList();

        assertEquals(
                List.of("future", "items", "external", "external"),
                globalHighlights.stream()
                        .map(info -> source.substring(info.startOffset, info.endOffset))
                        .toList()
        );
        globalHighlights.forEach(JJTemplateAnnotatorIntegrationTest::assertErrorStripeMarker);
    }

    private static void assertErrorStripeMarker(HighlightInfo info) {
        assertNotNull(info.forcedTextAttributes);
        assertNotNull(info.forcedTextAttributes.getErrorStripeColor());
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
