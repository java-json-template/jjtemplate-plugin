package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction;
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction;
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFixFactory;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("KotlinInternalInJava")
public final class JJTemplateSpellcheckerQuickFixFactory extends SpellCheckerQuickFixFactory {
    @Override
    public List<LocalQuickFix> createChangeToVariantsFixes(
            @NotNull PsiElement element,
            @NotNull TextRange rangeInElement,
            @NotNull String typo
    ) {
        var containingFile = element.getContainingFile();
        if (!(containingFile instanceof JJTemplateFile file)) {
            return null;
        }
        var absoluteRange = rangeInElement.shiftRight(element.getTextRange().getStartOffset());
        var occurrence = JJTemplateVariableAnalyzer.findAt(file.getText(), absoluteRange.getStartOffset());
        if (occurrence == null || absoluteRange.getEndOffset() > occurrence.end()) {
            return null;
        }

        var originalName = file.getText().substring(occurrence.start(), occurrence.end());
        var typoStart = absoluteRange.getStartOffset() - occurrence.start();
        var typoEnd = absoluteRange.getEndOffset() - occurrence.start();
        var variants = new ArrayList<SpellingVariant>();
        for (var suggestion : SpellCheckerManager.getInstance(file.getProject()).getSuggestions(typo)) {
            variants.add(new SpellingVariant(
                    suggestion,
                    originalName.substring(0, typoStart)
                            + suggestion
                            + originalName.substring(typoEnd)
            ));
        }
        if (variants.isEmpty()) {
            return null;
        }
        return new RenameVariableSpellingChoices(
                occurrence.declarationStart(), originalName, variants
        ).getAllAsFixes();
    }

    private static void renameVariable(
            PsiFile file,
            int declarationStart,
            String originalName,
            String replacementName
    ) {
        if (!(file instanceof JJTemplateFile jjTemplateFile)) {
            return;
        }
        var document = jjTemplateFile.getViewProvider().getDocument();
        if (document == null) {
            return;
        }
        var occurrences = JJTemplateVariableAnalyzer.findOccurrences(jjTemplateFile.getText()).stream()
                .filter(candidate -> candidate.declarationStart() == declarationStart)
                .filter(candidate -> originalName.equals(
                        jjTemplateFile.getText().substring(candidate.start(), candidate.end())
                ))
                .sorted((left, right) -> Integer.compare(right.start(), left.start()))
                .toList();
        for (var occurrence : occurrences) {
            document.replaceString(occurrence.start(), occurrence.end(), replacementName);
        }
    }

    private static final class RenameVariableSpellingChoices implements DefaultIntentionActionWithChoice {
        private final int declarationStart;
        private final String originalName;
        private final List<SpellingVariant> variants;

        private RenameVariableSpellingChoices(
                int declarationStart,
                String originalName,
                List<SpellingVariant> variants
        ) {
            this.declarationStart = declarationStart;
            this.originalName = originalName;
            this.variants = List.copyOf(variants);
        }

        @Override
        public ChoiceTitleIntentionAction getTitle() {
            var title = SpellCheckerBundle.message("change.to.title");
            return new ChoiceTitleIntentionAction(title, title);
        }

        @Override
        public List<ChoiceVariantIntentionAction> getVariants() {
            var actions = new ArrayList<ChoiceVariantIntentionAction>(variants.size());
            for (int i = 0; i < variants.size(); i++) {
                actions.add(new RenameVariableSpellingVariant(
                        i, declarationStart, originalName, variants.get(i)
                ));
            }
            return List.copyOf(actions);
        }
    }

    private static final class RenameVariableSpellingVariant
            extends ChoiceVariantIntentionAction implements HighPriorityAction {
        private final int index;
        private final int declarationStart;
        private final String originalName;
        private final SpellingVariant variant;

        private RenameVariableSpellingVariant(
                int index,
                int declarationStart,
                String originalName,
                SpellingVariant variant
        ) {
            this.index = index;
            this.declarationStart = declarationStart;
            this.originalName = originalName;
            this.variant = variant;
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public @NotNull String getName() {
            return variant.suggestion();
        }

        @Override
        public @NotNull String getFamilyName() {
            return SpellCheckerBundle.message("change.to.title");
        }

        @Override
        public void applyFix(@NotNull Project project, PsiFile file, Editor editor) {
            renameVariable(file, declarationStart, originalName, variant.replacementName());
        }
    }

    private record SpellingVariant(String suggestion, String replacementName) {
    }
}
