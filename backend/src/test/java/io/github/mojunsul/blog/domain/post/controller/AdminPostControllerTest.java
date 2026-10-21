package io.github.mojunsul.blog.domain.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import io.github.mojunsul.blog.domain.post.dto.AdminPostDetailResponse;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSearchRequest;
import io.github.mojunsul.blog.domain.post.dto.AdminPostSummaryResponse;
import io.github.mojunsul.blog.domain.post.dto.PostCreateResponse;
import io.github.mojunsul.blog.domain.post.dto.PostRequest;
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

	private ResultActions create(String body) throws Exception {
		MockHttpServletRequestBuilder request = post(URL).contentType(MediaType.APPLICATION_JSON);
		if (body != null) {
			request.content(body);
		}
		return mockMvc.perform(request);
	}

	private static String body(String field, String value) {
		String title = field.equals("title") ? value : "\"제목\"";
		String slug = field.equals("slug") ? value : "\"slug\"";
		String summary = field.equals("summary") ? value : "null";
		String content = field.equals("content") ? value : "\"본문\"";
		String thumbnailUrl = field.equals("thumbnailUrl") ? value : "null";
		String categoryId = field.equals("categoryId") ? value : "null";
		String status = field.equals("status") ? value : "\"DRAFT\"";
		return "{\"title\":" + title + ",\"slug\":" + slug + ",\"summary\":" + summary + ",\"content\":" + content
			+ ",\"thumbnailUrl\":" + thumbnailUrl + ",\"categoryId\":" + categoryId + ",\"status\":" + status + "}";
	}

	private static String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private PostRequest captureCreated() {
		ArgumentCaptor<PostRequest> captor = ArgumentCaptor.forClass(PostRequest.class);
		then(postService).should().create(captor.capture());
		return captor.getValue();
	}

	private void stubCreate() {
		given(postService.create(any())).willReturn(new PostCreateResponse(1L, "slug"));
	}

	@Test
	void 작성하면_201과_Location과_id_slug를_응답한다() throws Exception {
		// given
		given(postService.create(any())).willReturn(new PostCreateResponse(12L, "spring-security-jwt"));

		// when & then
		create(body("slug", "\"spring-security-jwt\"")).andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/v1/admin/posts/12"))
			.andExpect(jsonPath("$.id").value(12))
			.andExpect(jsonPath("$.slug").value("spring-security-jwt"));
	}

	@Test
	void 수정하면_200과_상세_응답이다() throws Exception {
		// given
		given(postService.update(anyLong(), any())).willReturn(new AdminPostDetailResponse(1L, "제목", "slug", null,
			"본문", null, PostStatus.DRAFT, null, null, List.of(), null, Instant.parse("2026-10-12T09:00:00Z"),
			Instant.parse("2026-10-12T09:30:00Z")));

		// when & then
		mockMvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON).content(body("slug", "\"slug\"")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.tags").isEmpty());
	}

	@Test
	void 삭제하면_204다() throws Exception {
		// when & then
		mockMvc.perform(delete(URL + "/1")).andExpect(status().isNoContent());
		then(postService).should().delete(1L);
	}

	@Test
	void 서비스가_예외를_던지면_ProblemDetail로_응답한다() throws Exception {
		// given
		given(postService.create(any())).willThrow(new BusinessException(ErrorCode.DUPLICATE_SLUG));
		given(postService.update(anyLong(), any())).willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

		// when & then
		create(body("slug", "\"slug\"")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
		mockMvc.perform(put(URL + "/9").contentType(MediaType.APPLICATION_JSON).content(body("slug", "\"slug\"")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " ", "\\u3000", "a\\u0000b", "a\\tb", "a\\nb", "a\\u007fb", "a\\u0085b"})
	void title이_잘못되면_400이다(String title) throws Exception {
		// when & then
		create(body("title", json(title))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		then(postService).should(never()).create(any());
	}

	@Test
	void title은_200자까지_통과하고_공백_제거_뒤_길이로_센다() throws Exception {
		// given
		stubCreate();
		String max = "a".repeat(200);

		// when & then
		create(body("title", json(max))).andExpect(status().isCreated());
		create(body("title", json("  " + max + "\\u3000"))).andExpect(status().isCreated());
		create(body("title", json(max + "a"))).andExpect(status().isBadRequest());
	}

	@Test
	void title의_앞뒤_공백은_제거해_서비스에_전달한다() throws Exception {
		// given
		stubCreate();

		// when
		create(body("title", "\" 제 목\\t\"")).andExpect(status().isCreated());

		// then
		assertThat(captureCreated().title()).isEqualTo("제 목");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " ", "Slug", " slug", "slug ", "a--b", "-a", "a-", "a_b", "한글"})
	void slug가_잘못되면_400이다(String slug) throws Exception {
		// when & then
		create(body("slug", json(slug))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void slug는_200자까지_통과한다() throws Exception {
		// given
		stubCreate();

		// when & then
		create(body("slug", json("a".repeat(200)))).andExpect(status().isCreated());
		create(body("slug", json("a".repeat(201)))).andExpect(status().isBadRequest());
	}

	@Test
	void summary는_500자까지_통과하고_공백_제거_뒤_길이로_센다() throws Exception {
		// given
		stubCreate();
		String max = "a".repeat(500);

		// when & then
		create(body("summary", json(max))).andExpect(status().isCreated());
		create(body("summary", json("  " + max + " "))).andExpect(status().isCreated());
		create(body("summary", json(max + "a"))).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "\\u3000\\t\\n"})
	void summary가_빈_값이면_null로_바뀐다(String summary) throws Exception {
		// given
		stubCreate();

		// when
		create(body("summary", json(summary))).andExpect(status().isCreated());

		// then
		assertThat(captureCreated().summary()).isNull();
	}

	@Test
	void summary는_줄바꿈을_허용하고_NUL은_400이다() throws Exception {
		// given
		stubCreate();

		// when & then
		create(body("summary", "\"a\\nb\"")).andExpect(status().isCreated());
		create(body("summary", "\"a\\u0000b\"")).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " ", "\\n\\t"})
	void content가_비어_있으면_400이다(String content) throws Exception {
		// when & then
		create(body("content", json(content))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void content는_최대_길이까지_통과하고_앞뒤_공백은_그대로_둔다() throws Exception {
		// given
		stubCreate();
		String max = "가".repeat(100000);

		// when & then
		create(body("content", json(max))).andExpect(status().isCreated());
		create(body("content", json(max + "가"))).andExpect(status().isBadRequest());
		create(body("content", "\" a\\n\"")).andExpect(status().isCreated());
		assertThat(captureLast().content()).isEqualTo(" a\n");
		create(body("content", "\"a\\u0000b\"")).andExpect(status().isBadRequest());
	}

	private PostRequest captureLast() {
		ArgumentCaptor<PostRequest> captor = ArgumentCaptor.forClass(PostRequest.class);
		then(postService).should(org.mockito.Mockito.atLeastOnce()).create(captor.capture());
		return captor.getValue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"javascript:alert(1)", "data:text/html,x", "ftp://a.com/a.png", "//a.com/a.png",
		"a.com/a.png", "http://", "https://a.com/a b.png", "https://a.com/a\\tb.png", "https://a.com/a\\u0000b",
		"HTTP://a.com/a.png"})
	void thumbnailUrl이_http_URL이_아니면_400이다(String url) throws Exception {
		// when & then
		create(body("thumbnailUrl", json(url))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void thumbnailUrl은_500자까지_통과하고_앞뒤_공백은_제거한다() throws Exception {
		// given
		stubCreate();
		String max = "https://a.com/" + "a".repeat(486);

		// when & then
		create(body("thumbnailUrl", json(max))).andExpect(status().isCreated());
		create(body("thumbnailUrl", json(max + "a"))).andExpect(status().isBadRequest());
		create(body("thumbnailUrl", "\" http://a.com/a.png \"")).andExpect(status().isCreated());
		assertThat(captureLast().thumbnailUrl()).isEqualTo("http://a.com/a.png");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "\\u3000"})
	void thumbnailUrl이_빈_값이면_null로_바뀐다(String url) throws Exception {
		// given
		stubCreate();

		// when
		create(body("thumbnailUrl", json(url))).andExpect(status().isCreated());

		// then
		assertThat(captureCreated().thumbnailUrl()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "draft", "ARCHIVED"})
	void status가_잘못되면_400이다(String status) throws Exception {
		// when & then
		create(body("status", json(status))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"true", "[]", "{}"})
	void status가_불리언_배열_객체면_400이다(String status) throws Exception {
		// when & then
		create(body("status", status)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"0,DRAFT", "1,PUBLISHED"})
	void status_숫자는_열거형_순서로_받아들여진다(String raw, PostStatus expected) throws Exception {
		// given
		stubCreate();

		// when
		create(body("status", raw)).andExpect(status().isCreated());

		// then
		assertThat(captureCreated().status()).isEqualTo(expected);
	}

	@Test
	void 문자열_필드의_숫자와_불리언은_문자열로_바뀌어_규칙을_받는다() throws Exception {
		// given
		stubCreate();

		// when
		create("{\"title\":true,\"slug\":1,\"summary\":2,\"content\":false,\"status\":\"DRAFT\"}")
			.andExpect(status().isCreated());

		// then
		PostRequest request = captureCreated();
		assertThat(request.title()).isEqualTo("true");
		assertThat(request.slug()).isEqualTo("1");
		assertThat(request.summary()).isEqualTo("2");
		assertThat(request.content()).isEqualTo("false");
	}

	@Test
	void 숫자로_바뀐_thumbnailUrl은_URL_규칙에_걸려_400이다() throws Exception {
		// when & then
		create(body("thumbnailUrl", "1")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("thumbnailUrl"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"title", "slug", "summary", "content", "thumbnailUrl"})
	void 문자열_필드가_배열이나_객체면_400이고_errors는_비어_있다(String field) throws Exception {
		// when & then
		create(body(field, "[\"a\"]")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors").isEmpty());
		create(body(field, "{\"x\":1}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void status가_null이면_400이고_errors에_status가_있다() throws Exception {
		// when & then
		create(body("status", "null")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("status"));
	}

	@Test
	void status가_없으면_400이고_errors에_status가_있다() throws Exception {
		// when & then
		create("{\"title\":\"t\",\"slug\":\"s\",\"content\":\"c\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("status"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
		1       | 1
		"1"     | 1
		1.5     | 1
		1.0     | 1
		""      |
		null    |
		""")
	void categoryId는_숫자_변환_결과가_서비스에_전달된다(String raw, Long expected) throws Exception {
		// given
		stubCreate();

		// when
		create(body("categoryId", raw)).andExpect(status().isCreated());

		// then
		assertThat(captureCreated().categoryId()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {"\"abc\"", "true", "[]", "{}", "99999999999999999999"})
	void categoryId가_숫자가_아니면_400이다(String categoryId) throws Exception {
		// when & then
		create(body("categoryId", categoryId)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 음수와_0인_categoryId는_검증하지_않고_서비스에_전달한다() throws Exception {
		// given
		stubCreate();

		// when & then
		create(body("categoryId", "0")).andExpect(status().isCreated());
		create(body("categoryId", "-1")).andExpect(status().isCreated());
	}

	@Test
	void tags를_보내도_무시한다() throws Exception {
		// given
		stubCreate();

		// when & then
		create("{\"title\":\"t\",\"slug\":\"s\",\"content\":\"c\",\"status\":\"DRAFT\",\"tags\":[\"a\"]}")
			.andExpect(status().isCreated());
	}

	@Test
	void 같은_키가_여럿이면_마지막_값을_쓴다() throws Exception {
		// given
		stubCreate();

		// when
		create("{\"title\":\"a\",\"title\":\"b\",\"slug\":\"s\",\"content\":\"c\",\"status\":\"DRAFT\"}")
			.andExpect(status().isCreated());

		// then
		assertThat(captureCreated().title()).isEqualTo("b");
	}

	@ParameterizedTest
	@ValueSource(strings = {"{broken", "[]"})
	void 깨진_JSON은_400이다(String body) throws Exception {
		// when & then
		create(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 본문이_없으면_400이다() throws Exception {
		// when & then
		create(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void JSON이_아닌_Content_Type이면_415다() throws Exception {
		// when & then
		mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(body("slug", "\"slug\"")))
			.andExpect(status().isUnsupportedMediaType());
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "99999999999999999999"})
	void 경로_id가_Long이_아니면_400이다(String id) throws Exception {
		// when & then
		mockMvc.perform(put(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body("slug", "\"slug\"")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(delete(URL + "/" + id)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 검증_실패_사유는_사용자용_한국어_메시지다() throws Exception {
		// when & then
		create(body("slug", "\"Bad\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("slug"))
			.andExpect(jsonPath("$.errors[0].reason").value("slug는 영문 소문자·숫자·하이픈만 쓸 수 있습니다"));
	}
}
