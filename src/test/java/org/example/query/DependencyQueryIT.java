package org.example.query;

import org.example.graph.GraphStore;
import org.example.graph.Neo4jGraphStore;
import org.example.model.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DependencyQuery with Neo4j.
 */
@Testcontainers
class DependencyQueryIT {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
        .withAdminPassword("testpassword");

    private static GraphStore graphStore;
    private static DependencyQuery dependencyQuery;

    @BeforeAll
    static void setUpGraph() throws Exception {
        graphStore = new Neo4jGraphStore(
            neo4j.getBoltUrl(),
            "neo4j",
            "testpassword"
        );
        graphStore.connect();
        graphStore.createIndexes();

        // Seed test data with dependency hierarchy:
        // UserController -> UserService -> UserRepository
        //                -> LogService
        // AdminController -> UserService (shared dependency)
        seedTestData();

        dependencyQuery = new DependencyQuery(graphStore);
    }

    private static void seedTestData() {
        // Create symbols
        Symbol userController = Symbol.builder()
            .id("sym-user-controller")
            .name("UserController")
            .fullyQualifiedName("com.example.controller.UserController")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/controller/UserController.java")
            .startLine(10)
            .build();

        Symbol adminController = Symbol.builder()
            .id("sym-admin-controller")
            .name("AdminController")
            .fullyQualifiedName("com.example.controller.AdminController")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/controller/AdminController.java")
            .startLine(10)
            .build();

        Symbol userService = Symbol.builder()
            .id("sym-user-service")
            .name("UserService")
            .fullyQualifiedName("com.example.service.UserService")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/service/UserService.java")
            .startLine(15)
            .build();

        Symbol logService = Symbol.builder()
            .id("sym-log-service")
            .name("LogService")
            .fullyQualifiedName("com.example.service.LogService")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/service/LogService.java")
            .startLine(8)
            .build();

        Symbol userRepository = Symbol.builder()
            .id("sym-user-repo")
            .name("UserRepository")
            .fullyQualifiedName("com.example.repository.UserRepository")
            .kind(SymbolKind.INTERFACE)
            .filePath("src/main/java/com/example/repository/UserRepository.java")
            .startLine(5)
            .build();

        Symbol baseEntity = Symbol.builder()
            .id("sym-base-entity")
            .name("BaseEntity")
            .fullyQualifiedName("com.example.model.BaseEntity")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/model/BaseEntity.java")
            .startLine(3)
            .build();

        Symbol userEntity = Symbol.builder()
            .id("sym-user-entity")
            .name("User")
            .fullyQualifiedName("com.example.model.User")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/model/User.java")
            .startLine(5)
            .build();

        graphStore.saveSymbols(List.of(
            userController, adminController, userService, logService,
            userRepository, baseEntity, userEntity
        ));

        // Create dependency references
        // UserController -> UserService (IMPORT)
        Reference ref1 = Reference.builder()
            .fromSymbolId("sym-user-controller")
            .toSymbolId("sym-user-service")
            .kind(ReferenceKind.IMPORT)
            .filePath("src/main/java/com/example/controller/UserController.java")
            .line(3)
            .build();

        // UserController -> LogService (IMPORT)
        Reference ref2 = Reference.builder()
            .fromSymbolId("sym-user-controller")
            .toSymbolId("sym-log-service")
            .kind(ReferenceKind.IMPORT)
            .filePath("src/main/java/com/example/controller/UserController.java")
            .line(4)
            .build();

        // UserService -> UserRepository (IMPORT)
        Reference ref3 = Reference.builder()
            .fromSymbolId("sym-user-service")
            .toSymbolId("sym-user-repo")
            .kind(ReferenceKind.IMPORT)
            .filePath("src/main/java/com/example/service/UserService.java")
            .line(3)
            .build();

        // AdminController -> UserService (IMPORT)
        Reference ref4 = Reference.builder()
            .fromSymbolId("sym-admin-controller")
            .toSymbolId("sym-user-service")
            .kind(ReferenceKind.IMPORT)
            .filePath("src/main/java/com/example/controller/AdminController.java")
            .line(3)
            .build();

        // User extends BaseEntity (EXTENDS)
        Reference ref5 = Reference.builder()
            .fromSymbolId("sym-user-entity")
            .toSymbolId("sym-base-entity")
            .kind(ReferenceKind.EXTENDS)
            .filePath("src/main/java/com/example/model/User.java")
            .line(5)
            .build();

        // UserRepository uses User (TYPE_REFERENCE)
        Reference ref6 = Reference.builder()
            .fromSymbolId("sym-user-repo")
            .toSymbolId("sym-user-entity")
            .kind(ReferenceKind.REFERENCE)
            .filePath("src/main/java/com/example/repository/UserRepository.java")
            .line(7)
            .build();

        graphStore.saveReferences(List.of(ref1, ref2, ref3, ref4, ref5, ref6));
    }

