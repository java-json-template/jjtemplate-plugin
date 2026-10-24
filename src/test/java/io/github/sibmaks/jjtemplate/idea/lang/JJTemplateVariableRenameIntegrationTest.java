package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateVariableRenameIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void renamesLocalDefinitionAndEveryRootUsage() {
        getFixture().configureByText("rename-local.jjt", """
                {
                  "definitions": [
                    {"attachements": []}
                  ],
                  "template": "{{ .att<caret>achements }} {{ .attachements?.size }}"
                }
                """);

        renameAtCaret("attachments");

        assertEquals("""
                {
                  "definitions": [
                    {"attachments": []}
                  ],
                  "template": "{{ .attachments }} {{ .attachments?.size }}"
                }
                """, getFixture().getFile().getText());
    }

    @Test
    void renamesOnlyTheSelectedRangeBindingScope() {
        getFixture().configureByText("rename-binding.jjt", """
                {
                  "definitions": [
                    {"{{ first range it,index of .items }}": "{{ .it }} {{ .index }}"},
                    {"{{ second range it,index of .items }}": "{{ .i<caret>t }} {{ .index }}"}
                  ],
                  "template": "{{ .it }}"
                }
                """);

        renameAtCaret("entry");

        getFixture().checkResult("""
                {
                  "definitions": [
                    {"{{ first range it,index of .items }}": "{{ .it }} {{ .index }}"},
                    {"{{ second range entry,index of .items }}": "{{ .entry }} {{ .index }}"}
                  ],
                  "template": "{{ .it }}"
                }
                """);
    }

    @Test
    void renamesSingleRangeBindingFromItsDeclaration() {
        getFixture().configureByText("rename-single-binding.jjt", """
                {
                  "definitions": [
                    {"{{ rows range ro<caret>w of .items }}": "{{ .row.name }}"}
                  ],
                  "template": "{{ .rows }}"
                }
                """);

        renameAtCaret("entry");

        getFixture().checkResult("""
                {
                  "definitions": [
                    {"{{ rows range entry of .items }}": "{{ .entry.name }}"}
                  ],
                  "template": "{{ .rows }}"
                }
                """);
    }

    @Test
    void renamesNamedDefinitionWithoutRenamingItsBinding() {
        getFixture().configureByText("rename-named-definition.jjt", """
                {
                  "definitions": [
                    {"{{ attachements range item of .items }}": "{{ .item }}"}
                  ],
                  "template": "{{ .attach<caret>ements }}"
                }
                """);

        renameAtCaret("attachments");

        getFixture().checkResult("""
                {
                  "definitions": [
                    {"{{ attachments range item of .items }}": "{{ .item }}"}
                  ],
                  "template": "{{ .attachments }}"
                }
                """);
    }

    @Test
    void globalAndPropertyNamesAreNotRenameTargets() {
        getFixture().configureByText("not-local.jjt", """
                {
                  "definitions": [{"local": {"member": 1}}],
                  "template": "{{ .glob<caret>al.member }}"
                }
                """);
        assertNull(ReadAction.compute(() -> getFixture().getFile().findReferenceAt(getFixture().getCaretOffset())));

        getFixture().configureByText("not-property.jjt", """
                {
                  "definitions": [{"local": {"member": 1}}],
                  "template": "{{ .local.mem<caret>ber }}"
                }
                """);
        assertNull(ReadAction.compute(() -> getFixture().getFile().findReferenceAt(getFixture().getCaretOffset())));

        getFixture().configureByText("not-method-property.jjt", """
                {
                  "definitions": [{"sub": 1}],
                  "template": "{{ .attachements.get(0).su<caret>b }}"
                }
                """);
        assertNull(ReadAction.compute(() -> getFixture().getFile().findReferenceAt(getFixture().getCaretOffset())));
    }

    private void renameAtCaret(String newName) {
        var target = ReadAction.compute(() -> {
            assertNotNull(JJTemplateVariableAnalyzer.findAt(
                    getFixture().getFile().getText(), getFixture().getCaretOffset()
            ));
            var leaf = getFixture().getFile().findElementAt(getFixture().getCaretOffset());
            assertNotNull(leaf);
            var reference = getFixture().getFile().findReferenceAt(getFixture().getCaretOffset());
            assertNotNull(reference);
            return reference.resolve();
        });
        assertNotNull(target);
        ApplicationManager.getApplication().invokeAndWait(
                () -> new RenameProcessor(getFixture().getProject(), target, newName, false, false).run()
        );
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
