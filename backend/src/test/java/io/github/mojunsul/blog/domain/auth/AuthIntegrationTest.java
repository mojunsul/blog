package io.github.mojunsul.blog.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.github.mojunsul.blog.domain.auth.entity.RefreshToken;
import io.github.mojunsul.blog.domain.auth.repository.AdminRepository;
import io.github.mojunsul.blog.domain.auth.repository.RefreshTokenRepository;
import io.github.mojunsul.blog.fixture.AdminFixture;
import io.github.mojunsul.blog.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestSupport {

	private static final String CSRF = "csrf-token";
	private static final String BODY = "{\"username\":\"admin\",\"password\":\"password-1234\"}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		adminRepository.save(AdminFixture.admin("admin", passwordEncoder.encode("password-1234")));
	}

	@AfterEach
	void tearDown() {
		refreshTokenRepository.deleteAll();
		adminRepository.deleteAll();
	}

	private MvcResult login() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.content(BODY)).andExpect(status().isOk()).andReturn();
	}

	@Test
	void CSRF_토큰_없는_로그인은_403이다() throws Exception {
		// when & then
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
	}

	@Test
	void 로그인_후_받은_access_token으로_me를_부르면_200이다() throws Exception {
		// given
		String accessToken = login().getResponse().getCookie("access_token").getValue();

		// when & then
		mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", accessToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"));
	}

	@Test
	void 쿠키가_없거나_비었거나_위조되면_me는_401이다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", "")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", "forged.token.value")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 로그인하면_DB에는_Refresh_토큰의_해시만_저장된다() throws Exception {
		// when
		String refreshToken = login().getResponse().getCookie("refresh_token").getValue();

		// then
		List<RefreshToken> saved = refreshTokenRepository.findAll();
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getTokenHash()).hasSize(64).isNotEqualTo(refreshToken);
	}

	@Test
	void 잘못된_access_token_쿠키가_있어도_공개_GET은_200이다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/posts").cookie(new Cookie("access_token", "forged.token.value")))
			.andExpect(status().isOk());
	}

	@Test
	void access_token_쿠키가_둘이면_첫_값만_쓴다() throws Exception {
		// given
		Cookie valid = new Cookie("access_token", login().getResponse().getCookie("access_token").getValue());
		Cookie invalid = new Cookie("access_token", "forged.token.value");

		// when & then
		mockMvc.perform(get("/api/v1/auth/me").cookie(valid, invalid)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/auth/me").cookie(invalid, valid)).andExpect(status().isUnauthorized());
	}
}
