package com.aira.api.user.repository;

import com.aira.api.user.domain.AppUser;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    boolean existsByNicknameNormalized(String nicknameNormalized);
    Optional<AppUser> findByNicknameNormalized(String nicknameNormalized);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appUser from AppUser appUser where appUser.id = :id")
    Optional<AppUser> findByIdForInterest(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select appUser from AppUser appUser where appUser.id = :id")
    Optional<AppUser> findByIdForSession(@Param("id") UUID id);
}
