package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SymbolKind enum.
 */
class SymbolKindTest {

    @Test
    void testIsType_class() {
        assertTrue(SymbolKind.CLASS.isType());
    }

    @Test
    void testIsType_interface() {
        assertTrue(SymbolKind.INTERFACE.isType());
    }

    @Test
    void testIsType_enum() {
        assertTrue(SymbolKind.ENUM.isType());
    }

    @Test
    void testIsType_struct() {
        assertTrue(SymbolKind.STRUCT.isType());
    }

    @Test
    void testIsType_trait() {
        assertTrue(SymbolKind.TRAIT.isType());
    }

    @Test
    void testIsType_annotation() {
        assertTrue(SymbolKind.ANNOTATION.isType());
    }

    @Test
    void testIsType_method() {
        assertFalse(SymbolKind.METHOD.isType());
    }

    @Test
    void testIsCallable_method() {
        assertTrue(SymbolKind.METHOD.isCallable());
    }

    @Test
    void testIsCallable_staticMethod() {
        assertTrue(SymbolKind.STATIC_METHOD.isCallable());
    }

    @Test
    void testIsCallable_abstractMethod() {
        assertTrue(SymbolKind.ABSTRACT_METHOD.isCallable());
    }

    @Test
    void testIsCallable_constructor() {
        assertTrue(SymbolKind.CONSTRUCTOR.isCallable());
    }

    @Test
    void testIsCallable_function() {
        assertTrue(SymbolKind.FUNCTION.isCallable());
    }

    @Test
    void testIsCallable_lambda() {
        assertTrue(SymbolKind.LAMBDA.isCallable());
    }

    @Test
    void testIsCallable_class() {
        assertFalse(SymbolKind.CLASS.isCallable());
    }

    @Test
    void testIsField_field() {
        assertTrue(SymbolKind.FIELD.isField());
    }

    @Test
    void testIsField_staticField() {
        assertTrue(SymbolKind.STATIC_FIELD.isField());
    }

    @Test
    void testIsField_constant() {
        assertTrue(SymbolKind.CONSTANT.isField());
    }

    @Test
    void testIsField_property() {
        assertTrue(SymbolKind.PROPERTY.isField());
    }

    @Test
    void testIsField_method() {
        assertFalse(SymbolKind.METHOD.isField());
    }

    @Test
    void testIsVariable_variable() {
        assertTrue(SymbolKind.VARIABLE.isVariable());
    }

    @Test
    void testIsVariable_parameter() {
        assertTrue(SymbolKind.PARAMETER.isVariable());
    }

    @Test
    void testIsVariable_field() {
        assertFalse(SymbolKind.FIELD.isVariable());
    }

    @Test
    void testIsContainer_package() {
        assertTrue(SymbolKind.PACKAGE.isContainer());
    }

    @Test
    void testIsContainer_module() {
        assertTrue(SymbolKind.MODULE.isContainer());
    }

    @Test
    void testIsContainer_class() {
        assertTrue(SymbolKind.CLASS.isContainer());
    }

    @Test
    void testIsContainer_method() {
        assertTrue(SymbolKind.METHOD.isContainer());
    }

    @Test
    void testIsContainer_field() {
        assertFalse(SymbolKind.FIELD.isContainer());
    }

    @Test
    void testFromLegacyScipKind() {
        assertEquals(SymbolKind.PACKAGE, SymbolKind.fromLegacyScipKind(1));
        assertEquals(SymbolKind.CLASS, SymbolKind.fromLegacyScipKind(2));
        assertEquals(SymbolKind.METHOD, SymbolKind.fromLegacyScipKind(3));
        assertEquals(SymbolKind.FIELD, SymbolKind.fromLegacyScipKind(4));
        assertEquals(SymbolKind.CONSTRUCTOR, SymbolKind.fromLegacyScipKind(5));
        assertEquals(SymbolKind.INTERFACE, SymbolKind.fromLegacyScipKind(6));
        assertEquals(SymbolKind.VARIABLE, SymbolKind.fromLegacyScipKind(7));
        assertEquals(SymbolKind.FUNCTION, SymbolKind.fromLegacyScipKind(8));
        assertEquals(SymbolKind.UNKNOWN, SymbolKind.fromLegacyScipKind(999));
    }
}
