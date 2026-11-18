package io.github.mojunsul.blog.domain.tag.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.mojunsul.blog.domain.tag.dto.TagResponse;
import io.github.mojunsul.blog.domain.tag.service.TagService;

@WebMvcTest(TagController.class)
@AutoConfigureMockMvc(addFilters = false)
class TagControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	TagService tagService;

	@Test
	void 태그_목록을_배열로_응답한다() throws Exception {
		// given
		given(tagService.getTags()).willReturn(List.of(new TagResponse("spring", 5), new TagResponse("jwt", 2)));

		// when & then
		mockMvc.perform(get("/api/v1/tags"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("spring"))
			.andExpect(jsonPath("$[0].postCount").value(5))
			.andExpect(jsonPath("$[1].name").value("jwt"));
	}
}
