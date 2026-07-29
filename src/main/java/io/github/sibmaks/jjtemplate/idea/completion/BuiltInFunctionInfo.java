package io.github.sibmaks.jjtemplate.idea.completion;

public record BuiltInFunctionInfo(
        String lookupKey,
        String namespace,
        String name,
        String signature,
        String description,
        boolean lazy
) {
    public String presentableName() {
        return namespace.isEmpty() ? name : namespace + ":" + name;
    }
}
