package io.github.mojunsul.blog.domain.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.AdminFixture;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
class AdminPostIntegrationTest extends IntegrationTestSupport {

	private static final String CSRF = "csrf-token";
	private static final String URL = "/api/v1/admin/posts";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	PostRepository postRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		adminRepository.save(AdminFixture.admin("admin", passwordEncoder.encode("password-1234")));
	}

	@AfterEach
	void tearDown() {
		postRepository.deleteAll();
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
	void 인증_없이_목록을_조회하면_401이다() throws Exception {
		// when & then
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	void 인증_없이_단건을_조회하면_401이다() throws Exception {
		// when & then
		mockMvc.perform(get(URL + "/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void 인증하면_DRAFT를_포함해_목록을_조회한다() throws Exception {
		// given
		postRepository.save(PostFixture.draft("draft"));
		String accessToken = accessToken();

		// when & then
		mockMvc.perform(get(URL).cookie(new Cookie("access_token", accessToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].slug").value("draft"))
			.andExpect(jsonPath("$.content[0].status").value("DRAFT"));
	}

	@Test
	void 인증하면_DRAFT_단건을_조회한다() throws Exception {
		// given
		Long id = postRepository.save(PostFixture.draft("draft")).getId();
		String accessToken = accessToken();

		// when & then
		mockMvc.perform(get(URL + "/" + id).cookie(new Cookie("access_token", accessToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("draft"))
			.andExpect(jsonPath("$.status").value("DRAFT"));
	}
}
