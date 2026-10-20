package io.github.mojunsul.blog.domain.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import io.github.mojunsul.blog.domain.auth.repository.AdminRepository;
import io.github.mojunsul.blog.domain.auth.repository.RefreshTokenRepository;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.fixture.AdminFixture;
import io.github.mojunsul.blog.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
class AdminCategoryIntegrationTest extends IntegrationTestSupport {

	private static final String CSRF = "csrf-token";
	private static final String URL = "/api/v1/admin/categories";
	private static final String BODY = "{\"name\":\"Backend\",\"slug\":\"backend\"}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		adminRepository.save(AdminFixture.admin("admin", passwordEncoder.encode("password-1234")));
	}

	@AfterEach
	void tearDown() {
		categoryRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		adminRepository.deleteAll();
	}

	private String accessToken() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.content("{\"username\":\"admin\",\"password\":\"password-1234\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("access_token")
			.getValue();
	}

	@Test
	void 인증_없이_생성하면_401이다() throws Exception {
		// when & then
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.content(BODY)).andExpect(status().isUnauthorized());
	}

	@Test
	void CSRF_토큰_없이_생성하면_403이다() throws Exception {
		// given
		String accessToken = accessToken();

		// when & then
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.cookie(new Cookie("access_token", accessToken))
			.content(BODY)).andExpect(status().isForbidden());
	}

	@Test
	void 인증과_CSRF가_있으면_생성은_201이다() throws Exception {
		// given
		String accessToken = accessToken();

		// when & then
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.cookie(new Cookie("access_token", accessToken), new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.content(BODY)).andExpect(status().isCreated());
	}
}
