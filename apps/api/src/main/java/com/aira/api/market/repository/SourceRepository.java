package com.aira.api.market.repository;

import com.aira.api.market.domain.Source;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {}