    @AfterAll
    static void tearDown() {
        if (graphStore != null) {
            graphStore.close();
        }
    }

    // ==================== Direct Dependency Tests ====================

    @Test
    void testFindDirectDependencies() {
        List<Symbol> deps = dependencyQuery.findDirectDependencies("sym-user-controller");

        assertEquals(2, deps.size());
        assertTrue(deps.stream().anyMatch(s -> s.name().equals("UserService")));
        assertTrue(deps.stream().anyMatch(s -> s.name().equals("LogService")));
    }

    @Test
    void testFindDirectDependencies_noDeps() {
        List<Symbol> deps = dependencyQuery.findDirectDependencies("sym-base-entity");

        assertTrue(deps.isEmpty());
    }

    // ==================== Transitive Dependency Tests ====================

    @Test
    void testFindTransitiveDependencies_depth1() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "com.example.controller.UserController", 1);

        assertFalse(result.isEmpty());
        assertEquals("UserController", result.rootSymbol().name());
        assertEquals(2, result.totalDependencies()); // UserService, LogService
    }

    @Test
    void testFindTransitiveDependencies_depth2() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "com.example.controller.UserController", 2);

        assertFalse(result.isEmpty());
        // UserController -> UserService -> UserRepository
        // UserController -> LogService
        assertEquals(3, result.totalDependencies());
    }

    @Test
    void testFindTransitiveDependencies_depth3() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "com.example.controller.UserController", 3);

        assertFalse(result.isEmpty());
        // UserController -> UserService -> UserRepository -> User
        // UserController -> LogService
        assertTrue(result.totalDependencies() >= 3);
    }

    @Test
    void testFindDependencies_byName() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies("UserController", 1);

        assertFalse(result.isEmpty());
        assertEquals("UserController", result.rootSymbol().name());
    }

    @Test
    void testFindDependencies_notFound() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies("NonExistent", 1);

        assertTrue(result.isEmpty());
    }

    // ==================== Dependent (Reverse) Tests ====================

    @Test
    void testFindDirectDependents() {
        List<Symbol> dependents = dependencyQuery.findDirectDependents("sym-user-service");

        assertEquals(2, dependents.size());
        assertTrue(dependents.stream().anyMatch(s -> s.name().equals("UserController")));
        assertTrue(dependents.stream().anyMatch(s -> s.name().equals("AdminController")));
    }

    @Test
    void testFindDependents_depth1() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependents(
            "com.example.service.UserService", 1);

        assertFalse(result.isEmpty());
        assertEquals(2, result.totalDependencies()); // UserController, AdminController
    }

    @Test
    void testFindDependents_noCallers() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependents(
            "com.example.controller.UserController", 1);

        assertFalse(result.isEmpty());
        assertEquals(0, result.totalDependencies()); // Nothing depends on controller
    }

    // ==================== Tree Structure Tests ====================

    @Test
    void testDependencyTreeStructure() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "UserController", 2);

        assertNotNull(result.tree());
        assertFalse(result.tree().children().isEmpty());

        // Find UserService in children
        var userServiceNode = result.tree().children().stream()
            .filter(n -> n.symbol().name().equals("UserService"))
            .findFirst();

        assertTrue(userServiceNode.isPresent());
        // UserService should have UserRepository as child
        assertFalse(userServiceNode.get().children().isEmpty());
    }

    @Test
    void testFlattenDependencies() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "UserController", 2);

        List<Symbol> flat = dependencyQuery.flattenDependencies(result.tree());

        assertFalse(flat.isEmpty());
        assertTrue(flat.stream().anyMatch(s -> s.name().equals("UserService")));
        assertTrue(flat.stream().anyMatch(s -> s.name().equals("UserRepository")));
    }

    // ==================== Output Format Tests ====================

    @Test
    void testTreeStringOutput() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "UserController", 1);

        String tree = result.tree().toTreeString();

        assertNotNull(tree);
        assertTrue(tree.contains("UserController"));
        assertTrue(tree.contains("├──") || tree.contains("└──"));
    }

    @Test
    void testJsonStringOutput() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "UserController", 1);

        String json = result.tree().toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"dependencies\""));
    }

    // ==================== Inheritance Tests ====================

    @Test
    void testExtendsDependency() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependencies(
            "com.example.model.User", 1);

        assertFalse(result.isEmpty());
        assertEquals(1, result.totalDependencies());
        assertEquals("BaseEntity", result.tree().children().get(0).symbol().name());
    }

    @Test
    void testExtendsDependent() {
        DependencyQuery.DependencyResult result = dependencyQuery.findDependents(
            "com.example.model.BaseEntity", 1);

        assertFalse(result.isEmpty());
        assertTrue(result.tree().children().stream()
            .anyMatch(n -> n.symbol().name().equals("User")));
    }
}

