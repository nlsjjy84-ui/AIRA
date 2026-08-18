package com.aira.api.market.repository;

import com.aira.api.market.domain.SourceAuthorityScope;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceAuthorityScopeRepository extends JpaRepository<SourceAuthorityScope, UUID> {}
