package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public final class JJTemplateFileType extends LanguageFileType {
    public static final JJTemplateFileType INSTANCE = new JJTemplateFileType();
    private static final Icon ICON = IconLoader.getIcon("/icons/jjtemplate.svg", JJTemplateFileType.class);

    private JJTemplateFileType() {
        super(JJTemplateLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "JJTemplate";
    }

    @Override
    public @NotNull String getDescription() {
        return "JJTemplate template";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "jjt";
    }

    @Override
    public @Nullable Icon getIcon() {
        return ICON;
    }
}
