package com.cvesters.aurevanta.auth.problem;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Why one field was rejected, said in a way a client can translate.
 *
 * <p>
 * The {@code code} names the constraint that failed — {@code size}, {@code email} — and
 * never the English Bean Validation happened to generate. The attributes are what a
 * message needs to interpolate, so a client can write "use at least 12 characters" in its
 * own language without knowing that the field is a password or that the bound is 12.
 *
 * <p>
 * Attributes are flattened into the object rather than nested, so the wire form is
 * {@code {"code": "size", "min": 12, "max": 72}}. Only the constraint's numeric
 * attributes appear; how validation is implemented stays on this side.
 */
public record FieldProblem(String code, Map<String, Object> attributes) {

	@JsonProperty("code")
	@Override
	public String code() {
		return this.code;
	}

	@JsonAnyGetter
	@Override
	public Map<String, Object> attributes() {
		return this.attributes;
	}

}
