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
import org.springframework.test.web.servlet.ResultActions;

import io.github.mojunsul.blog.domain.post.dto.AdminPostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSummaryResponse;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.post.service.PostService;
import io.github.mojunsul.blog.global.common.PageResponse;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.exception.GlobalExceptionHandler;

@WebMvcTest(AdminPostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminPostControllerTest {

	private static final String URL = "/api/v1/admin/posts";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PostService postService;

	@Test
	void 글_목록을_상태와_시각을_포함해_페이지_형식으로_응답한다() throws Exception {
		// given
		AdminPostSummaryResponse post = new AdminPostSummaryResponse(13L, "작성 중인 글", "draft-post", PostStatus.DRAFT,
			null, null, Instant.parse("2026-10-12T09:00:00Z"), Instant.parse("2026-10-12T09:30:00Z"));
		given(postService.getAdminPosts(any())).willReturn(new PageResponse<>(List.of(post), 0, 10, 1, 1, false));

		// when & then
		mockMvc.perform(get(URL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].status").value("DRAFT"))
			.andExpect(jsonPath("$.content[0].category").value(nullValue()))
			.andExpect(jsonPath("$.content[0].publishedAt").value(nullValue()))
			.andExpect(jsonPath("$.content[0].createdAt").value("2026-10-12T09:00:00Z"))
			.andExpect(jsonPath("$.content[0].updatedAt").value("2026-10-12T09:30:00Z"))
			.andExpect(jsonPath("$.content[0].summary").doesNotExist())
			.andExpect(jsonPath("$.content[0].tags").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@ParameterizedTest
	@CsvSource({"page,-1", "page,10000", "size,0", "size,51"})
	void 범위를_벗어난_page와_size는_400으로_응답한다(String field, String value) throws Exception {
		// when & then
		mockMvc.perform(get(URL).param(field, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value(field));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "null", value = {"|null", "status=|null", "status=%20|null",
		"status=DRAFT|DRAFT", "status=PUBLISHED|PUBLISHED", "status=%20DRAFT|DRAFT", "status=DRAFT%20|DRAFT",
		"status=DRAFT&status=abc|DRAFT", "status=&status=DRAFT|null"})
	void status는_공백을_지우고_바꾼_값으로_Service에_전달한다(String query, PostStatus expected) throws Exception {
		// given
		given(postService.getAdminPosts(any())).willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, false));

		// when
		ResultActions result = mockMvc.perform(get(URI.create(URL + "?" + query)));

		// then
		result.andExpect(status().isOk());
		then(postService).should().getAdminPosts(new AdminPostSearchRequest(null, null, expected));
	}

	@ParameterizedTest
	@ValueSource(strings = {"status=draft", "status=ARCHIVED", "status=null", "status=abc&status=DRAFT",
		"status=%20&status=DRAFT"})
	void 상태가_아니거나_첫_값이_상태가_아니면_400으로_응답한다(String query) throws Exception {
		// when & then
		mockMvc.perform(get(URI.create(URL + "?" + query)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("status"))
			.andExpect(jsonPath("$.errors[0].reason").value("형식이 올바르지 않습니다"));
	}

	@Test
	void 글_단건을_수정용_필드와_함께_응답한다() throws Exception {
		// given
		AdminPostDetailResponse post = new AdminPostDetailResponse(12L, "제목", "my-post", null, "# 본문", null,
			PostStatus.DRAFT, null, null, List.of(), null, Instant.parse("2026-10-10T12:00:00Z"),
			Instant.parse("2026-10-11T03:30:00Z"));
		given(postService.getAdminPost(12L)).willReturn(post);

		// when & then
		mockMvc.perform(get(URL + "/12"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.categoryId").value(nullValue()))
			.andExpect(jsonPath("$.category").value(nullValue()))
			.andExpect(jsonPath("$.tags").isEmpty())
			.andExpect(jsonPath("$.publishedAt").value(nullValue()))
			.andExpect(jsonPath("$.createdAt").value("2026-10-10T12:00:00Z"))
			.andExpect(jsonPath("$.viewCount").doesNotExist());
	}

	@Test
	void 없는_글은_404로_응답한다() throws Exception {
		// given
		given(postService.getAdminPost(0L)).willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

		// when & then
		mockMvc.perform(get(URL + "/0"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "99999999999999999999"})
	void id가_Long이_아니면_400으로_응답한다(String id) throws Exception {
		// when & then
		mockMvc.perform(get(URL + "/" + id))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}
}
