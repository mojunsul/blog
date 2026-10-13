package io.github.mojunsul.blog.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

class RequestIdFilterTest {

	private static final String UUID_FORMAT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private final RequestIdFilter filter = new RequestIdFilter();

	@Test
	void 형식에_맞는_요청_ID는_그대로_응답_헤더와_MDC에_들어간다() throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Request-Id", "abc-123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> mdcInChain = new AtomicReference<>();
		MockFilterChain chain = new MockFilterChain() {
			@Override
			public void doFilter(ServletRequest req, ServletResponse res) {
				mdcInChain.set(MDC.get("requestId"));
			}
		};

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
		assertThat(mdcInChain.get()).isEqualTo("abc-123");
	}

	@Test
	void 헤더가_없으면_UUID를_만든다() throws Exception {
		// given
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

		// then
		assertThat(response.getHeader("X-Request-Id")).matches(UUID_FORMAT);
	}

	@ParameterizedTest
	@MethodSource("invalidRequestIds")
	void 형식이_다른_요청_ID는_버리고_UUID를_만든다(String invalid) throws Exception {
		// given
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Request-Id", invalid);
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getHeader("X-Request-Id")).matches(UUID_FORMAT);
	}

	static Stream<String> invalidRequestIds() {
		return Stream.of("a".repeat(65), "abc\ninjected", "한글", "");
	}

	@Test
	void 요청이_끝나면_MDC에_요청_ID가_남지_않는다() throws Exception {
		// when
		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

		// then
		assertThat(MDC.get("requestId")).isNull();
	}
}
