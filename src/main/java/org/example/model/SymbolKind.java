package org.example.model;

/**
 * Enum representing the kind/type of a symbol in the codebase.
 * Maps to SCIP SymbolInformation.Kind values with additional fine-grained types.
 */
public enum SymbolKind {
    // ============= Containers =============
    /** Package/namespace */
    PACKAGE,
    /** Module (Python, JS) */
    MODULE,

    // ============= Types =============
    /** Class */
    CLASS,
    /** Interface */
    INTERFACE,
    /** Enum */
    ENUM,
    /** Enum member/constant */
    ENUM_MEMBER,
    /** Struct (Go, Rust) */
    STRUCT,
    /** Trait (Rust, Scala) */
    TRAIT,
    /** Annotation type (@interface in Java) */
    ANNOTATION,
    /** Inner/nested class */
    INNER_CLASS,
    /** Anonymous class */
    ANONYMOUS_CLASS,

    // ============= Members =============
    /** Instance method */
    METHOD,
    /** Static method */
    STATIC_METHOD,
    /** Abstract method */
    ABSTRACT_METHOD,
    /** Constructor */
    CONSTRUCTOR,
    /** Function (top-level or lambda) */
    FUNCTION,
    /** Lambda expression */
    LAMBDA,

    // ============= Fields =============
    /** Instance field */
    FIELD,
    /** Static field */
    STATIC_FIELD,
    /** Constant (final static field) */
    CONSTANT,
    /** Property (getter/setter pair) */
    PROPERTY,
    /** Getter method */
    GETTER,
    /** Setter method */
    SETTER,

    // ============= Variables =============
    /** Local variable */
    VARIABLE,
    /** Method/function parameter */
    PARAMETER,
    /** Type parameter (generics) */
    TYPE_PARAMETER,

    /** Unknown symbol kind */
    UNKNOWN;

    /**
     * Convert from SCIP SymbolInformation.Kind ordinal to our SymbolKind.
     * 
     * SCIP Kind values:
     *   UnspecifiedKind = 0
     *   AbstractMethod = 66
     *   Accessor = 72
     *   Array = 1
     *   Assertion = 2
     *   AssociatedType = 3
     *   Attribute = 4
     *   Axiom = 5
     *   Boolean = 6
     *   Class = 7
     *   Constant = 8
     *   Constructor = 9
     *   ...and many more
     */
    public static SymbolKind fromScipKind(int scipKind) {
        return switch (scipKind) {
            case 1 -> PACKAGE;      // Array (reusing as Package)
            case 7 -> CLASS;        // Class
            case 8 -> CONSTANT;     // Constant
            case 9 -> CONSTRUCTOR;  // Constructor
            case 10 -> ENUM;        // Enum
            case 11 -> ENUM_MEMBER; // EnumMember
            case 14 -> FIELD;       // Field
            case 17 -> FUNCTION;    // Function
            case 18 -> GETTER;      // Getter
            case 23 -> INTERFACE;   // Interface
            case 26 -> LAMBDA;      // Lambda
            case 30 -> METHOD;      // Method
            case 33 -> MODULE;      // Module
            case 35 -> PACKAGE;     // Namespace/Package
            case 39 -> PARAMETER;   // Parameter
            case 41 -> PROPERTY;    // Property
            case 45 -> SETTER;      // Setter
            case 49 -> STRUCT;      // Struct
            case 53 -> TRAIT;       // Trait
            case 59 -> TYPE_PARAMETER; // TypeParameter
            case 60 -> VARIABLE;    // Variable
            case 66 -> ABSTRACT_METHOD; // AbstractMethod
            case 79 -> STATIC_FIELD;    // StaticField
            case 80 -> STATIC_METHOD;   // StaticMethod
            default -> UNKNOWN;
        };
    }

    /**
     * Legacy conversion method for backward compatibility.
     */
    public static SymbolKind fromLegacyScipKind(int scipKind) {
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

    /**
     * Check if this is a type symbol (class, interface, enum, etc.).
     */
    public boolean isType() {
        return this == CLASS || this == INTERFACE || this == ENUM || 
               this == STRUCT || this == TRAIT || this == ANNOTATION ||
               this == INNER_CLASS || this == ANONYMOUS_CLASS;
    }

    /**
     * Check if this is a callable symbol (method, function, constructor).
     */
    public boolean isCallable() {
        return this == METHOD || this == STATIC_METHOD || this == ABSTRACT_METHOD ||
               this == CONSTRUCTOR || this == FUNCTION || this == LAMBDA ||
               this == GETTER || this == SETTER;
    }

    /**
     * Check if this is a field-like symbol.
     */
    public boolean isField() {
        return this == FIELD || this == STATIC_FIELD || this == CONSTANT || this == PROPERTY;
    }

    /**
     * Check if this is a variable-like symbol.
     */
    public boolean isVariable() {
        return this == VARIABLE || this == PARAMETER;
    }

    /**
     * Check if this is a container symbol (can have children).
     */
    public boolean isContainer() {
        return this == PACKAGE || this == MODULE || isType() || isCallable();
    }
}
