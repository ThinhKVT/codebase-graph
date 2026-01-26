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
    FORWARD_DEFINITION,
    TYPE_DEFINITION,
    UNKNOWN;

    /**
     * Map SCIP SymbolRole bitfield to ReferenceKind.
     * SCIP SymbolRole flags:
     *   Definition       = 0x1
     *   Import           = 0x2
     *   WriteAccess      = 0x4
     *   ReadAccess       = 0x8
     *   Generated        = 0x10
     *   Test             = 0x20
     *   ForwardDefinition = 0x40
     */
    public static ReferenceKind fromScipRole(int role) {
        // Check flags in priority order
        if ((role & 0x1) != 0) {
            return DEFINITION;
        }
        if ((role & 0x40) != 0) {
            return FORWARD_DEFINITION;
        }
        if ((role & 0x2) != 0) {
            return IMPORT;
        }
        if ((role & 0x4) != 0) {
            return WRITE;
        }
        if ((role & 0x8) != 0) {
            return READ;
        }
        // Default to REFERENCE for plain references
        return REFERENCE;
    }

    /**
     * Determine reference kind based on the target symbol kind.
     * This helps distinguish CALL (to methods/functions) from TYPE_REF (to classes/interfaces).
     */
    public static ReferenceKind fromTargetSymbolKind(SymbolKind targetKind) {
        return switch (targetKind) {
            case METHOD, FUNCTION, CONSTRUCTOR -> CALL;
            case CLASS, INTERFACE, ENUM -> TYPE_REF;
            case FIELD -> FIELD_ACCESS;
            default -> REFERENCE;
        };
    }
}
