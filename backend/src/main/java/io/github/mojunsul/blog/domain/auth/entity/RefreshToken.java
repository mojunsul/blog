package io.github.mojunsul.blog.domain.auth.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// refresh_token 테이블에 updated_at이 없어 BaseTimeEntity를 상속하지 않는다.
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "admin_id", nullable = false)
	private Admin admin;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	// 재발급으로 교체된 시각. Repository의 조건부 UPDATE로만 채운다
	private Instant usedAt;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Builder
	private RefreshToken(Admin admin, String tokenHash, Instant expiresAt) {
		this.admin = admin;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}
}
