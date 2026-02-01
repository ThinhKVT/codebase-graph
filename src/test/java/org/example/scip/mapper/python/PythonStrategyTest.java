package org.example.scip.mapper.python;

import org.example.model.LanguageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PythonStrategy.
 */
class PythonStrategyTest {

    private PythonStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PythonStrategy();
    }

    @Test
    void testGetLanguage() {
        assertEquals(LanguageSupport.PYTHON, strategy.getLanguage());
    }

    // ============= parseParentSymbolId Tests =============

    @Test
    void testParseParentSymbolId_method() {
        // Method in class
        String methodId = "scip-python python pkg 1.0.0 module/MyClass#method().";
        String parentId = strategy.parseParentSymbolId(methodId);
        assertEquals("scip-python python pkg 1.0.0 module/MyClass#", parentId);
    }

    @Test
    void testParseParentSymbolId_function() {
        // Module-level function
        String funcId = "scip-python python pkg 1.0.0 module/function().";
        String parentId = strategy.parseParentSymbolId(funcId);
        assertEquals("scip-python python pkg 1.0.0 module/", parentId);
    }

    @Test
    void testParseParentSymbolId_field() {
        // Class attribute
        String fieldId = "scip-python python pkg 1.0.0 module/MyClass#field.";
        String parentId = strategy.parseParentSymbolId(fieldId);
        assertEquals("scip-python python pkg 1.0.0 module/MyClass#", parentId);
    }

    @Test
    void testParseParentSymbolId_class() {
        // Class in module
        String classId = "scip-python python pkg 1.0.0 module/MyClass#";
        String parentId = strategy.parseParentSymbolId(classId);
        assertEquals("scip-python python pkg 1.0.0 module/", parentId);
    }

    @Test
    void testParseParentSymbolId_null() {
        assertNull(strategy.parseParentSymbolId(null));
    }

    @Test
    void testParseParentSymbolId_empty() {
        assertNull(strategy.parseParentSymbolId(""));
    }

    // ============= isServiceType Tests =============

    @Test
    void testIsServiceType_service() {
        assertTrue(strategy.isServiceType("module/UserService#"));
    }

    @Test
    void testIsServiceType_repository() {
        assertTrue(strategy.isServiceType("module/UserRepository#"));
    }

    @Test
    void testIsServiceType_handler() {
        assertTrue(strategy.isServiceType("module/EventHandler#"));
    }

    @Test
    void testIsServiceType_client() {
        assertTrue(strategy.isServiceType("module/HttpClient#"));
    }

    @Test
    void testIsServiceType_controller() {
        assertTrue(strategy.isServiceType("module/UserController#"));
    }

    @Test
    void testIsServiceType_view() {
        // Django pattern
        assertTrue(strategy.isServiceType("module/UserView#"));
    }

    @Test
    void testIsServiceType_router() {
        // FastAPI pattern
        assertTrue(strategy.isServiceType("module/UserRouter#"));
    }

    @Test
    void testIsServiceType_plainClass() {
        assertFalse(strategy.isServiceType("module/User#"));
    }

    @Test
    void testIsServiceType_model() {
        assertFalse(strategy.isServiceType("module/UserModel#"));
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
