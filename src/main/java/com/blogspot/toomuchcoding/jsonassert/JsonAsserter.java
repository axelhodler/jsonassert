package com.blogspot.toomuchcoding.jsonassert;

import java.util.LinkedList;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.DocumentContext;

import net.minidev.json.JSONArray;

class JsonAsserter implements JsonVerifiable {

	private static final Logger log = LoggerFactory.getLogger(JsonAsserter.class);

	protected final DocumentContext parsedJson;
	protected final LinkedList<String> jsonPathBuffer;
	protected final Object fieldName;
	protected final JsonAsserterConfiguration jsonAsserterConfiguration;

	protected JsonAsserter(DocumentContext parsedJson, LinkedList<String> jsonPathBuffer,
			Object fieldName, JsonAsserterConfiguration jsonAsserterConfiguration) {
		this.parsedJson = parsedJson;
		this.jsonPathBuffer = new LinkedList<String>(jsonPathBuffer);
		this.fieldName = fieldName;
		this.jsonAsserterConfiguration = jsonAsserterConfiguration;
	}

	@Override
	public JsonVerifiable contains(final Object value) {
		FieldAssertion asserter = new FieldAssertion(parsedJson, jsonPathBuffer, value, jsonAsserterConfiguration);
		// this is fake part of jsonpath since in the next section we will remove this entry
		asserter.jsonPathBuffer.offer("[*]");
		return asserter;
	}

	@Override
	public FieldAssertion field(final Object value) {
		FieldAssertion asserter = new FieldAssertion(parsedJson, jsonPathBuffer, value, jsonAsserterConfiguration);
		asserter.jsonPathBuffer.offer("." + String.valueOf(value));
		return asserter;
	}

	@Override
	public ArrayAssertion array(final Object value) {
		if (value == null) {
			return array();
		}
		ArrayAssertion asserter = new ArrayAssertion(parsedJson, jsonPathBuffer, value, jsonAsserterConfiguration);
		asserter.jsonPathBuffer.offer("." + String.valueOf(value) + "[*]");
		return asserter;
	}

	@Override
	public ArrayValueAssertion arrayField() {
		return new ArrayValueAssertion(parsedJson, jsonPathBuffer, jsonAsserterConfiguration);
	}

	@Override
	public ArrayAssertion array() {
		ArrayAssertion asserter = new ArrayAssertion(parsedJson, jsonPathBuffer, jsonAsserterConfiguration);
		asserter.jsonPathBuffer.offer("[*]");
		return asserter;
	}

	@Override
	public JsonVerifiable isEqualTo(String value) {
		if (value == null) {
			return isNull();
		}
		ReadyToCheckAsserter readyToCheck = new ReadyToCheckAsserter(parsedJson,
				jsonPathBuffer, fieldName, jsonAsserterConfiguration);
		readyToCheck.jsonPathBuffer.removeLast();
		readyToCheck.jsonPathBuffer.offer("[?(@." + String.valueOf(fieldName)+ " == " + wrapValueWithSingleQuotes(value) + ")]");
		readyToCheck.checkBufferedJsonPathString();
		return readyToCheck;
	}

	@Override
	public JsonVerifiable isEqualTo(Object value) {
		if (value == null) {
			return isNull();
		}
		if (value instanceof Number) {
			return isEqualTo((Number) value);
		} else if (value instanceof Boolean) {
			return isEqualTo((Boolean) value);
		} else if (value instanceof Pattern) {
			return matches(((Pattern) value).pattern());
		}
		return isEqualTo(value.toString());
	}

	@Override
	public JsonVerifiable isEqualTo(Number value) {
		if (value == null) {
			return isNull();
		}
		ReadyToCheckAsserter readyToCheck = new ReadyToCheckAsserter(parsedJson,
				jsonPathBuffer, fieldName, jsonAsserterConfiguration);
		readyToCheck.jsonPathBuffer.removeLast();
		readyToCheck.jsonPathBuffer.offer("[?(@." + String.valueOf(fieldName)+ " == " + value + ")]");
		readyToCheck.checkBufferedJsonPathString();
		return readyToCheck;
	}

