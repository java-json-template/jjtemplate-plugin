package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateEnterHandlerIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void indentsNewValueInsideEmptyObject() {
        getFixture().configureByText("empty-object.jjt", """
                {
                  "template": {<caret>}
                }
                """);
        CodeStyle.getIndentOptions(getFixture().getFile()).INDENT_SIZE = 2;

        getFixture().performEditorAction(IdeActions.ACTION_EDITOR_ENTER);

        getFixture().checkResult("""
                {
                  "template": {
                    <caret>
                  }
                }
                """);
    }

    @Test
    void indentsNewValueInsideEmptyArray() {
        getFixture().configureByText("empty-array.jjt", """
                {
                  "template": [<caret>]
                }
                """);
        CodeStyle.getIndentOptions(getFixture().getFile()).INDENT_SIZE = 2;

        getFixture().performEditorAction(IdeActions.ACTION_EDITOR_ENTER);

        getFixture().checkResult("""
                {
                  "template": [
                    <caret>
                  ]
                }
                """);
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
