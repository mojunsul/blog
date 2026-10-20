package io.github.mojunsul.blog.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.mojunsul.blog.global.config.AuthProperties;
import io.jsonwebtoken.JwtException;

class JwtProviderTest {

	private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
	private static final String SECRET = secret(32, 1);

	private static String secret(int length, int fill) {
		byte[] bytes = new byte[length];
		Arrays.fill(bytes, (byte)fill);
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static Clock clockAt(Instant instant) {
		return Clock.fixed(instant, ZoneOffset.UTC);
	}

	private static JwtProvider provider(String secret, boolean required, Instant now) {
		return new JwtProvider(new AuthProperties(secret, required, true), clockAt(now));
	}

	@Test
	void 발급한_토큰에서_username을_꺼낸다() {
		// given
		JwtProvider provider = provider(SECRET, true, NOW);

		// when
		String token = provider.createAccessToken("admin");

		// then
		assertThat(provider.parseUsername(token)).isEqualTo("admin");
	}

	@Test
	void 만료_시각이_지나면_검증에_실패한다() {
		// given
		String token = provider(SECRET, true, NOW).createAccessToken("admin");
		JwtProvider later = provider(SECRET, true, NOW.plus(Duration.ofMinutes(15)).plusSeconds(1));

		// when & then
		assertThatThrownBy(() -> later.parseUsername(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void 만료_직전이면_검증에_성공한다() {
		// given
		String token = provider(SECRET, true, NOW).createAccessToken("admin");
		JwtProvider almost = provider(SECRET, true, NOW.plus(Duration.ofMinutes(15)).minusSeconds(1));

		// when & then
		assertThat(almost.parseUsername(token)).isEqualTo("admin");
	}

	@Test
	void 다른_키로_서명한_토큰은_검증에_실패한다() {
		// given
		String token = provider(secret(32, 2), true, NOW).createAccessToken("admin");

		// when & then
		assertThatThrownBy(() -> provider(SECRET, true, NOW).parseUsername(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void 서명이_없는_토큰은_검증에_실패한다() {
		// given
		String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"admin\"}".getBytes());

		// when & then
		assertThatThrownBy(() -> provider(SECRET, true, NOW).parseUsername(header + "." + payload + "."))
			.isInstanceOf(JwtException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void 키가_비어_있으면_required가_true일_때_기동에_실패한다(String blank) {
		// when & then
		assertThatThrownBy(() -> provider(blank, true, NOW)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("blog.auth.jwt-secret");
	}

	@Test
	void 키가_null이면_required가_true일_때_기동에_실패한다() {
		// when & then
		assertThatThrownBy(() -> provider(null, true, NOW)).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void 키가_비어_있으면_required가_false일_때_무작위_키로_동작한다(String blank) {
		// given
		JwtProvider provider = provider(blank, false, NOW);

		// when
		String token = provider.createAccessToken("admin");

		// then
		assertThat(provider.parseUsername(token)).isEqualTo("admin");
	}

	@Test
	void 무작위_키는_기동마다_달라_다른_인스턴스의_토큰을_검증하지_못한다() {
		// given
		String token = provider("", false, NOW).createAccessToken("admin");

		// when & then
		assertThatThrownBy(() -> provider("", false, NOW).parseUsername(token)).isInstanceOf(JwtException.class);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void Base64가_아니면_기동에_실패한다(boolean required) {
		// when & then
		assertThatThrownBy(() -> provider("not base64!!", required, NOW)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("blog.auth.jwt-secret");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void 디코딩_결과가_32바이트_미만이면_기동에_실패한다(boolean required) {
		// when & then
		assertThatThrownBy(() -> provider(secret(31, 1), required, NOW)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("blog.auth.jwt-secret");
	}

	@Test
	void 앞뒤_공백과_줄바꿈이_붙은_키는_지우고_통과한다() {
		// when & then
		assertThatCode(() -> provider("  " + SECRET + "\n", true, NOW)).doesNotThrowAnyException();
	}

	@Test
	void 패딩_없는_43자_키는_통과한다() {
		// given
		String unpadded = SECRET.substring(0, 43);

		// when & then
		assertThat(unpadded).hasSize(43);
		assertThatCode(() -> provider(unpadded, true, NOW)).doesNotThrowAnyException();
	}

	@Test
	void URL_safe_문자가_든_키는_Base64가_아니다() {
		// given
		byte[] bytes = new byte[32];
		Arrays.fill(bytes, (byte)0xfb);
		String urlSafe = Base64.getUrlEncoder().encodeToString(bytes);

		// when & then
		assertThat(urlSafe).contains("-", "_");
		assertThatThrownBy(() -> provider(urlSafe, true, NOW)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 중간에_공백이_든_키는_Base64가_아니다() {
		// given
		String spaced = SECRET.substring(0, 10) + " " + SECRET.substring(10);

		// when & then
		assertThatThrownBy(() -> provider(spaced, true, NOW)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 오류_메시지에_키_값이_나오지_않는다() {
		// given
		String bad = secret(31, 7);

		// when & then
		assertThatThrownBy(() -> provider(bad, true, NOW)).hasMessageNotContaining(bad);
	}
}
