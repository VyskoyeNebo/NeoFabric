/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl.discovery;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BundledApiRegistryTest {
	private static final String TEST_CHECKSUM = String.join("", Collections.nCopies(128, "a"));
	private static final String TEST_RESOURCE = BundledApiRegistry.RESOURCE_ROOT + "1.20.1/test-api.jar";

	@Test
	void bundledIndexContainsConfiguredMinecraft1201Through1204() throws IOException {
		BundledApiRegistry registry = BundledApiRegistry.load();
		Assertions.assertEquals(Arrays.asList("1.20.1", "1.20.2", "1.20.3", "1.20.4"), new ArrayList<>(registry.getEntries().keySet()));
		BundledApiRegistry.Entry entry = registry.require("1.20.1");
		Assertions.assertEquals("1.20.1", entry.getMinecraftVersion());
		Assertions.assertEquals("0.92.12+1.20.1", entry.getApiVersion());
		Assertions.assertEquals(BundledApiRegistry.RESOURCE_ROOT + "1.20.1/NeoFabric-API-0.92.12+1.20.1.jar", entry.getResource());
		Assertions.assertEquals("NeoFabric-API-0.92.12+1.20.1.jar", entry.getFileName());
		Assertions.assertEquals("de9da2344043b3e9ef9fb7dbfff5f2577b06000c4f73f2a37959a80123141169084cece19133bd299b45feaaa4648bdaca2ea085684888cc8c55cc9134a477ca",
				entry.getSha512());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"})
	void supportedButUnconfiguredVersionsNeverFallBack(String version) throws IOException {
		BundledApiRegistry registry = parse(index(record("1.20.1", fields("test-api", TEST_RESOURCE, TEST_CHECKSUM))));
		IOException error = Assertions.assertThrows(IOException.class, () -> registry.require(version));
		Assertions.assertTrue(error.getMessage().contains("Minecraft " + version));
		Assertions.assertTrue(error.getMessage().contains("尚未内置"));
		Assertions.assertTrue(error.getMessage().contains("不会回退"));
		Assertions.assertEquals("1.20.1", registry.require("1.20.1").getMinecraftVersion());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.5", "1.20.6"})
	void bundledIndexRejectsVersionsWithoutResources(String version) throws IOException {
		IOException error = Assertions.assertThrows(IOException.class, () -> BundledApiRegistry.load().require(version));
		Assertions.assertTrue(error.getMessage().contains("Minecraft " + version));
		Assertions.assertTrue(error.getMessage().contains("尚未内置"));
		Assertions.assertTrue(error.getMessage().contains("不会回退"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"1.20", "1.20.7", "1.20.1-rc.1", "24w14a", "1.21"})
	void unsupportedVersionsAreRejected(String version) throws IOException {
		IOException error = Assertions.assertThrows(IOException.class, () -> BundledApiRegistry.load().require(version));
		Assertions.assertTrue(error.getMessage().contains("不支持的 Minecraft 版本"));
	}

	@Test
	void versionSelectionUsesExactRecordAndPreservesIndexOrder() throws IOException {
		String secondChecksum = String.join("", Collections.nCopies(128, "b"));
		String secondResource = BundledApiRegistry.RESOURCE_ROOT + "1.20.2/second-test-api.jar";
		BundledApiRegistry registry = parse(index(record("1.20.1", fields("first-test-api", TEST_RESOURCE, TEST_CHECKSUM))
				+ "," + record("1.20.2", fields("second-test-api", secondResource, secondChecksum))));
		Assertions.assertEquals(Arrays.asList("1.20.1", "1.20.2"), new ArrayList<>(registry.getEntries().keySet()));
		BundledApiRegistry.Entry first = registry.require("1.20.1");
		BundledApiRegistry.Entry second = registry.require("1.20.2");
		Assertions.assertEquals("1.20.1", first.getMinecraftVersion());
		Assertions.assertEquals("first-test-api", first.getApiVersion());
		Assertions.assertEquals(TEST_RESOURCE, first.getResource());
		Assertions.assertEquals(TEST_CHECKSUM, first.getSha512());
		Assertions.assertEquals("test-api.jar", first.getFileName());
		Assertions.assertEquals("1.20.2", second.getMinecraftVersion());
		Assertions.assertEquals("second-test-api", second.getApiVersion());
		Assertions.assertEquals(secondResource, second.getResource());
		Assertions.assertEquals(secondChecksum, second.getSha512());
		Assertions.assertEquals("second-test-api.jar", second.getFileName());
		Assertions.assertNotSame(first, second);
	}

	@Test
	void registryRecordsCannotBeReplacedOrRemoved() throws IOException {
		BundledApiRegistry registry = BundledApiRegistry.load();
		Map<String, BundledApiRegistry.Entry> entries = registry.getEntries();
		BundledApiRegistry.Entry entry = registry.require("1.20.1");
		Assertions.assertThrows(UnsupportedOperationException.class, () -> entries.clear());
		Assertions.assertThrows(UnsupportedOperationException.class, () -> entries.put("1.20.2", entry));
		Assertions.assertThrows(UnsupportedOperationException.class, () -> entries.entrySet().iterator().next().setValue(null));
		Assertions.assertSame(entry, registry.require("1.20.1"));
		Assertions.assertEquals(Arrays.asList("1.20.1", "1.20.2", "1.20.3", "1.20.4"), new ArrayList<>(entries.keySet()));
	}

	static Stream<Arguments> invalidIndexes() {
		List<Arguments> cases = new ArrayList<>();
		String validFields = fields("test-api", TEST_RESOURCE, TEST_CHECKSUM);
		String validRecord = record("1.20.1", validFields);
		cases.add(Arguments.of("missing schema", "{\"apis\":{}}"));
		cases.add(Arguments.of("missing API records", "{\"schemaVersion\":1}"));
		cases.add(Arguments.of("unknown top-level field", "{\"schemaVersion\":1,\"apis\":{},\"extra\":true}"));
		cases.add(Arguments.of("duplicate schema", "{\"schemaVersion\":1,\"schemaVersion\":1,\"apis\":{}}"));
		cases.add(Arguments.of("duplicate API map", "{\"schemaVersion\":1,\"apis\":{},\"apis\":{}}"));
		cases.add(Arguments.of("unsupported schema", "{\"schemaVersion\":2,\"apis\":{}}"));
		cases.add(Arguments.of("string schema", "{\"schemaVersion\":\"1\",\"apis\":{}}"));
		cases.add(Arguments.of("null schema", "{\"schemaVersion\":null,\"apis\":{}}"));
		cases.add(Arguments.of("API array", "{\"schemaVersion\":1,\"apis\":[]}"));
		cases.add(Arguments.of("entry array", index("\"1.20.1\":[]")));
		cases.add(Arguments.of("duplicate Minecraft record", index(validRecord + "," + validRecord)));
		cases.add(Arguments.of("unsupported Minecraft record", index(record("1.20.7", validFields))));
		cases.add(Arguments.of("trailing data", index(validRecord) + "{}"));
		cases.add(Arguments.of("incomplete JSON", "{\"schemaVersion\":1,\"apis\":{"));
		cases.add(Arguments.of("non-object document", "[]"));

		for (String field : Arrays.asList("apiVersion", "resource", "sha512")) {
			String omitted = validFields.replaceFirst("\\\"" + field + "\\\":\\\"[^\\\"]*\\\",?", "").replaceAll(",$", "");
			cases.add(Arguments.of("missing " + field, index(record("1.20.1", omitted))));
			cases.add(Arguments.of("duplicate " + field, index(record("1.20.1", validFields + "," + quote(field) + ":\"duplicate\""))));
			cases.add(Arguments.of("non-string " + field, index(record("1.20.1", validFields.replaceFirst(
					"\\\"" + field + "\\\":\\\"[^\\\"]*\\\"", quote(field) + ":null")))));
		}

		cases.add(Arguments.of("empty API version", index(record("1.20.1", fields("", TEST_RESOURCE, TEST_CHECKSUM)))));
		cases.add(Arguments.of("blank API version", index(record("1.20.1", fields("  ", TEST_RESOURCE, TEST_CHECKSUM)))));
		cases.add(Arguments.of("unknown entry field", index(record("1.20.1", validFields + ",\"other\":\"value\""))));
		cases.add(Arguments.of("duplicate source", index(record("1.20.1", validFields + ",\"source\":\"one\",\"source\":\"two\""))));
		cases.add(Arguments.of("non-string source", index(record("1.20.1", validFields + ",\"source\":false"))));

		for (String checksum : Arrays.asList("", "a", TEST_CHECKSUM.substring(1), TEST_CHECKSUM + "a", TEST_CHECKSUM.toUpperCase(Locale.ROOT),
				String.join("", Collections.nCopies(128, "g")))) {
			cases.add(Arguments.of("bad SHA-512 " + checksum, index(record("1.20.1", fields("test-api", TEST_RESOURCE, checksum)))));
		}

		for (String resource : Arrays.asList("", "META-INF/neofabric/old.jar", BundledApiRegistry.RESOURCE_ROOT + "1.20.2/test.jar",
				BundledApiRegistry.RESOURCE_ROOT + "1.20.1/../1.20.2/test.jar", BundledApiRegistry.RESOURCE_ROOT + "1.20.1/./test.jar",
				BundledApiRegistry.RESOURCE_ROOT + "1.20.1/subdir/test.jar", BundledApiRegistry.RESOURCE_ROOT + "1.20.1/..\\test.jar",
				BundledApiRegistry.RESOURCE_ROOT + "1.20.1/%2e%2e.jar", "/" + TEST_RESOURCE, "https://example.invalid/test.jar",
				BundledApiRegistry.RESOURCE_ROOT + "1.20.1/test.zip")) {
			cases.add(Arguments.of("invalid resource " + resource, index(record("1.20.1", fields("test-api", resource, TEST_CHECKSUM)))));
		}

		return cases.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidIndexes")
	void rejectsInvalidIndex(String description, String json) {
		IOException error = Assertions.assertThrows(IOException.class, () -> parse(json), description);
		Assertions.assertNotNull(error.getMessage());
		Assertions.assertFalse(error.getMessage().isEmpty());
	}

	private static BundledApiRegistry parse(String json) throws IOException {
		return BundledApiRegistry.parse(new StringReader(json));
	}

	private static String index(String records) {
		return "{\"schemaVersion\":1,\"apis\":{" + records + "}}";
	}

	private static String record(String minecraftVersion, String fields) {
		return quote(minecraftVersion) + ":{" + fields + "}";
	}

	private static String fields(String apiVersion, String resource, String checksum) {
		return "\"apiVersion\":" + quote(apiVersion) + ",\"resource\":" + quote(resource) + ",\"sha512\":" + quote(checksum);
	}

	private static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
