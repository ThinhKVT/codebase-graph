package org.example.scip.mapper.go;

import org.example.model.LanguageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GoStrategy.
 */
class GoStrategyTest {

    private GoStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GoStrategy();
    }

    @Test
    void testGetLanguage() {
        assertEquals(LanguageSupport.GO, strategy.getLanguage());
    }

    // ============= parseParentSymbolId Tests =============

    @Test
    void testParseParentSymbolId_method() {
        // Method of a struct
        String methodId = "scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#Method().";
        String parentId = strategy.parseParentSymbolId(methodId);
        assertEquals("scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#", parentId);
    }

    @Test
    void testParseParentSymbolId_function() {
        // Package-level function
        String funcId = "scip-go gomod github.com/user/pkg v1.0.0 pkg/Function().";
        String parentId = strategy.parseParentSymbolId(funcId);
        assertEquals("scip-go gomod github.com/user/pkg v1.0.0 pkg/", parentId);
    }

    @Test
    void testParseParentSymbolId_field() {
        // Field of a struct
        String fieldId = "scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#field.";
        String parentId = strategy.parseParentSymbolId(fieldId);
        assertEquals("scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#", parentId);
    }

    @Test
    void testParseParentSymbolId_struct() {
        // Struct in package
        String structId = "scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#";
        String parentId = strategy.parseParentSymbolId(structId);
        assertEquals("scip-go gomod github.com/user/pkg v1.0.0 pkg/", parentId);
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
        assertTrue(strategy.isServiceType("pkg/UserService#"));
    }

    @Test
    void testIsServiceType_repository() {
        assertTrue(strategy.isServiceType("pkg/UserRepository#"));
    }

    @Test
    void testIsServiceType_handler() {
        assertTrue(strategy.isServiceType("pkg/UserHandler#"));
    }

    @Test
    void testIsServiceType_client() {
        assertTrue(strategy.isServiceType("pkg/HttpClient#"));
    }

    @Test
    void testIsServiceType_store() {
        assertTrue(strategy.isServiceType("pkg/UserStore#"));
    }

    @Test
    void testIsServiceType_interfaceWithEr() {
        // Go interface naming convention: ends with "er"
        assertTrue(strategy.isServiceType("pkg/Reader#"));
        assertTrue(strategy.isServiceType("pkg/Writer#"));
        assertTrue(strategy.isServiceType("pkg/Handler#"));
    }

    @Test
    void testIsServiceType_plainStruct() {
        assertFalse(strategy.isServiceType("pkg/User#"));
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
