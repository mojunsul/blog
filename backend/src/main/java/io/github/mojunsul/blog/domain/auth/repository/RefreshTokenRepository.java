package io.github.mojunsul.blog.domain.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.domain.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	void deleteByTokenHash(String tokenHash);

	void deleteByAdmin(Admin admin);

	void deleteByExpiresAtLessThanEqual(Instant now);

	// 조회 후 갱신으로 나누면 동시 요청이 모두 성공할 수 있어 한 문장으로 하나만 성공하게 한다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update RefreshToken t set t.usedAt = :now
		where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt > :now
		""")
	int markUsed(
		@Param("tokenHash")
		String tokenHash,
		@Param("now")
		Instant now);
}
