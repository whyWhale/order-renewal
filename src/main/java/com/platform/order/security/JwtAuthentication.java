package com.platform.order.security;

import com.google.common.base.Preconditions;

public record JwtAuthentication(Long id, String token) {
	public JwtAuthentication {
		Preconditions.checkArgument(id != null, "user id is required elements");
		Preconditions.checkArgument(!token.isBlank(), "token is required elements");
	}
}
