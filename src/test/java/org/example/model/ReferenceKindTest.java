package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReferenceKind enum.
 */
class ReferenceKindTest {

    @Test
    void testFromScipRole_definition() {
        assertEquals(ReferenceKind.DEFINITION, ReferenceKind.fromScipRole(0x1));
    }

    @Test
    void testFromScipRole_import() {
        assertEquals(ReferenceKind.IMPORT, ReferenceKind.fromScipRole(0x2));
    }

    @Test
    void testFromScipRole_write() {
        assertEquals(ReferenceKind.WRITE, ReferenceKind.fromScipRole(0x4));
    }

    @Test
    void testFromScipRole_read() {
        assertEquals(ReferenceKind.READ, ReferenceKind.fromScipRole(0x8));
    }

    @Test
    void testFromScipRole_forwardDefinition() {
        assertEquals(ReferenceKind.FORWARD_DEFINITION, ReferenceKind.fromScipRole(0x40));
    }

    @Test
    void testFromScipRole_reference() {
        assertEquals(ReferenceKind.REFERENCE, ReferenceKind.fromScipRole(0));
    }

    @Test
    void testFromTargetSymbolKind_method() {
        assertEquals(ReferenceKind.CALL, ReferenceKind.fromTargetSymbolKind(SymbolKind.METHOD));
    }

    @Test
    void testFromTargetSymbolKind_function() {
        assertEquals(ReferenceKind.CALL, ReferenceKind.fromTargetSymbolKind(SymbolKind.FUNCTION));
    }

    @Test
    void testFromTargetSymbolKind_constructor() {
        assertEquals(ReferenceKind.CALL, ReferenceKind.fromTargetSymbolKind(SymbolKind.CONSTRUCTOR));
    }

    @Test
    void testFromTargetSymbolKind_class() {
        assertEquals(ReferenceKind.TYPE_REF, ReferenceKind.fromTargetSymbolKind(SymbolKind.CLASS));
    }

    @Test
    void testFromTargetSymbolKind_interface() {
        assertEquals(ReferenceKind.TYPE_REF, ReferenceKind.fromTargetSymbolKind(SymbolKind.INTERFACE));
    }

    @Test
    void testFromTargetSymbolKind_field() {
        assertEquals(ReferenceKind.FIELD_ACCESS, ReferenceKind.fromTargetSymbolKind(SymbolKind.FIELD));
    }

    @Test
    void testFromTargetSymbolKind_annotation() {
        assertEquals(ReferenceKind.ANNOTATED_WITH, ReferenceKind.fromTargetSymbolKind(SymbolKind.ANNOTATION));
    }

    @Test
    void testIsStructural() {
        assertTrue(ReferenceKind.CONTAINS.isStructural());
        assertFalse(ReferenceKind.CALL.isStructural());
    }

    @Test
    void testIsTypeRelationship() {
        assertTrue(ReferenceKind.HAS_TYPE.isTypeRelationship());
        assertTrue(ReferenceKind.RETURNS_TYPE.isTypeRelationship());
        assertTrue(ReferenceKind.TYPE_REF.isTypeRelationship());
        assertFalse(ReferenceKind.CALL.isTypeRelationship());
    }

    @Test
    void testIsDependency() {
        assertTrue(ReferenceKind.CALL.isDependency());
        assertTrue(ReferenceKind.INJECTS.isDependency());
        assertTrue(ReferenceKind.IMPORT.isDependency());
        assertFalse(ReferenceKind.EXTENDS.isDependency());
    }

    @Test
    void testIsInheritance() {
        assertTrue(ReferenceKind.EXTENDS.isInheritance());
        assertTrue(ReferenceKind.IMPLEMENTS.isInheritance());
        assertFalse(ReferenceKind.CALL.isInheritance());
    }
}
