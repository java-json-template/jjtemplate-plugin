package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class JJTemplateFile extends PsiFileBase {
    public JJTemplateFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, JJTemplateLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return JJTemplateFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "JJTemplate File";
    }
}
