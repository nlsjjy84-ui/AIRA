package com.aira.api.analysis.repository;

import com.aira.api.analysis.domain.Impact;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpactRepository extends JpaRepository<Impact, UUID> {}
