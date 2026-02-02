package org.example.model;

/**
 * Enum representing the kind of reference/relationship between symbols.
 */
public enum ReferenceKind {
    // ============= Existing =============
    /** Symbol definition */
    DEFINITION,
    /** Generic reference */
    REFERENCE,
    /** Import statement */
    IMPORT,
    /** Class/interface extension */
    EXTENDS,
    /** Interface implementation */
    IMPLEMENTS,
    /** Method/function call */
    CALL,
    /** Type reference (class, interface usage) */
    TYPE_REF,
    /** Field access */
    FIELD_ACCESS,
    /** Write access to variable/field */
    WRITE,
    /** Read access to variable/field */
    READ,
    /** Forward declaration */
    FORWARD_DEFINITION,
    /** Type definition */
    TYPE_DEFINITION,

    // ============= New: Structural =============
    /** Parent contains child (Class->Method, Package->Class) */
    CONTAINS,

    // ============= New: Type relationships =============
    /** Field/Parameter has type X */
    HAS_TYPE,
    /** Method returns type X */
    RETURNS_TYPE,
    /** Method throws exception X */
    THROWS,
    /** Method creates instance of X (new X()) */
    CREATES,

    // ============= New: Dependency Injection =============
    /** Dependency injection (Spring @Autowired, constructor injection, etc.) */
    INJECTS,

    // ============= New: Annotations =============
    /** Symbol is annotated with X */
    ANNOTATED_WITH,

    /** Unknown reference kind */
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
            case METHOD, FUNCTION, CONSTRUCTOR, STATIC_METHOD, ABSTRACT_METHOD -> CALL;
            case CLASS, INTERFACE, ENUM, STRUCT, TRAIT, INNER_CLASS -> TYPE_REF;
            case FIELD, STATIC_FIELD, CONSTANT, PROPERTY -> FIELD_ACCESS;
            case ANNOTATION -> ANNOTATED_WITH;
            default -> REFERENCE;
        };
    }

    /**
     * Check if this is a structural relationship (parent-child).
     */
    public boolean isStructural() {
        return this == CONTAINS;
    }

    /**
     * Check if this is a type relationship.
     */
    public boolean isTypeRelationship() {
        return this == HAS_TYPE || this == RETURNS_TYPE || this == TYPE_REF;
    }

    /**
     * Check if this is a dependency/usage relationship.
     */
    public boolean isDependency() {
        return this == CALL || this == FIELD_ACCESS || this == CREATES || 
               this == INJECTS || this == IMPORT;
    }

    /**
     * Check if this is an inheritance relationship.
     */
    public boolean isInheritance() {
        return this == EXTENDS || this == IMPLEMENTS;
    }
}
