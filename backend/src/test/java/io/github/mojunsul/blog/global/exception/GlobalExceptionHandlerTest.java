package io.github.mojunsul.blog.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@RestController
	static class TestController {

		record Body(@NotBlank(message = "제목을 입력하세요") String title) {
		}

		@GetMapping("/test/business")
		void business() {
			throw new BusinessException(ErrorCode.POST_NOT_FOUND);
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("secret internal detail");
		}

		@PostMapping("/test/validate")
		void validate(
			@Valid
			@RequestBody
			Body body) {}
	}

	@Test
	void 비즈니스_예외는_ErrorCode의_상태와_코드로_응답한다() throws Exception {
		// when & then
		mockMvc.perform(get("/test/business"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.instance").value("/test/business"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void 검증에_실패하면_400과_필드별_오류를_응답한다() throws Exception {
		// when & then
		mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("title"))
			.andExpect(jsonPath("$.errors[0].reason").value("제목을 입력하세요"));
	}

	@Test
	void JSON_형식이_잘못되면_400으로_응답한다() throws Exception {
		// when & then
		mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void 예상하지_못한_예외는_500으로_응답하고_내부_정보를_숨긴다() throws Exception {
		// when & then
		mockMvc.perform(get("/test/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(content().string(not(containsString("secret"))));
	}
}
