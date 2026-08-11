package com.cvesters.aurevanta.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two mechanisms now put links in people's inboxes — {@code user_tokens} and the
 * invitations table — and they are only as strong as this is, so it is proved here rather
 * than through whichever of them happens to be tested.
 */
class LinkTokensTests {

	@Test
	void generatesAUrlSafeTokenWithEnoughEntropyToBeUnguessable() {
		// 32 bytes, base64url, unpadded.
		assertThat(LinkTokens.generate()).hasSize(43).matches("[A-Za-z0-9_-]+");
	}

	/**
	 * Not a proof of randomness, which no test can be — but a generator that returned the
	 * same value twice, or drew from a seeded source, would fail here and nowhere else.
	 */
	@Test
	void generatesADifferentTokenEveryTime() {
		Set<String> issued = new HashSet<>();
		for (int attempt = 0; attempt < 100; attempt++) {
			issued.add(LinkTokens.generate());
		}

		assertThat(issued).hasSize(100);
	}

	@Test
	void storesOnlyASha256OfTheToken() throws Exception {
		String raw = LinkTokens.generate();

		assertThat(LinkTokens.hash(raw)).isEqualTo(sha256Hex(raw)).hasSize(64).isNotEqualTo(raw);
	}

	/** Redemption finds a row by hash, so the same token has to hash the same way. */
	@Test
	void hashesOneTokenToOneValue() {
		String raw = LinkTokens.generate();

		assertThat(LinkTokens.hash(raw)).isEqualTo(LinkTokens.hash(raw)).isNotEqualTo(LinkTokens.hash(raw + "x"));
	}

	/**
	 * SHA-256 is mandatory on every Java platform, so this cannot happen in production —
	 * but a hash that quietly became something else would make every token already issued
	 * unredeemable, so it fails loudly rather than being assumed away.
	 */
	@Test
	void refusesToCarryOnIfTheHashAlgorithmIsMissing() {
		assertThatThrownBy(() -> LinkTokens.digest("SHA-000")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("SHA-000");
	}

	private static String sha256Hex(String value) throws Exception {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

}
