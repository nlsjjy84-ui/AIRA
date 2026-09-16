package com.aira.api.personalfinance.repository;

import com.aira.api.personalfinance.domain.PersonalFinanceConnection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalFinanceConnectionRepository extends JpaRepository<PersonalFinanceConnection, UUID> {
    List<PersonalFinanceConnection> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);
}
