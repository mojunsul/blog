package io.github.mojunsul.blog.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@RestController
	static class TestController {

		record Query(Integer page) {
		}

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

		@GetMapping("/test/query")
		void query(
			@Valid
			Query query) {}

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
	void 쿼리_파라미터_타입_변환에_실패하면_내부_메시지_대신_일반_메시지를_응답한다() throws Exception {
		// when & then
		mockMvc.perform(get("/test/query").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("page"))
			.andExpect(jsonPath("$.errors[0].reason").value("형식이 올바르지 않습니다"))
			.andExpect(content().string(not(containsString("java.lang"))));
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

	@Test
	void 비즈니스_예외는_코드와_경로를_WARN_한_줄로_남기고_스택_트레이스는_남기지_않는다(CapturedOutput output) throws Exception {
		// when
		mockMvc.perform(get("/test/business"));

		// then
		assertThat(output.getAll()).contains("WARN").contains("code=POST_NOT_FOUND, path=/test/business")
			.doesNotContain("BusinessException:").doesNotContain("\tat ");
	}

	@Test
	void 검증에_실패하면_로그를_남기지_않는다(CapturedOutput output) throws Exception {
		// when
		mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"));

		// then
		assertThat(output.getAll()).doesNotContain("/test/validate").doesNotContain("INVALID_INPUT");
	}
}
