package io.github.mojunsul.blog.global.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.mojunsul.blog.global.config.AuthProperties;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

	private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

	private static final String SECRET_KEY_NAME = "blog.auth.jwt-secret";
	private static final int MIN_KEY_BYTES = 32;
	static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

	private final Clock clock;
	private final SecretKey key;
	private final JwtParser parser;

	public JwtProvider(AuthProperties properties, Clock clock) {
		this.clock = clock;
		this.key = Keys.hmacShaKeyFor(resolveKeyBytes(properties));
		this.parser = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build();
	}

	public String createAccessToken(String username) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(username)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
			.signWith(key, Jwts.SIG.HS256)
			.compact();
	}

	public String parseUsername(String token) {
		return parser.parseSignedClaims(token).getPayload().getSubject();
	}

	private static byte[] resolveKeyBytes(AuthProperties properties) {
		String secret = properties.jwtSecret();
		if (secret == null || secret.isBlank()) {
			if (properties.jwtSecretRequired()) {
				throw new IllegalStateException(SECRET_KEY_NAME + " 설정이 필요합니다");
			}
			log.warn("{}이 비어 있어 무작위 키를 사용합니다. 재시작하면 기존 토큰이 무효가 됩니다", SECRET_KEY_NAME);
			byte[] random = new byte[MIN_KEY_BYTES];
			new SecureRandom().nextBytes(random);
			return random;
		}

		byte[] decoded;
		try {
			decoded = Base64.getDecoder().decode(secret.strip());
		} catch (IllegalArgumentException e) {
			// 원인 예외 메시지에 키 조각이 들어갈 수 있어 연결하지 않는다
			throw new IllegalStateException(SECRET_KEY_NAME + "은 Base64 형식이어야 합니다");
		}
		if (decoded.length < MIN_KEY_BYTES) {
			throw new IllegalStateException(SECRET_KEY_NAME + "은 디코딩 결과가 " + MIN_KEY_BYTES + "바이트 이상이어야 합니다");
		}
		return decoded;
	}
}
