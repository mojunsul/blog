package io.github.mojunsul.blog.domain.category.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.mojunsul.blog.domain.category.dto.AdminCategoryResponse;
import io.github.mojunsul.blog.domain.category.service.CategoryService;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.exception.GlobalExceptionHandler;
import io.github.mojunsul.blog.support.ControllerTestSupport;

@WebMvcTest(AdminCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminCategoryControllerTest extends ControllerTestSupport {

	private static final String URL = "/api/v1/admin/categories";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	CategoryService categoryService;

	private ResultActions create(String body) throws Exception {
		MockHttpServletRequestBuilder request = post(URL).contentType(MediaType.APPLICATION_JSON);
		if (body != null) {
			request.content(body);
		}
		return mockMvc.perform(request);
	}

	private static String body(String name, String slug) {
		return "{\"name\":" + name + ",\"slug\":" + slug + "}";
	}

	private static String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	@Test
	void 생성하면_201과_Location과_본문을_응답한다() throws Exception {
		// given
		given(categoryService.create("Backend", "backend"))
			.willReturn(new AdminCategoryResponse(1L, "Backend", "backend"));

		// when & then
		create("{\"name\":\"Backend\",\"slug\":\"backend\"}")
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/v1/admin/categories/1"))
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.name").value("Backend"))
			.andExpect(jsonPath("$.slug").value("backend"));
	}

	@Test
	void 수정하면_200과_본문을_응답한다() throws Exception {
		// given
		given(categoryService.update(1L, "Infra", "infra")).willReturn(new AdminCategoryResponse(1L, "Infra", "infra"));

		// when & then
		mockMvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Infra\",\"slug\":\"infra\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.name").value("Infra"))
			.andExpect(jsonPath("$.slug").value("infra"));
	}

	@Test
	void 삭제하면_204다() throws Exception {
		// when & then
		mockMvc.perform(delete(URL + "/1")).andExpect(status().isNoContent());
		then(categoryService).should().delete(1L);
	}

	@Test
	void 서비스가_404를_던지면_ProblemDetail로_응답한다() throws Exception {
		// given
		given(categoryService.update(anyLong(), anyString(), anyString()))
			.willThrow(new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

		// when & then
		mockMvc.perform(put(URL + "/9").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Infra\",\"slug\":\"infra\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}

	@Test
	void 서비스가_409를_던지면_ProblemDetail로_응답한다() throws Exception {
		// given
		given(categoryService.create(anyString(), anyString()))
			.willThrow(new BusinessException(ErrorCode.DUPLICATE_SLUG));

		// when & then
		create("{\"name\":\"Backend\",\"slug\":\"backend\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " ", "\\t", "\\u3000", "\\u3000\\t\\n", "a\\u0000b", "a\\u0007b", "a\\u007fb",
		"a\\u0085b",
		"01234567890123456789012345678901234567890123456789x"})
	void name이_잘못되면_400이다(String name) throws Exception {
		// when & then
		create(body(json(name), "\"backend\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		then(categoryService).should(never()).create(any(), any());
	}

	@Test
	void name이_없으면_400이다() throws Exception {
		// when & then
		create("{\"slug\":\"backend\"}").andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " ", "Backend", " backend", "backend ", "back--end", "-backend", "backend-", "back_end",
		"한글"})
	void slug가_잘못되면_400이다(String slug) throws Exception {
		// when & then
		create(body("\"Backend\"", json(slug))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void slug가_101자면_400이고_100자면_통과한다() throws Exception {
		// given
		given(categoryService.create(anyString(), anyString())).willReturn(new AdminCategoryResponse(1L, "n", "s"));

		// when & then
		create(body("\"Backend\"", json("a".repeat(101)))).andExpect(status().isBadRequest());
		create(body("\"Backend\"", json("a".repeat(100)))).andExpect(status().isCreated());
	}

	@Test
	void slug가_없으면_400이다() throws Exception {
		// when & then
		create("{\"name\":\"Backend\"}").andExpect(status().isBadRequest());
	}

	@Test
	void name은_50자까지_통과하고_공백_제거_뒤_길이로_센다() throws Exception {
		// given
		given(categoryService.create(anyString(), anyString())).willReturn(new AdminCategoryResponse(1L, "n", "s"));
		String fifty = "a".repeat(50);

		// when & then
		create(body(json(fifty), "\"backend\"")).andExpect(status().isCreated());
		create(body(json("  " + fifty), "\"backend\"")).andExpect(status().isCreated());
		create(body(json(fifty + "a"), "\"backend\"")).andExpect(status().isBadRequest());
	}

	@Test
	void name의_앞뒤_공백은_제거해_서비스에_전달한다() throws Exception {
		// given
		given(categoryService.create(anyString(), anyString())).willReturn(new AdminCategoryResponse(1L, "n", "s"));
		ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);

		// when
		create(body("\" Back end\\t\"", "\"backend\"")).andExpect(status().isCreated());
		create(body("\"\\u3000Backend\"", "\"backend\"")).andExpect(status().isCreated());
		create(body("\"\\u00a0Backend\"", "\"backend\"")).andExpect(status().isCreated());

		// then
		then(categoryService).should(org.mockito.Mockito.times(3)).create(name.capture(), anyString());
		org.assertj.core.api.Assertions.assertThat(name.getAllValues())
			.containsExactly("Back end", "Backend", "\u00a0Backend");
	}

	@Test
	void 같은_키가_여럿이면_마지막_값을_쓴다() throws Exception {
		// given
		given(categoryService.create("B", "b")).willReturn(new AdminCategoryResponse(1L, "B", "b"));

		// when & then
		create("{\"name\":\"A\",\"name\":\"B\",\"slug\":\"a\",\"slug\":\"b\"}").andExpect(status().isCreated());
	}

	@Test
	void 숫자와_불리언은_문자열로_바뀌어_통과한다() throws Exception {
		// given
		given(categoryService.create("true", "1")).willReturn(new AdminCategoryResponse(1L, "true", "1"));

		// when & then
		create("{\"name\":true,\"slug\":1}").andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"name\":[\"a\"],\"slug\":\"a\"}", "{\"name\":\"a\",\"slug\":{\"x\":1}}", "{broken",
		"[]"})
	void 배열_객체_필드나_깨진_JSON은_400이다(String body) throws Exception {
		// when & then
		create(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 본문이_없으면_400이다() throws Exception {
		// when & then
		create(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "99999999999999999999"})
	void 경로_id가_Long이_아니면_400이다(String id) throws Exception {
		// when & then
		mockMvc.perform(put(URL + "/" + id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"a\",\"slug\":\"a\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(delete(URL + "/" + id)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}
}
