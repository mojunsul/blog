package io.github.mojunsul.blog.domain.post.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.mojunsul.blog.domain.post.dto.PostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.PostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.PostSummaryResponse;
import io.github.mojunsul.blog.domain.post.service.PostService;
import io.github.mojunsul.blog.global.common.PageResponse;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.exception.GlobalExceptionHandler;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PostService postService;

	@Test
	void 글_목록을_페이지_형식으로_응답한다() throws Exception {
		// given
		var post = new PostSummaryResponse(1L, "제목", "slug", null, null, null, List.of(),
			Instant.parse("2026-10-11T01:00:00Z"));
		given(postService.getPublishedPosts(any()))
			.willReturn(new PageResponse<>(List.of(post), 0, 10, 1, 1, false));

		// when & then
		mockMvc.perform(get("/api/v1/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].slug").value("slug"))
			.andExpect(jsonPath("$.content[0].category").value(nullValue()))
			.andExpect(jsonPath("$.content[0].tags").isEmpty())
			.andExpect(jsonPath("$.content[0].publishedAt").value("2026-10-11T01:00:00Z"))
			.andExpect(jsonPath("$.content[0].content").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@ParameterizedTest
	@CsvSource({"page,-1", "page,10000", "size,0", "size,51"})
	void 범위를_벗어난_page와_size는_400으로_응답한다(String field, String value) throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/posts").param(field, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value(field));
	}

	@Test
	void page_상한값은_200으로_응답한다() throws Exception {
		// given
		given(postService.getPublishedPosts(any())).willReturn(new PageResponse<>(List.of(), 9999, 10, 0, 0, false));

		// when & then
		mockMvc.perform(get("/api/v1/posts").param("page", "9999"))
			.andExpect(status().isOk());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "null", value = {"page=|null|null", "page=%20|null|null",
		"size=%20|null|null", "page=01|1|null", "page=0x10|16|null", "page=1&page=abc|1|null",
		"size=1&size=2|null|1"})
	void 숫자로_바꿀_수_있는_쿼리는_바뀐_값으로_Service에_전달한다(String query, Integer page, Integer size) throws Exception {
		// given
		given(postService.getPublishedPosts(any())).willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, false));

		// when
		mockMvc.perform(get(URI.create("/api/v1/posts?" + query)))
			.andExpect(status().isOk());

		// then
		then(postService).should().getPublishedPosts(new PostSearchRequest(page, size));
	}

	@ParameterizedTest
	@ValueSource(strings = {"page=0x2710", "page=abc&page=1"})
	void 바뀐_값이_범위를_벗어나거나_첫_값이_숫자가_아니면_400으로_응답한다(String query) throws Exception {
		// when & then
		mockMvc.perform(get(URI.create("/api/v1/posts?" + query)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("page"));
	}

	@Test
	void 글_상세를_본문과_수정_시각을_포함해_응답한다() throws Exception {
		// given
		var post = new PostDetailResponse(1L, "제목", "my-post", null, "# 본문", null, null, List.of(),
			Instant.parse("2026-10-11T01:00:00Z"), Instant.parse("2026-10-11T03:30:00Z"));
		given(postService.getPublishedPost("my-post")).willReturn(post);

		// when & then
		mockMvc.perform(get("/api/v1/posts/my-post"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("my-post"))
			.andExpect(jsonPath("$.content").value("# 본문"))
			.andExpect(jsonPath("$.category").value(nullValue()))
			.andExpect(jsonPath("$.tags").isEmpty())
			.andExpect(jsonPath("$.publishedAt").value("2026-10-11T01:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-10-11T03:30:00Z"));
	}

	@Test
	void 없는_글은_404_ProblemDetail로_응답한다() throws Exception {
		// given
		given(postService.getPublishedPost("missing")).willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

		// when & then
		mockMvc.perform(get("/api/v1/posts/missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
	}
}