	@Override
	public JsonVerifiable isNull() {
		ReadyToCheckAsserter readyToCheck = new ReadyToCheckAsserter(parsedJson,
				jsonPathBuffer, fieldName, jsonAsserterConfiguration);
		readyToCheck.jsonPathBuffer.removeLast();
		readyToCheck.jsonPathBuffer.offer("[?(@." + String.valueOf(fieldName) + " == null)]");
		readyToCheck.checkBufferedJsonPathString();
		return readyToCheck;
	}

	@Override
	public JsonVerifiable matches(String value) {
		if (value == null) {
			return isNull();
		}
		ReadyToCheckAsserter readyToCheck = new ReadyToCheckAsserter(parsedJson,
				jsonPathBuffer, fieldName, jsonAsserterConfiguration);
		readyToCheck.jsonPathBuffer.removeLast();
		readyToCheck.jsonPathBuffer.offer("[?(@." + String.valueOf(fieldName)
				+ " =~ /" + stringWithEscapedSingleQuotes(value) + "/)]");
		readyToCheck.checkBufferedJsonPathString();
		return readyToCheck;
	}

	@Override
	public JsonVerifiable isEqualTo(Boolean value) {
		if (value == null) {
			return isNull();
		}
		ReadyToCheckAsserter readyToCheck = new ReadyToCheckAsserter(parsedJson,
				jsonPathBuffer, fieldName, jsonAsserterConfiguration);
		readyToCheck.jsonPathBuffer.removeLast();
		readyToCheck.jsonPathBuffer.offer("[?(@." + String.valueOf(fieldName)+ " == " + String.valueOf(value) + ")]");
		readyToCheck.checkBufferedJsonPathString();
		return readyToCheck;
	}

	@Override
	public JsonVerifiable value() {
		return new ReadyToCheckAsserter(parsedJson,
				jsonPathBuffer, fieldName, jsonAsserterConfiguration);
	}

	@Override
	public JsonVerifiable withoutThrowingException() {
		jsonAsserterConfiguration.ignoreJsonPathException = true;
		return this;
	}

	private void check(String jsonPathString) {
		if (jsonAsserterConfiguration.ignoreJsonPathException) {
			log.warn("WARNING!!! Overriding verification of the JSON Path. Your tests may pass even though they shouldn't");
			return;
		}
		if (parsedJson.read(jsonPathString, JSONArray.class).isEmpty()) {
			throw new IllegalStateException("Parsed JSON doesn't match the JSON path [" + jsonPathString + "]");
		}
	}

	protected void checkBufferedJsonPathString() {
		check(createJsonPathString());
	}

	private String createJsonPathString() {
		LinkedList<String> queue = new LinkedList<String>(jsonPathBuffer);
		StringBuilder stringBuffer = new StringBuilder();
		while (!queue.isEmpty()) {
			stringBuffer.append(queue.remove());
		}
		return stringBuffer.toString();
	}

	@Override
	public String jsonPath() {
		return createJsonPathString();
	}

	@Override
	public void matchesJsonPath(String jsonPath) {
		check(jsonPath);
	}

	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!getClass().equals(o.getClass()))
			return false;
		JsonAsserter jsonPathAsserter = (JsonAsserter) o;
		if (!fieldName.equals(jsonPathAsserter.fieldName))
			return false;
		return jsonPathBuffer.equals(jsonPathAsserter.jsonPathBuffer);

	}

	public int hashCode() {
		int result;
		result = (parsedJson != null ? parsedJson.hashCode() : 0);
		result = 31 * result + (jsonPathBuffer != null ? jsonPathBuffer.hashCode() : 0);
		result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "\\nAsserter{\n    " + "jsonPathBuffer=" + String.valueOf(jsonPathBuffer)
				+ "\n}";
	}

	@Override
	public boolean isIteratingOverNamelessArray() {
		return false;
	}

	@Override
	public boolean isIteratingOverArray() {
		return false;
	}

	@Override
	public boolean isAssertingAValueInArray() {
		return false;
	}

	protected static String stringWithEscapedSingleQuotes(Object object) {
		String stringValue = object.toString();
		return stringValue.replaceAll("'", "\\\\'");
	}

	protected String wrapValueWithSingleQuotes(Object value) {
		return value instanceof String ?
				"'" + stringWithEscapedSingleQuotes(value) + "'" :
				value.toString();
	}
}