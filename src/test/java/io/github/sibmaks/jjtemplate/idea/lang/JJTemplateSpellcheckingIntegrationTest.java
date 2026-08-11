package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD.CloseResource")
class JJTemplateSpellcheckingIntegrationTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Test
    void spellchecksLocalAndRootVariableNamesOnly() {
        var source = """
                {
                  "definitions": [
                    {
                      "attachements": "{{ .attachements }}"
                    }
                  ],
                  "template": "{{ .globall.proprtyyy }} {{ functonnn .externalll }}"
                }
                """;
        getFixture().configureByText("variable-spelling.jjt", source);
        getFixture().enableInspections(new SpellCheckingInspection());

        var typoRanges = getFixture().doHighlighting().stream()
                .filter(info -> info.getDescription() != null)
                .filter(info -> info.getDescription().startsWith("Typo: In word"))
                .map(info -> source.substring(info.startOffset, info.endOffset))
                .toList();

        assertEquals(
                List.of("attachements", "attachements", "globall", "externalll"),
                typoRanges
        );
    }

    @Test
    void spellingCorrectionRenamesLocalVariableUsages() {
        getFixture().configureByText("variable-spelling-fix.jjt", """
                {
                  "definitions": [{"attachements": []}],
                  "template": "{{ .attach<caret>ements }} {{ .attachements.size }}"
                }
                """);
        getFixture().enableInspections(new SpellCheckingInspection());
        getFixture().doHighlighting();

        var fixes = getFixture().getAllQuickFixes().stream()
                .filter(action -> "attachments".equals(action.getText()))
                .toList();
        assertTrue(!fixes.isEmpty(), "fixes=" + fixes);
        getFixture().launchAction(fixes.getFirst());

        assertEquals("""
                {
                  "definitions": [{"attachments": []}],
                  "template": "{{ .attachments }} {{ .attachments.size }}"
                }
                """, getFixture().getFile().getText());
    }

    @Test
    void spellingCorrectionOnNamedRangeRenamesSpreadUsage() {
        getFixture().configureByText("named-range-spelling-fix.jjt", """
                {
                  "definitions": [
                    {"{{ attach<caret>ements range key,value of .object }}": "{{ .key }} = {{ .value }}"}
                  ],
                  "template": "{{ . }}",
                  "key": {{ }},
                  "list": [42, "{{. .attachements }}"]
                }
                """);
        getFixture().enableInspections(new SpellCheckingInspection());
        getFixture().doHighlighting();

        var availableFixes = getFixture().getAvailableIntentions();
        var fix = availableFixes.stream()
                .filter(action -> "attachments".equals(action.getText()))
                .findFirst()
                .orElseThrow();
        getFixture().launchAction(fix);

        assertEquals("""
                {
                  "definitions": [
                    {"{{ attachments range key,value of .object }}": "{{ .key }} = {{ .value }}"}
                  ],
                  "template": "{{ . }}",
                  "key": {{ }},
                  "list": [42, "{{. .attachments }}"]
                }
                """, getFixture().getFile().getText());
    }

    @Test
    void spellingCorrectionRenamesExactNamedRangeExampleFromDefinition() {
        assertExactNamedRangeSpellingCorrection(true);
    }

    @Test
    void spellingCorrectionRenamesExactNamedRangeExampleFromUsage() {
        assertExactNamedRangeSpellingCorrection(false);
    }

    private void assertExactNamedRangeSpellingCorrection(boolean caretInDefinition) {
        var definitionName = caretInDefinition ? "attach<caret>ements" : "attachements";
        var usageName = caretInDefinition ? "attachements" : "attach<caret>ements";
        getFixture().configureByText("exact-named-range-spelling-fix.jjt", """
                {
                  "definitions": [
                    {
                      "{{ %s range entry,index of .input.entrySet() }}": "{{ .entry.value }}"
                    }
                  ],
                  "template": "{{ .%s | cast:str }}"
                }
                """.formatted(definitionName, usageName));
        getFixture().enableInspections(new SpellCheckingInspection());
        getFixture().doHighlighting();

        var availableFixes = getFixture().getAvailableIntentions();
        var attachmentFixes = availableFixes.stream()
                .filter(action -> "attachments".equals(action.getText()))
                .toList();
        assertEquals(1, attachmentFixes.size(), "available fixes=" + availableFixes);
        getFixture().launchAction(attachmentFixes.getFirst());

        assertEquals("""
                {
                  "definitions": [
                    {
                      "{{ attachments range entry,index of .input.entrySet() }}": "{{ .entry.value }}"
                    }
                  ],
                  "template": "{{ .attachments | cast:str }}"
                }
                """, getFixture().getFile().getText());
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
