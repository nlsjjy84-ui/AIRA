package com.aira.api.market.repository;

import com.aira.api.market.domain.FactAssertion;
import com.aira.api.market.domain.FactAssertionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactAssertionRepository
        extends JpaRepository<FactAssertion, FactAssertionId> {}
