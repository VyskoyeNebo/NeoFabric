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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.util.LoaderUtil;

@ExtendWith(LauncherTestExtension.class)
class NeoFabricGateTest {
	@TempDir
	Path tempDir;

	private FabricLoaderImpl loader;
	private ModDiscoverer discoverer;

	@BeforeEach
	void setUp() {
		GameProvider provider = mock();
		when(provider.getBuiltinMods()).thenReturn(Collections.emptyList());
		loader = mock();
		when(loader.getGameProvider()).thenReturn(provider);
		discoverer = new ModDiscoverer(mock(), mock());
	}

	static Stream<Arguments> gateCases() {
		Object[][] flags = {
				{"", false},
				{",\"neofabric\":true", true},
				{",\"neofabric\":false", false},
				{",\"neofabric\":\"true\"", false},
				{",\"neofabric\":null", false},
				{",\"neofabric\":1", false},
				{",\"neofabric\":{}", false},
				{",\"neofabric\":[]", false},
				{",\"custom\":{\"neofabric\":true}", false},
				{",\"neofabric\":true,\"neofabric\":false", false},
				{",\"neofabric\":false,\"neofabric\":true", true},
				{",\"neofabric\":true,\"neofabric\":\"true\"", false}
		};
		List<Arguments> cases = new ArrayList<>();

		for (String kind : new String[] {"directory", "jar", "nested"}) {
			for (int schema = 0; schema <= 1; schema++) {
				for (Object[] flag : flags) {
					cases.add(Arguments.of(kind, schema, flag[0], flag[1]));
				}
			}
		}

		return cases.stream();
	}

	@ParameterizedTest
	@MethodSource("gateCases")
	void topLevelBooleanControlsDiscovery(String kind, int schema, String flag, boolean accepted) throws Exception {
		String metadata = "{\"schemaVersion\":" + schema + ",\"id\":\"gated_mod\",\"version\":\"1.0.0\"" + flag + "}";
		Path candidate;

		switch (kind) {
		case "directory":
			candidate = Files.createDirectory(tempDir.resolve("mod"));
			Files.write(candidate.resolve("fabric.mod.json"), metadata.getBytes(StandardCharsets.UTF_8));
			break;
		case "jar":
			candidate = Files.write(tempDir.resolve("mod.jar"), createJar(metadata, null));
			break;
		case "nested":
			String parent = "{\"schemaVersion\":1,\"id\":\"parent_mod\",\"version\":\"1.0.0\",\"neofabric\":true,"
					+ "\"jars\":[{\"file\":\"META-INF/jars/child.jar\"}]}";
			candidate = Files.write(tempDir.resolve("parent.jar"), createJar(parent, createJar(metadata, null)));
			break;
		default:
			throw new AssertionError(kind);
		}

		Path sibling = Files.createDirectory(tempDir.resolve("sibling"));
		Files.write(sibling.resolve("fabric.mod.json"), ("{\"schemaVersion\":1,\"id\":\"sibling_mod\","
				+ "\"version\":\"1.0.0\",\"neofabric\":true}").getBytes(StandardCharsets.UTF_8));
		discoverer.addCandidateFinder(out -> {
			out.accept(LoaderUtil.normalizeExistingPath(candidate), false);
			out.accept(LoaderUtil.normalizeExistingPath(sibling), false);
		});

		List<ModCandidateImpl> mods = discoverer.discoverMods(loader, new HashMap<>());
		Assertions.assertEquals(accepted, mods.stream().anyMatch(mod -> mod.getId().equals("gated_mod")));
		Assertions.assertTrue(mods.stream().anyMatch(mod -> mod.getId().equals("sibling_mod")));

		if (kind.equals("nested")) {
			Assertions.assertTrue(mods.stream().anyMatch(mod -> mod.getId().equals("parent_mod")));
		}
	}

	@Test
	void loaderMetadataDeclaresCompatibility() {
		ModCandidateImpl candidate = discoverer.new ModScanTask(Collections.singletonList(
				LoaderUtil.normalizeExistingPath(Paths.get("build/resources/main"))), false).invoke();
		Assertions.assertNotNull(candidate);
		Assertions.assertEquals("fabricloader", candidate.getId());
		Assertions.assertEquals("NeoFabric", candidate.getMetadata().getName());
	}

	private static byte[] createJar(String metadata, byte[] nested) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(metadata.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();

			if (nested != null) {
				zip.putNextEntry(new ZipEntry("META-INF/jars/child.jar"));
				zip.write(nested);
				zip.closeEntry();
			}
		}

		return bytes.toByteArray();
	}
}
