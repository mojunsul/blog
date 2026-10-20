package io.github.mojunsul.blog.domain.auth.scheduler;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleaner {

	private final RefreshTokenRepository refreshTokenRepository;
	private final Clock clock;

	// 서버 시간대에 기대지 않도록 zone을 명시한다
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	@Transactional
	public void deleteExpired() {
		refreshTokenRepository.deleteByExpiresAtLessThanEqual(clock.instant());
	}
}
