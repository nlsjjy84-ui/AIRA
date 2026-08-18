package com.aira.api.market.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SourceAuthorityScopeTests {
    @Test
    void activeDefaultsToTrue() {
        assertTrue(new SourceAuthorityScope().isActive());
    }

    @Test
    void authorityScopeTypeContainsExactlyV1Values() {
        assertArrayEquals(new AuthorityScopeType[] {
                AuthorityScopeType.COMPANY_REPORTED_RESULTS,
                AuthorityScopeType.COMPANY_OFFICIAL_STATEMENT,
                AuthorityScopeType.REGULATORY_DISCLOSURE,
                AuthorityScopeType.LISTING_STATUS,
                AuthorityScopeType.MARKET_TRADING_DATA,
                AuthorityScopeType.MONETARY_POLICY_DECISION,
                AuthorityScopeType.OFFICIAL_ECONOMIC_STATISTICS
        }, AuthorityScopeType.values());
    }

    @Test
    void authorityRoleContainsExactlyV1Values() {
        assertArrayEquals(new AuthorityRole[] {
                AuthorityRole.ORIGINATOR,
                AuthorityRole.OFFICIAL_OPERATOR,
                AuthorityRole.OFFICIAL_REPOSITORY,
                AuthorityRole.DECISION_AUTHORITY
        }, AuthorityRole.values());
    }

    @Test
    void timestampLifecycleInitializesAndUpdatesExpectedFields() throws Exception {
        SourceAuthorityScope scope = new SourceAuthorityScope();

        invokeLifecycle(scope, PrePersist.class);

        OffsetDateTime createdAt = scope.getCreatedAt();
        OffsetDateTime initialUpdatedAt = scope.getUpdatedAt();
        assertNotNull(createdAt);
        assertNotNull(initialUpdatedAt);
        assertEquals(createdAt, initialUpdatedAt);

        OffsetDateTime oldUpdatedAt = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        setField(scope, "updatedAt", oldUpdatedAt);
        invokeLifecycle(scope, PreUpdate.class);

        assertEquals(createdAt, scope.getCreatedAt());
        assertTrue(scope.getUpdatedAt().isAfter(oldUpdatedAt));
    }

    @Test
    void jpaRelationshipsAndEnumsMatchRegistryDesign() throws Exception {
        assertManyToOne("source", false, false);
        assertManyToOne("subjectEntity", true, true);
        assertManyToOne("jurisdictionEntity", true, true);
        assertStringEnum("scopeType");
        assertStringEnum("authorityRole");
    }

    private static void assertManyToOne(
            String fieldName, boolean nullable, boolean optional) throws Exception {
        Field field = SourceAuthorityScope.class.getDeclaredField(fieldName);
        ManyToOne relation = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertNotNull(relation);
        assertEquals(optional, relation.optional());
        assertEquals(nullable, joinColumn.nullable());
        assertArrayEquals(new CascadeType[0], relation.cascade());
        assertNull(field.getAnnotation(OneToMany.class));
        assertNull(field.getAnnotation(OneToOne.class));
    }

    private static void assertStringEnum(String fieldName) throws Exception {
        Field field = SourceAuthorityScope.class.getDeclaredField(fieldName);
        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        Column column = field.getAnnotation(Column.class);

        assertNotNull(enumerated);
        assertEquals(EnumType.STRING, enumerated.value());
        assertFalse(column.nullable());
    }

    private static void invokeLifecycle(
            SourceAuthorityScope scope, Class<? extends Annotation> annotationType) throws Exception {
        for (Method method : SourceAuthorityScope.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotationType)) {
                method.setAccessible(true);
                method.invoke(scope);
                return;
            }
        }
        throw new AssertionError("Missing lifecycle callback: " + annotationType.getSimpleName());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
