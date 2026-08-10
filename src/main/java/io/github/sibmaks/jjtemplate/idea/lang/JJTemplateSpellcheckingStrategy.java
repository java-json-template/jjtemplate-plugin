package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.IdentifierSplitter;
import com.intellij.spellchecker.quickfixes.SaveTo;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Arrays;

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
        for (var suggestion : SpellCheckerManager.getInstance(file.getProject()).getSuggestions(typo)) {
            var replacementName = originalName.substring(0, typoStart)
                    + suggestion
                    + originalName.substring(typoEnd);
            fixes.add(new RenameVariableSpellingFix(
                    occurrence.declarationStart(), originalName, replacementName, suggestion
            ));
        }
        Arrays.stream(getDefaultRegularFixes(false, typo, element, textRange))
                .filter(SaveTo.class::isInstance)
                .forEach(fixes::add);
        return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
    }

    private static final class RenameVariableSpellingFix implements LocalQuickFix, HighPriorityAction {
        private final int declarationStart;
        private final String originalName;
        private final String replacementName;
        private final String suggestion;

        private RenameVariableSpellingFix(
                int declarationStart,
                String originalName,
                String replacementName,
                String suggestion
        ) {
            this.declarationStart = declarationStart;
            this.originalName = originalName;
            this.replacementName = replacementName;
            this.suggestion = suggestion;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Change variable spelling";
        }

        @Override
        public @NotNull String getName() {
            return "Change to '" + suggestion + "' and rename usages";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            var file = descriptor.getPsiElement().getContainingFile();
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
    }
}
