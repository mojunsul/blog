package io.github.mojunsul.blog.domain.post.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.mojunsul.blog.domain.post.dto.PostSummaryResponse;
import io.github.mojunsul.blog.domain.post.service.PostService;
import io.github.mojunsul.blog.global.common.PageResponse;
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
	@CsvSource({"page,-1", "size,0", "size,51"})
	void 범위를_벗어난_page와_size는_400으로_응답한다(String field, String value) throws Exception {
		// when & then
		mockMvc.perform(get("/api/v1/posts").param(field, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value(field));
	}
}
