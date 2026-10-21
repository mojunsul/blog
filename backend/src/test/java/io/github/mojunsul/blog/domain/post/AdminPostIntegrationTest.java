package io.github.mojunsul.blog.domain.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.mojunsul.blog.domain.auth.repository.AdminRepository;
import io.github.mojunsul.blog.domain.auth.repository.RefreshTokenRepository;
import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.AdminFixture;
import io.github.mojunsul.blog.fixture.CategoryFixture;
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
	CategoryRepository categoryRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		adminRepository.save(AdminFixture.admin("admin", passwordEncoder.encode("password-1234")));
	}

	@AfterEach
	void tearDown() {
		postRepository.deleteAll();
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

	private static String body(String slug, String status, Long categoryId) {
		return "{\"title\":\"제목\",\"slug\":\"" + slug + "\",\"content\":\"본문\",\"status\":\"" + status
			+ "\",\"categoryId\":" + categoryId + "}";
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String accessToken, String body)
		throws Exception {
		request.cookie(new Cookie("access_token", accessToken), new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF);
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private long createdId(ResultActions result) throws Exception {
		String location = result.andReturn().getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	@Test
	void 인증_없이_작성_수정_삭제하면_401이다() throws Exception {
		// when & then
		MockHttpServletRequestBuilder[] requests = {
			post(URL).content(body("a", "DRAFT", null)),
			put(URL + "/1").content(body("a", "DRAFT", null)),
			delete(URL + "/1")};
		for (MockHttpServletRequestBuilder request : requests) {
			mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
				.cookie(new Cookie("XSRF-TOKEN", CSRF))
				.header("X-XSRF-TOKEN", CSRF)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void CSRF_토큰_없이_작성_수정_삭제하면_403이다() throws Exception {
		// given
		Cookie access = new Cookie("access_token", accessToken());

		// when & then
		MockHttpServletRequestBuilder[] requests = {
			post(URL).content(body("a", "DRAFT", null)),
			put(URL + "/1").content(body("a", "DRAFT", null)),
			delete(URL + "/1")};
		for (MockHttpServletRequestBuilder request : requests) {
			mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).cookie(access))
				.andExpect(status().isForbidden());
		}
	}

	@Test
	void 작성_단건_조회_수정_삭제_흐름() throws Exception {
		// given
		String token = accessToken();
		Category category = categoryRepository.save(CategoryFixture.category("backend"));

		// when & then
		ResultActions created = send(post(URL), token, body("flow", "PUBLISHED", category.getId()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("flow"));
		long id = createdId(created);
		created.andExpect(header().string("Location", URL + "/" + id));

		mockMvc.perform(get(URL + "/" + id).cookie(new Cookie("access_token", token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.categoryId").value(category.getId()))
			.andExpect(jsonPath("$.publishedAt").isNotEmpty());

		send(put(URL + "/" + id), token, body("flow-2", "DRAFT", null)).andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("flow-2"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.category").doesNotExist())
			.andExpect(jsonPath("$.publishedAt").isNotEmpty());

		send(delete(URL + "/" + id), token, null).andExpect(status().isNoContent());
		send(delete(URL + "/" + id), token, null).andExpect(status().isNotFound());
		mockMvc.perform(get(URL + "/" + id).cookie(new Cookie("access_token", token)))
			.andExpect(status().isNotFound());
	}

	@Test
	void 작성할_때_slug가_중복이면_409다() throws Exception {
		// given
		postRepository.save(PostFixture.draft("dup"));

		// when & then
		send(post(URL), accessToken(), body("dup", "DRAFT", null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
	}

	@Test
	void 수정할_때_다른_글의_slug면_409이고_자기_slug는_통과한다() throws Exception {
		// given
		postRepository.save(PostFixture.draft("other"));
		Long id = postRepository.save(PostFixture.draft("mine")).getId();
		String token = accessToken();

		// when & then
		send(put(URL + "/" + id), token, body("other", "DRAFT", null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
		send(put(URL + "/" + id), token, body("mine", "PUBLISHED", null)).andExpect(status().isOk());
	}

	@Test
	void 없는_카테고리로_작성_수정하면_404다() throws Exception {
		// given
		Long id = postRepository.save(PostFixture.draft("mine")).getId();
		String token = accessToken();

		// when & then
		send(post(URL), token, body("new", "DRAFT", 999999L)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
		send(put(URL + "/" + id), token, body("mine", "DRAFT", 999999L)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}

	@Test
	void 없는_글을_수정_삭제하면_404다() throws Exception {
		// given
		String token = accessToken();

		// when & then
		send(put(URL + "/999999"), token, body("a", "DRAFT", null)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
		send(delete(URL + "/999999"), token, null).andExpect(status().isNotFound());
	}
}
