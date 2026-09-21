package com.aira.api.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fact")
public class Fact {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_entity_id", nullable = false)
    private MarketEntity subjectEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FactPredicate predicate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FactStatus status;

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

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "as_of_at")
    private OffsetDateTime asOfAt;

    @Column(name = "dedup_key", nullable = false)
    private byte[] dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_reason", length = 40)
    private FactStatusReason statusReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Fact() {}

    public static Fact supportedNumber(MarketEntity subjectEntity, Event event,
            FactPredicate predicate, BigDecimal value, String currencyCode,
            LocalDate periodStart, LocalDate periodEnd, byte[] dedupKey,
            OffsetDateTime now) {
        if (subjectEntity == null || subjectEntity.getId() == null
                || (event != null && event.getId() == null) || !isEarningsPredicate(predicate) || value == null
                || !isCurrencyCode(currencyCode) || periodStart == null || periodEnd == null
                || periodStart.isAfter(periodEnd) || dedupKey == null || dedupKey.length != 32
                || now == null) {
            throw new IllegalArgumentException("Supported number fact values are required");
        }
        Fact fact = new Fact();
        fact.subjectEntity = subjectEntity;
        fact.event = event;
        fact.predicate = predicate;
        fact.status = FactStatus.SUPPORTED;
        fact.valueType = FactValueType.NUMBER;
        fact.valueNumber = value;
        fact.currencyCode = currencyCode;
        fact.periodStart = periodStart;
        fact.periodEnd = periodEnd;
        fact.dedupKey = dedupKey.clone();
        fact.createdAt = now;
        fact.updatedAt = now;
        return fact;
    }

    public static Fact supportedStatisticalNumber(MarketEntity subjectEntity,
            FactPredicate predicate, BigDecimal value,
            LocalDate periodStart, LocalDate periodEnd, byte[] dedupKey,
            OffsetDateTime now) {
        if (subjectEntity == null || subjectEntity.getId() == null
                || subjectEntity.getEntityType() != EntityType.COUNTRY
                || predicate != FactPredicate.REAL_GDP || value == null
                || periodStart == null || periodEnd == null || !isCalendarQuarter(periodStart, periodEnd)
                || dedupKey == null || dedupKey.length != 32 || now == null) {
            throw new IllegalArgumentException("Supported statistical number fact values are required");
        }
        Fact fact = new Fact();
        fact.subjectEntity = subjectEntity;
        fact.predicate = predicate;
        fact.status = FactStatus.SUPPORTED;
        fact.valueType = FactValueType.NUMBER;
        fact.valueNumber = value;
        fact.periodStart = periodStart;
        fact.periodEnd = periodEnd;
        fact.dedupKey = dedupKey.clone();
        fact.createdAt = now;
        fact.updatedAt = now;
        return fact;
    }

    public static Fact supportedMarketNumber(MarketEntity subjectEntity, FactPredicate predicate,
            BigDecimal value, LocalDate tradingDate, byte[] dedupKey, OffsetDateTime now) {
        boolean shares = predicate == FactPredicate.TRADING_VOLUME || predicate == FactPredicate.LISTED_SHARES;
        boolean monetary = predicate == FactPredicate.OPEN_PRICE || predicate == FactPredicate.HIGH_PRICE
                || predicate == FactPredicate.LOW_PRICE || predicate == FactPredicate.CLOSE_PRICE
                || predicate == FactPredicate.TRADING_VALUE || predicate == FactPredicate.MARKET_CAP;
        if (subjectEntity == null || subjectEntity.getId() == null
                || subjectEntity.getEntityType() != EntityType.SECURITY || (!shares && !monetary)
                || value == null || value.signum() < 0 || (shares && value.stripTrailingZeros().scale() > 0)
                || tradingDate == null || dedupKey == null || dedupKey.length != 32 || now == null) {
            throw new IllegalArgumentException("Supported daily market fact values are required");
        }
        Fact fact = new Fact();
        fact.subjectEntity = subjectEntity;
        fact.predicate = predicate;
        fact.status = FactStatus.SUPPORTED;
        fact.valueType = FactValueType.NUMBER;
        fact.valueNumber = value;
        fact.currencyCode = monetary ? "KRW" : null;
        fact.periodStart = tradingDate;
        fact.periodEnd = tradingDate;
        fact.dedupKey = dedupKey.clone();
        fact.createdAt = now;
        fact.updatedAt = now;
        return fact;
    }

    public static Fact supportedMarketIndexNumber(MarketEntity subjectEntity, FactPredicate predicate,
            BigDecimal value, LocalDate tradingDate, byte[] dedupKey, OffsetDateTime now) {
        boolean indexPredicate = predicate == FactPredicate.INDEX_CLOSE
                || predicate == FactPredicate.INDEX_CHANGE || predicate == FactPredicate.INDEX_CHANGE_RATE;
        if (subjectEntity == null || subjectEntity.getId() == null
                || subjectEntity.getEntityType() != EntityType.MARKET || !indexPredicate
                || value == null || (predicate == FactPredicate.INDEX_CLOSE && value.signum() < 0)
                || tradingDate == null || dedupKey == null || dedupKey.length != 32 || now == null) {
            throw new IllegalArgumentException("Supported daily market-index fact values are required");
        }
        Fact fact = new Fact();
        fact.subjectEntity = subjectEntity;
        fact.predicate = predicate;
        fact.status = FactStatus.SUPPORTED;
        fact.valueType = FactValueType.NUMBER;
        fact.valueNumber = value;
        fact.periodStart = tradingDate;
        fact.periodEnd = tradingDate;
        fact.dedupKey = dedupKey.clone();
        fact.createdAt = now;
        fact.updatedAt = now;
        return fact;
    }

    public void markConflicting(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Fact update time is required");
        }
        if (status != FactStatus.CONFLICTING) {
            valueNumber = null;
            valueText = null;
            valueBoolean = null;
            valueDate = null;
            valueTimestamp = null;
            status = FactStatus.CONFLICTING;
            statusReason = FactStatusReason.ASSERTED_VALUE_CONFLICT;
            updatedAt = now;
        }
    }

    private static boolean isCalendarQuarter(LocalDate start, LocalDate end) {
        int month = start.getMonthValue();
        return start.getDayOfMonth() == 1
                && (month == 1 || month == 4 || month == 7 || month == 10)
                && end.equals(start.plusMonths(3).minusDays(1));
    }

    private static boolean isEarningsPredicate(FactPredicate predicate) {
        return predicate == FactPredicate.REVENUE
                || predicate == FactPredicate.OPERATING_INCOME;
    }

    private static boolean isCurrencyCode(String value) {
        return value != null && value.matches("[A-Z]{3}");
    }

    public UUID getId() { return id; }
    public MarketEntity getSubjectEntity() { return subjectEntity; }
    public Event getEvent() { return event; }
    public FactPredicate getPredicate() { return predicate; }
    public FactStatus getStatus() { return status; }
    public FactValueType getValueType() { return valueType; }
    public BigDecimal getValueNumber() { return valueNumber; }
    public String getValueText() { return valueText; }
    public Boolean getValueBoolean() { return valueBoolean; }
    public LocalDate getValueDate() { return valueDate; }
    public OffsetDateTime getValueTimestamp() { return valueTimestamp; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public OffsetDateTime getAsOfAt() { return asOfAt; }
    public byte[] getDedupKey() { return dedupKey == null ? null : dedupKey.clone(); }
    public FactStatusReason getStatusReason() { return statusReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
