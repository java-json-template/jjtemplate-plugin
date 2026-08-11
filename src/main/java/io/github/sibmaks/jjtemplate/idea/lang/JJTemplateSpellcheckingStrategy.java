package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.IdentifierSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

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
                var host = file.findElementAt(range.getStartOffset());
                if (host == null || range.getEndOffset() > host.getTextRange().getEndOffset()) {
                    continue;
                }
                var variableName = range.substring(source);
                consumer.consumeToken(
                        host,
                        variableName,
                        false,
                        range.getStartOffset() - host.getTextRange().getStartOffset(),
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
}
