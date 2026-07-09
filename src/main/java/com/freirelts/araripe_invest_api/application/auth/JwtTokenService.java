package com.freirelts.araripe_invest_api.application.auth;

import com.freirelts.araripe_invest_api.infrastructure.config.AraripeSecurityProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final AraripeSecurityProperties properties;

	JwtTokenService(JwtEncoder jwtEncoder, AraripeSecurityProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	String issueAccessToken(AraripeUserDetails userDetails) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(properties.jwt().expiration());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.jwt().issuer())
				.audience(List.of(properties.jwt().audience()))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(userDetails.id().toString())
				.claim("email", userDetails.getUsername())
				.claim("roles", userDetails.roles().stream().map(Enum::name).toList())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
