package io.github.mojunsul.blog.fixture;

import io.github.mojunsul.blog.domain.auth.entity.Admin;

public final class AdminFixture {

	private AdminFixture() {}

	public static Admin admin() {
		return admin("admin");
	}

	public static Admin admin(String username) {
		return admin(username, "encoded-password");
	}

	public static Admin admin(String username, String encodedPassword) {
		return Admin.builder().username(username).password(encodedPassword).build();
	}
}
