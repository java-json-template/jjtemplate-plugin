package io.github.sibmaks.jjtemplate.idea.lang;

import com.intellij.lang.Language;

public final class JJTemplateLanguage extends Language {
    public static final JJTemplateLanguage INSTANCE = new JJTemplateLanguage();

    private JJTemplateLanguage() {
        super("JJTemplate");
    }
}
