package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fact_assertion")
public class FactAssertion {
    @EmbeddedId
    private FactAssertionId id;

    @MapsId("factId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fact_id", nullable = false)
    private Fact fact;

    @MapsId("evidenceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(nullable = false, columnDefinition = "text")
    private String locator;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private FactValueType valueType;

    @Column(name = "value_number")
    private BigDecimal valueNumber;

    @Column(name = "value_text", columnDefinition = "text")
    private String valueText;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_timestamp")
    private OffsetDateTime valueTimestamp;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected FactAssertion() {}

    public static FactAssertion assertedNumber(Fact fact, Evidence evidence, String locator,
            BigDecimal value, OffsetDateTime now) {
        if (fact == null || fact.getId() == null || evidence == null || evidence.getId() == null
                || locator == null || locator.isBlank() || value == null || now == null) {
            throw new IllegalArgumentException("Number assertion values are required");
        }
        FactAssertion assertion = new FactAssertion();
        assertion.id = new FactAssertionId(fact.getId(), evidence.getId());
        assertion.fact = fact;
        assertion.evidence = evidence;
        assertion.locator = locator;
        assertion.valueType = FactValueType.NUMBER;
        assertion.valueNumber = value;
        assertion.createdAt = now;
        return assertion;
    }

    public FactAssertionId getId() { return id; }
    public Fact getFact() { return fact; }
    public Evidence getEvidence() { return evidence; }
    public String getLocator() { return locator; }
    public FactValueType getValueType() { return valueType; }
    public BigDecimal getValueNumber() { return valueNumber; }
    public String getValueText() { return valueText; }
    public Boolean getValueBoolean() { return valueBoolean; }
    public LocalDate getValueDate() { return valueDate; }
    public OffsetDateTime getValueTimestamp() { return valueTimestamp; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
