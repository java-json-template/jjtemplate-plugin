package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.json.JsonLanguage;
import com.intellij.json.formatter.JsonFormattingBuilderModel;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;

/** Reuses JSON's block model so IntelliJ can format and detect indents in JJTemplate files. */
public final class JJTemplateFormattingModelBuilder implements FormattingModelBuilder {
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        var sourceFile = formattingContext.getContainingFile();
        var jsonFile = PsiFileFactory.getInstance(formattingContext.getProject()).createFileFromText(
                sourceFile.getName() + ".json",
                JsonLanguage.INSTANCE,
                sourceFile.getText()
        );
        var jsonContext = FormattingContext.create(
                jsonFile,
                formattingContext.getFormattingRange(),
                formattingContext.getCodeStyleSettings(),
                formattingContext.getFormattingMode()
        );
        var jsonModel = new JsonFormattingBuilderModel().createModel(jsonContext);
        return FormattingModelProvider.createFormattingModelForPsiFile(
                sourceFile,
                jsonModel.getRootBlock(),
                formattingContext.getCodeStyleSettings()
        );
    }
}
