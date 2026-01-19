package org.example.model;

/**
 * Enum representing the kind/type of a symbol in the codebase.
 * Maps to SCIP SymbolInformation.Kind values.
 */
public enum SymbolKind {
    PACKAGE,
    CLASS,
    INTERFACE,
    ENUM,
    ENUM_MEMBER,
    METHOD,
    FIELD,
    VARIABLE,
    CONSTRUCTOR,
    FUNCTION,
    MODULE,
    PARAMETER,
    TYPE_PARAMETER,
    UNKNOWN;

    /**
     * Convert from SCIP SymbolInformation.Kind ordinal to our SymbolKind.
     */
    public static SymbolKind fromScipKind(int scipKind) {
        return switch (scipKind) {
            case 1 -> PACKAGE;
            case 2 -> CLASS;
            case 3 -> METHOD;
            case 4 -> FIELD;
            case 5 -> CONSTRUCTOR;
            case 6 -> INTERFACE;
            case 7 -> VARIABLE;
            case 8 -> FUNCTION;
            case 9 -> MODULE;
            case 10 -> PARAMETER;
            case 11 -> TYPE_PARAMETER;
            case 12 -> ENUM;
            case 13 -> ENUM_MEMBER;
            default -> UNKNOWN;
        };
    }
}

