package io.github.mojunsul.blog.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.mojunsul.blog.domain.auth.dto.LoginResult;
import io.github.mojunsul.blog.domain.auth.service.AuthService;
import io.github.mojunsul.blog.global.config.AuthProperties;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.exception.GlobalExceptionHandler;
import io.github.mojunsul.blog.global.security.CookieUtil;
import io.github.mojunsul.blog.support.ControllerTestSupport;
import jakarta.servlet.http.Cookie;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, CookieUtil.class})
@EnableConfigurationProperties(AuthProperties.class)
class AuthControllerTest extends ControllerTestSupport {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthService authService;

	private ResultActions login(String body) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON);
		if (body != null) {
			request.content(body);
		}
		return mockMvc.perform(request);
	}

	@Test
	void 로그인하면_본문과_두_쿠키를_응답한다() throws Exception {
		// given
		given(authService.login("admin", "password")).willReturn(new LoginResult("admin", "access", "refresh"));

		// when & then
		login("{\"username\":\"admin\",\"password\":\"password\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("access_token=access"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("refresh_token=refresh"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/api/v1/auth"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Secure"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("HttpOnly"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("SameSite=Lax"))));
	}

	@Test
	void 인증에_실패하면_401_ProblemDetail로_응답한다() throws Exception {
		// given
		given(authService.login("admin", "wrong")).willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		// when & then
		login("{\"username\":\"admin\",\"password\":\"wrong\"}")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"password\":\"pw\"}", "{\"username\":\"\",\"password\":\"pw\"}",
		"{\"username\":\"   \",\"password\":\"pw\"}", "{\"username\":null,\"password\":\"pw\"}"})
	void username이_없거나_비었으면_400이다(String body) throws Exception {
		// when & then
		login(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("username"));
	}

	@Test
	void username이_51자면_400이다() throws Exception {
		// when & then
		login("{\"username\":\"" + "a".repeat(51) + "\",\"password\":\"pw\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("username"));
	}

	@Test
	void username이_50자면_Service로_전달한다() throws Exception {
		// given
		String username = "a".repeat(50);
		given(authService.login(username, "pw")).willReturn(new LoginResult(username, "access", "refresh"));

		// when & then
		login("{\"username\":\"" + username + "\",\"password\":\"pw\"}").andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"username\":\"admin\"}", "{\"username\":\"admin\",\"password\":\"\"}",
		"{\"username\":\"admin\",\"password\":null}"})
	void password가_없거나_비었으면_400이다(String body) throws Exception {
		// when & then
		login(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("password"));
	}

	@Test
	void 공백만_있는_password는_Service까지_전달한다() throws Exception {
		// given
		given(authService.login("admin", "   ")).willReturn(new LoginResult("admin", "access", "refresh"));

		// when & then
		login("{\"username\":\"admin\",\"password\":\"   \"}").andExpect(status().isOk());
		then(authService).should().login("admin", "   ");
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "not json", "[]"})
	void 잘못된_JSON이면_400이다(String body) throws Exception {
		// when & then
		login(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void 본문이_없으면_400이다() throws Exception {
		// when & then
		login(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 같은_키가_여러_개면_마지막_값을_쓴다() throws Exception {
		// given
		given(authService.login("b", "pw")).willReturn(new LoginResult("b", "access", "refresh"));

		// when & then
		login("{\"username\":\"a\",\"username\":\"b\",\"password\":\"pw\"}").andExpect(status().isOk());
		then(authService).should().login("b", "pw");
	}

	@Test
	void 숫자_username은_문자열로_Service에_전달한다() throws Exception {
		// given
		given(authService.login("1", "pw")).willReturn(new LoginResult("1", "access", "refresh"));

		// when & then
		login("{\"username\":1,\"password\":\"pw\"}").andExpect(status().isOk());
		then(authService).should().login("1", "pw");
	}

	@Test
	void 재발급하면_204와_새_두_쿠키를_응답한다() throws Exception {
		// given
		given(authService.refresh("old")).willReturn(new LoginResult("admin", "access", "new-refresh"));

		// when & then
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "old")))
			.andExpect(status().isNoContent())
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("access_token=access"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/api;"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("refresh_token=new-refresh"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/api/v1/auth"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Max-Age=1209600"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("HttpOnly"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("SameSite=Lax"))));
	}

	@Test
	void 재발급에_실패하면_401_ProblemDetail로_응답한다() throws Exception {
		// given
		given(authService.refresh("bad")).willThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		// when & then
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "bad")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void 재발급_쿠키가_없으면_null로_Service에_전달한다() throws Exception {
		// given
		given(authService.refresh(null)).willThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		// when & then
		mockMvc.perform(post("/api/v1/auth/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void 재발급_쿠키가_둘이면_첫_값을_Service에_전달한다() throws Exception {
		// given
		given(authService.refresh("first")).willReturn(new LoginResult("admin", "access", "new-refresh"));

		// when & then
		mockMvc.perform(post("/api/v1/auth/refresh")
			.cookie(new Cookie("refresh_token", "first"), new Cookie("refresh_token", "second")))
			.andExpect(status().isNoContent());
		then(authService).should().refresh("first");
	}

	@Test
	void 로그아웃하면_204와_만료된_두_쿠키를_응답한다() throws Exception {
		// when & then
		mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", "token")))
			.andExpect(status().isNoContent())
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("access_token=;"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/api;"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("refresh_token=;"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Path=/api/v1/auth;"))))
			.andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Max-Age=0"))));
		then(authService).should().logout("token");
	}

	@Test
	void 로그아웃_쿠키가_둘이면_첫_값을_Service에_전달한다() throws Exception {
		// when & then
		mockMvc.perform(post("/api/v1/auth/logout")
			.cookie(new Cookie("refresh_token", "first"), new Cookie("refresh_token", "second")))
			.andExpect(status().isNoContent());
		then(authService).should().logout("first");
	}
}
