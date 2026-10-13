package com.l2c.nexus;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.l2c.nexus.department.persistence.DepartmentRepository;
import com.l2c.nexus.membership.persistence.MembershipRepository;
import com.l2c.nexus.request.persistence.ServiceRequestRepository;
import com.l2c.nexus.team.persistence.InvitationRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.RestController;

/**
 * The architecture rules of ADR-0001, ADR-0004 and ADR-0021 as executable checks. Production code
 * only (tests are excluded). When one fails, the message names the offending class and line.
 */
@AnalyzeClasses(packages = "com.l2c.nexus", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.l2c.nexus";

    /** Which modules each module may use. Anything else is a violation. */
    private static final Map<String, Set<String>> ALLOWED =
            Map.of(
                    "shared",
                    Set.of(),
                    "audit",
                    Set.of("shared"),
                    "membership",
                    Set.of("shared"),
                    "identity",
                    Set.of("audit", "shared"),
                    "organization",
                    Set.of("membership", "audit", "shared"),
                    "department",
                    Set.of("organization", "membership", "audit", "shared"),
                    "request",
                    Set.of("organization", "membership", "department", "audit", "shared"),
                    "team",
                    Set.of(
                            "organization",
                            "membership",
                            "identity",
                            "audit",
                            "department",
                            "request",
                            "shared"),
                    "dashboard",
                    Set.of("organization", "request", "shared"));

    @ArchTest
    static final ArchRule modulesHaveNoCycles =
            slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

    @ArchTest
    static void modulesOnlyUseWhatTheyAreAllowedTo(JavaClasses classes) {
        for (Map.Entry<String, Set<String>> module : ALLOWED.entrySet()) {
            Set<String> forbidden = new LinkedHashSet<>(ALLOWED.keySet());
            forbidden.removeAll(module.getValue());
            forbidden.remove(module.getKey());
            if (forbidden.isEmpty()) {
                continue;
            }
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "." + module.getKey() + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            forbidden.stream()
                                    .map(name -> BASE + "." + name + "..")
                                    .toArray(String[]::new))
                    .because("module " + module.getKey() + " may only use " + module.getValue())
                    .check(classes);
        }
    }

    @ArchTest
    static final ArchRule applicationServicesDoNotKnowTheWebLayer =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(BASE + "..api..");

    @ArchTest
    static final ArchRule domainClassesDependOnNoOtherLayer =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            BASE + "..application..",
                            BASE + "..api..",
                            BASE + "..persistence..",
                            BASE + "..security..",
                            BASE + "..web..");

    @ArchTest
    static final ArchRule controllersLiveInApiPackages =
            classes()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .resideInAPackage(BASE + "..api..");

    @ArchTest
    static final ArchRule controllersNeverExposeEntities =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "..api..")
                    .should()
                    .dependOnClassesThat()
                    .areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule controllersNeverTouchRepositories =
            noClasses()
                    .that()
                    .resideInAPackage(BASE + "..api..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(org.springframework.data.repository.Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAnnotatedWith(org.springframework.stereotype.Repository.class);

    @ArchTest
    static final ArchRule persistenceClassesArePrivateToTheirModule =
            classes()
                    .that()
                    .resideInAPackage(BASE + "..persistence..")
                    .should(onlyBeUsedByTheirOwnModule());

    @ArchTest
    static final ArchRule entitiesArePrivateToTheirModule =
            classes().that().areAnnotatedWith(Entity.class).should(onlyBeUsedByTheirOwnModule());

    /**
     * Tenant data is never loaded, listed, counted or deleted by id alone: only through methods
     * that take the organization (findByIdAndOrganizationId and friends). The organization table
     * itself is the tenant root and is exempt.
     */
    @ArchTest
    static final ArchRule tenantRepositoriesAreNeverUsedWithoutTheOrganization =
            noClasses()
                    .should()
                    .callMethodWhere(
                            target(
                                            nameMatching(
                                                    "findById|getById|getReferenceById|findAllById|findAll"
                                                            + "|deleteById|deleteAllById|deleteAll|existsById|count"))
                                    .and(target(owner(tenantRepositories()))))
                    .because("every tenant query must carry the organization (ADR-0004)");

    @ArchTest
    static final ArchRule noStandardStreams =
            GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging =
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noFieldInjection =
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noGenericExceptions =
            GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    private static DescribedPredicate<JavaClass> tenantRepositories() {
        return assignableTo(MembershipRepository.class)
                .or(assignableTo(DepartmentRepository.class))
                .or(assignableTo(InvitationRepository.class))
                .or(assignableTo(ServiceRequestRepository.class));
    }

    private static ArchCondition<JavaClass> onlyBeUsedByTheirOwnModule() {
        return new ArchCondition<>("only be used by their own module") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String module = moduleOf(item);
                for (Dependency dependency : item.getDirectDependenciesToSelf()) {
                    if (!moduleOf(dependency.getOriginClass()).equals(module)) {
                        events.add(violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String moduleOf(JavaClass javaClass) {
        String prefix = BASE + ".";
        String name = javaClass.getPackageName();
        if (!name.startsWith(prefix)) {
            return "(outside)";
        }
        String rest = name.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
