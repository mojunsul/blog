package io.github.mojunsul.blog.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.mojunsul.blog.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
class SecurityConfigTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 공개_경로는_인증_없이_조회된다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void 인증_없이_관리자_경로를_부르면_401_ProblemDetail이다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/admin/posts"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value("/api/v1/admin/posts"))
			.andExpect(jsonPath("$.errors").isEmpty())
			.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void CSRF_토큰_없이_상태_변경_요청을_보내면_403이다() throws Exception {
		// when & then
		mockMvc.perform(post("/api/v1/admin/posts"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
	}

	@Test
	void CSRF_쿠키와_헤더_값이_다르면_403이다() throws Exception {
		// when & then
		mockMvc.perform(post("/api/v1/admin/posts")
			.cookie(new Cookie("XSRF-TOKEN", "a"))
			.header("X-XSRF-TOKEN", "b"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
	}

	@Test
	void CSRF_토큰이_맞고_인증이_없으면_401이다() throws Exception {
		// when & then
		mockMvc.perform(post("/api/v1/admin/posts")
			.cookie(new Cookie("XSRF-TOKEN", "a"))
			.header("X-XSRF-TOKEN", "a"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@WithMockUser(roles = "USER")
	void 권한이_없는_사용자가_관리자_경로를_부르면_403이다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/admin/posts"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 방화벽이_거절한_요청은_400_ProblemDetail이다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/posts/a;b"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors").isEmpty())
			.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void error_경로는_인증_없이_원래_상태를_돌려준다() throws Exception {
		// when & then
		mockMvc.perform(get("/error").requestAttr("jakarta.servlet.error.status_code", 500))
			.andExpect(status().isInternalServerError());
	}

	@Test
	void csrf_엔드포인트는_HttpOnly_아닌_Lax_쿠키를_발급한다() throws Exception {
		// when
		ResultActions result = mockMvc.perform(get("/api/v1/auth/csrf"));

		// then
		Cookie cookie = result.andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getSecure()).isFalse();
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
	}

	@Test
	void csrf_쿠키가_이미_있으면_다시_발급하지_않는다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/auth/csrf").cookie(new Cookie("XSRF-TOKEN", "a")))
			.andExpect(status().isNoContent())
			.andExpect(header().doesNotExist("Set-Cookie"));
	}

	@Test
	void 공개_조회도_쿠키가_없으면_csrf_쿠키를_미리_발급한다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/posts"))
			.andExpect(status().isOk())
			.andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")));
	}

	@Test
	void HTTPS_요청이면_csrf_쿠키에_Secure가_붙는다() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/auth/csrf").secure(true))
			.andExpect(header().string("Set-Cookie", containsString("Secure")));
	}
}
