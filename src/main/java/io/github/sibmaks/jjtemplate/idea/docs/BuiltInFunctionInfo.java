package io.github.sibmaks.jjtemplate.idea.docs;

public record BuiltInFunctionInfo(
        String lookupKey,
        String namespace,
        String name,
        String description,
        boolean lazy
) {
    public String presentableName() {
        return namespace.isEmpty() ? name : namespace + ":" + name;
    }
}
