package com.aira.api.delivery.repository;

import com.aira.api.delivery.domain.Alert;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {}
