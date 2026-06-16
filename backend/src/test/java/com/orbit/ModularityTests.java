package com.orbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architectural regression test for the Orbit Spring Modulith modules.
 *
 * <p>
 * This is the failing gate for every future PR: it verifies that modules stay
 * encapsulated, that dependencies remain acyclic, and that no module imports
 * another module's internal packages. It also locks the intended dependency
 * directions so that accidentally adding a new allowed-but-wrong module
 * dependency is caught.
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(OrbitApplication.class);

    /**
     * Fails the build on:
     * <ul>
     * <li>cross-module imports of non-exported types (e.g. another module's
     * {@code internal} package)</li>
     * <li>cyclic module dependencies</li>
     * <li>dependencies on types that are not part of a published API</li>
     * </ul>
     */
    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    /**
     * Emits the C4-style component diagram and per-module canvases to
     * {@code target/spring-modulith-docs/}. These are both proof that the
     * dependency graph matches the intended design and onboarding artifacts.
     */
    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }

    @Test
    void embeddingHasNoOrbitDependencies() {
        assertDependenciesSubset("embedding", Set.of());
    }

    @Test
    void authHasNoOrbitDependencies() {
        assertDependenciesSubset("auth", Set.of());
    }

    @Test
    void profileOnlyDependsOnEmbeddingAndAuth() {
        assertDependenciesSubset("profile", Set.of("embedding", "auth"));
    }

    @Test
    void requestOnlyDependsOnEmbeddingAndAuth() {
        assertDependenciesSubset("request", Set.of("embedding", "auth"));
    }

    @Test
    void discoveryOnlyDependsOnProfileRequestEmbeddingAndAuth() {
        assertDependenciesSubset("discovery", Set.of("profile", "request", "embedding", "auth"));
    }

    @Test
    void onboardingOnlyDependsOnProfileEmbeddingAndAuth() {
        assertDependenciesSubset("onboarding", Set.of("profile", "embedding", "auth"));
    }

    @Test
    void contactOnlyDependsOnRequestNotificationAndAuth() {
        assertDependenciesSubset("contact", Set.of("request", "notification", "auth"));
    }

    @Test
    void notificationOnlyDependsOnRequestAndAuth() {
        assertDependenciesSubset("notification", Set.of("request", "auth"));
    }

    /**
     * Asserts that all module dependencies of {@code moduleName} are contained in
     * {@code allowedDependencies}. The assertion fails if an out-of-policy
     * dependency is introduced.
     */
    private void assertDependenciesSubset(String moduleName, Set<String> allowedDependencies) {
        var module = modules.getModuleByName(moduleName)
                .orElseThrow(() -> new AssertionError("Module not found: " + moduleName));

        var actualDependencies = module.getDependencies(modules)
                .uniqueModules()
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet());

        assertThat(actualDependencies)
                .as("Dependencies of module '%s' must be subset of %s", moduleName, allowedDependencies)
                .isSubsetOf(allowedDependencies);
    }

}
