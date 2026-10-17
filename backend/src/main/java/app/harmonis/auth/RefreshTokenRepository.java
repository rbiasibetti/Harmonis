package app.harmonis.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Marca como usado de forma atomica; retorna 0 se outra requisicao ja o consumiu. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.revokedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :family and t.revokedAt is null")
    int revokeFamily(@Param("family") String family, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :user and t.revokedAt is null")
    int revokeAllOfUser(@Param("user") Long user, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken t where t.familyExpiresAt < :now or t.expiresAt < :cutoff")
    int deleteExpired(@Param("now") Instant now, @Param("cutoff") Instant cutoff);
}
