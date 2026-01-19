package org.example.model;

/**
 * Enum representing the kind of reference/relationship between symbols.
 */
public enum ReferenceKind {
    DEFINITION,
    REFERENCE,
    IMPORT,
    EXTENDS,
    IMPLEMENTS,
    CALL,
    TYPE_REF,
    FIELD_ACCESS,
    WRITE,
    READ,
    UNKNOWN;

    public static ReferenceKind fromScipRole(int role) {
        if ((role & 1) != 0) {
            return DEFINITION;
        }
        return REFERENCE;
    }
}

