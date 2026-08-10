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
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.inspections.IdentifierSplitter;
import com.intellij.spellchecker.quickfixes.SaveTo;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class JJTemplateSpellcheckingStrategy extends SpellcheckingStrategy {
    private static final Tokenizer<JJTemplateFile> VARIABLE_TOKENIZER = new Tokenizer<>() {
        @Override
        public void tokenize(@NotNull JJTemplateFile file, @NotNull TokenConsumer consumer) {
            var source = file.getText();
            var ranges = new LinkedHashSet<TextRange>();
            for (var definition : UnusedDefinitionAnalyzer.findDefinitions(source)) {
                ranges.add(TextRange.create(definition.start(), definition.end()));
            }

            try {
                var tokens = new TemplateLexer(source).tokens();
                for (int i = 0; i < tokens.size(); i++) {
                    var token = tokens.get(i);
                    if (token.type != TokenType.IDENT) {
                        continue;
                    }
                    if (JJTemplateAnnotator.isDefinitionName(tokens, i)
                            || JJTemplateAnnotator.isRangeBindingName(tokens, i)
                            || JJTemplateAnnotator.isRootVariable(tokens, i)) {
                        ranges.add(TextRange.create(token.start, token.end));
                    }
                }
            } catch (Exception ignored) {
                return;
            }

            var splitter = IdentifierSplitter.getInstance();
            for (var range : ranges) {
                var variableName = range.substring(source);
                consumer.consumeToken(
                        file,
                        variableName,
                        false,
                        range.getStartOffset(),
                        TextRange.allOf(variableName),
                        splitter
                );
            }
        }
    };

    @Override
    public @NotNull Tokenizer<?> getTokenizer(PsiElement element) {
        if (element instanceof JJTemplateFile) {
            return VARIABLE_TOKENIZER;
        }
        return EMPTY_TOKENIZER;
    }

    @Override
    public LocalQuickFix[] getRegularFixes(
            @NotNull PsiElement element,
            @NotNull TextRange textRange,
            boolean useRename,
            String typo
    ) {
        if (!(element instanceof JJTemplateFile file)) {
            return super.getRegularFixes(element, textRange, useRename, typo);
        }
        var occurrence = JJTemplateVariableAnalyzer.findAt(file.getText(), textRange.getStartOffset());
        if (occurrence == null || textRange.getEndOffset() > occurrence.end()) {
            return super.getRegularFixes(element, textRange, false, typo);
        }

        var originalName = file.getText().substring(occurrence.start(), occurrence.end());
        var typoStart = textRange.getStartOffset() - occurrence.start();
        var typoEnd = textRange.getEndOffset() - occurrence.start();
        var fixes = new ArrayList<LocalQuickFix>();
        var variants = new ArrayList<SpellingVariant>();
        for (var suggestion : SpellCheckerManager.getInstance(file.getProject()).getSuggestions(typo)) {
            var replacementName = originalName.substring(0, typoStart)
                    + suggestion
                    + originalName.substring(typoEnd);
            variants.add(new SpellingVariant(suggestion, replacementName));
        }
        if (!variants.isEmpty()) {
            fixes.addAll(new RenameVariableSpellingChoices(
                    occurrence.declarationStart(), originalName, variants
            ).getAllAsFixes());
        }
        Arrays.stream(getDefaultRegularFixes(false, typo, element, textRange))
                .filter(SaveTo.class::isInstance)
                .forEach(fixes::add);
        return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
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
