package io.github.mojunsul.blog.domain.category.controller;

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

import io.github.mojunsul.blog.domain.category.dto.CategoryResponse;
import io.github.mojunsul.blog.domain.category.service.CategoryService;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	CategoryService categoryService;

	@Test
	void 카테고리_목록을_배열로_응답한다() throws Exception {
		// given
		given(categoryService.getCategories()).willReturn(List.of(
			new CategoryResponse(1L, "Backend", "backend", 8), new CategoryResponse(2L, "Infra", "infra", 0)));

		// when & then
		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(1))
			.andExpect(jsonPath("$[0].name").value("Backend"))
			.andExpect(jsonPath("$[0].slug").value("backend"))
			.andExpect(jsonPath("$[0].postCount").value(8))
			.andExpect(jsonPath("$[1].postCount").value(0));
	}
}
