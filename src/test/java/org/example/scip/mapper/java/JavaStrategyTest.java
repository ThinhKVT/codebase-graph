package org.example.scip.mapper.java;

import org.example.model.LanguageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JavaStrategy.
 */
class JavaStrategyTest {

    private JavaStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new JavaStrategy();
    }

    @Test
    void testGetLanguage() {
        assertEquals(LanguageSupport.JAVA, strategy.getLanguage());
    }

    // ============= parseParentSymbolId Tests =============

    @Test
    void testParseParentSymbolId_method() {
        // Method in class
        String methodId = "scip-java maven . . . com/example/MyClass#myMethod().";
        String parentId = strategy.parseParentSymbolId(methodId);
        assertEquals("scip-java maven . . . com/example/MyClass#", parentId);
    }

    @Test
    void testParseParentSymbolId_field() {
        // Field in class
        String fieldId = "scip-java maven . . . com/example/MyClass#myField.";
        String parentId = strategy.parseParentSymbolId(fieldId);
        assertEquals("scip-java maven . . . com/example/MyClass#", parentId);
    }

    @Test
    void testParseParentSymbolId_class() {
        // Top-level class has no parent (or package)
        String classId = "scip-java maven . . . com/example/MyClass#";
        String parentId = strategy.parseParentSymbolId(classId);
        assertNull(parentId); // No inner # before last #
    }

    @Test
    void testParseParentSymbolId_innerClass() {
        // Inner class in outer class
        String innerClassId = "scip-java maven . . . com/example/Outer#Inner#";
        String parentId = strategy.parseParentSymbolId(innerClassId);
        assertEquals("scip-java maven . . . com/example/Outer#Inner#", parentId);
    }

    @Test
    void testParseParentSymbolId_null() {
        assertNull(strategy.parseParentSymbolId(null));
    }

    @Test
    void testParseParentSymbolId_empty() {
        assertNull(strategy.parseParentSymbolId(""));
    }

    @Test
    void testParseParentSymbolId_noHash() {
        // Symbol without # has no parent
        assertNull(strategy.parseParentSymbolId("scip-java maven . . . com/example/"));
    }

    // ============= isServiceType Tests =============

    @Test
    void testIsServiceType_service() {
        assertTrue(strategy.isServiceType("com/example/UserService#"));
    }

    @Test
    void testIsServiceType_repository() {
        assertTrue(strategy.isServiceType("com/example/UserRepository#"));
    }

    @Test
    void testIsServiceType_controller() {
        assertTrue(strategy.isServiceType("com/example/UserController#"));
    }

    @Test
    void testIsServiceType_handler() {
        assertTrue(strategy.isServiceType("com/example/EventHandler#"));
    }

    @Test
    void testIsServiceType_client() {
        assertTrue(strategy.isServiceType("com/example/HttpClient#"));
    }

    @Test
    void testIsServiceType_dao() {
        assertTrue(strategy.isServiceType("com/example/UserDao#"));
    }

    @Test
    void testIsServiceType_factory() {
        assertTrue(strategy.isServiceType("com/example/UserFactory#"));
    }

    @Test
    void testIsServiceType_plainClass() {
        // Plain class without service pattern
        // Note: ends with "#" so it's considered a type (potentially injectable)
        // This is intentional - interfaces and types are commonly injected
        assertTrue(strategy.isServiceType("com/example/User#"));
    }

    @Test
    void testIsServiceType_nonType() {
        // Non-type symbol (method, field) should not be a service type
        assertFalse(strategy.isServiceType("com/example/User#getName()."));
    }

    @Test
    void testIsServiceType_null() {
        assertFalse(strategy.isServiceType(null));
    }

    @Test
    void testIsServiceType_empty() {
        assertFalse(strategy.isServiceType(""));
    }
}
