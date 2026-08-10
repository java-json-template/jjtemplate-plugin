package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.autodetect.IndentOptionsDetectorImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateFormattingIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void exposesStandardFormattingAndCodeStyleProviders() {
        assertInstanceOf(
                JJTemplateFormattingModelBuilder.class,
                LanguageFormatting.INSTANCE.forLanguage(JJTemplateLanguage.INSTANCE)
        );
        assertInstanceOf(
                JJTemplateLanguageCodeStyleSettingsProvider.class,
                LanguageCodeStyleSettingsProvider.forLanguage(JJTemplateLanguage.INSTANCE)
        );
    }

    @Test
    void reformatsWithConfiguredJjTemplateIndent() {
        getFixture().configureByText(
                "format.jjt",
                "{\"definitions\":[{\"message\":\"Hello, {{ .name }}!\"}],\"template\":\"{{ .message }}\"}"
        );
        var indentOptions = CodeStyle.getIndentOptions(getFixture().getFile());
        assertNotNull(indentOptions);
        indentOptions.INDENT_SIZE = 2;
        indentOptions.CONTINUATION_INDENT_SIZE = 2;
        indentOptions.TAB_SIZE = 2;

        getFixture().performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT);

        var expected = """
                {
                  "definitions": [
                    {
                      "message": "Hello, {{ .name }}!"
                    }
                  ],
                  "template": "{{ .message }}"
                }
                """;
        assertEquals(expected.stripTrailing(), ReadAction.compute(() -> getFixture().getFile().getText()));
    }

    @Test
    void supportsStandardIndentDetection() {
        getFixture().configureByText("detected.jjt", """
                {
                   "nested": {
                      "value": true
                   }
                }
                """);

        var detected = ReadAction.compute(() -> new IndentOptionsDetectorImpl(
                getFixture().getProject(),
                getFixture().getFile().getVirtualFile(),
                getFixture().getEditor().getDocument(),
                new EmptyProgressIndicator()
        ).getIndentOptions());

        assertEquals(3, detected.INDENT_SIZE);
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
