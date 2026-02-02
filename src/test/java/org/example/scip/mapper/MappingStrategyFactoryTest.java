package org.example.scip.mapper;

import org.example.model.LanguageSupport;
import org.example.scip.mapper.go.GoStrategy;
import org.example.scip.mapper.java.JavaStrategy;
import org.example.scip.mapper.python.PythonStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MappingStrategyFactory.
 */
class MappingStrategyFactoryTest {

    @Test
    void testCreateFromString_java() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create("java");
        assertInstanceOf(JavaStrategy.class, strategy);
        assertEquals(LanguageSupport.JAVA, strategy.getLanguage());
    }

    @Test
    void testCreateFromString_go() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create("go");
        assertInstanceOf(GoStrategy.class, strategy);
        assertEquals(LanguageSupport.GO, strategy.getLanguage());
    }

    @Test
    void testCreateFromString_golang() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create("golang");
        assertInstanceOf(GoStrategy.class, strategy);
    }

    @Test
    void testCreateFromString_python() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create("python");
        assertInstanceOf(PythonStrategy.class, strategy);
        assertEquals(LanguageSupport.PYTHON, strategy.getLanguage());
    }

    @Test
    void testCreateFromString_py() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create("py");
        assertInstanceOf(PythonStrategy.class, strategy);
    }

    @Test
    void testCreateFromString_caseInsensitive() {
        assertInstanceOf(JavaStrategy.class, MappingStrategyFactory.create("JAVA"));
        assertInstanceOf(GoStrategy.class, MappingStrategyFactory.create("GO"));
        assertInstanceOf(PythonStrategy.class, MappingStrategyFactory.create("Python"));
    }

    @Test
    void testCreateFromString_null() {
        // Null defaults to Java
        LanguageMappingStrategy strategy = MappingStrategyFactory.create((String) null);
        assertInstanceOf(JavaStrategy.class, strategy);
    }

    @Test
    void testCreateFromString_blank() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create("  ");
        assertInstanceOf(JavaStrategy.class, strategy);
    }

    @Test
    void testCreateFromString_unsupported() {
        assertThrows(IllegalArgumentException.class, () -> 
            MappingStrategyFactory.create("rust"));
    }

    @Test
    void testCreateFromEnum_java() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create(LanguageSupport.JAVA);
        assertInstanceOf(JavaStrategy.class, strategy);
    }

    @Test
    void testCreateFromEnum_go() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create(LanguageSupport.GO);
        assertInstanceOf(GoStrategy.class, strategy);
    }

    @Test
    void testCreateFromEnum_python() {
        LanguageMappingStrategy strategy = MappingStrategyFactory.create(LanguageSupport.PYTHON);
        assertInstanceOf(PythonStrategy.class, strategy);
    }

    @Test
    void testGetAllStrategies() {
        var strategies = MappingStrategyFactory.getAllStrategies();
        assertEquals(3, strategies.size());
        
        assertTrue(strategies.stream().anyMatch(s -> s instanceof JavaStrategy));
        assertTrue(strategies.stream().anyMatch(s -> s instanceof GoStrategy));
        assertTrue(strategies.stream().anyMatch(s -> s instanceof PythonStrategy));
    }
}
