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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.UrlUtil;

class BundledModCandidateFinderTest {
	@TempDir
	Path tempDir;

	private FabricLoaderImpl loader;
	private ModDiscoverer discoverer;
	private BundledModCandidateFinder finder;

	@BeforeEach
	void setUp() {
		FabricLauncher launcher = mock();
		when(launcher.getEnvironmentType()).thenReturn(EnvType.CLIENT);
		FabricLauncherBase.setLauncher(launcher);

		GameProvider provider = mock();
		when(provider.getBuiltinMods()).thenReturn(Arrays.asList(
				new BuiltinMod(Collections.singletonList(tempDir), new BuiltinModMetadata.Builder("minecraft", "1.20.1").build()),
				new BuiltinMod(Collections.singletonList(tempDir), new BuiltinModMetadata.Builder("fabricloader", FabricLoaderImpl.VERSION).build())));
		loader = mock();
		when(loader.getGameProvider()).thenReturn(provider);
		discoverer = new ModDiscoverer(mock(), mock());
		finder = new BundledModCandidateFinder(tempDir, false);
	}

	@Test
	void extractsOriginalBytesAndRepairsCorruptCache() throws IOException {
		Path original = tempDir.resolve("original.jar");

		try (InputStream resource = BundledModCandidateFinder.class.getResourceAsStream(BundledModCandidateFinder.API_RESOURCE)) {
			Assertions.assertNotNull(resource);
			Files.copy(resource, original);
		}

		Path extracted = finder.extractBundledApi();
		Assertions.assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(extracted));
		Assertions.assertEquals(extracted, finder.extractBundledApi());
		Files.write(extracted, "corrupted cache".getBytes(StandardCharsets.UTF_8));
		Assertions.assertEquals(extracted, finder.extractBundledApi());
		Assertions.assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(extracted));
	}

	@Test
	void bundledApiResolvesAndBuildsContainersWithoutExternalMods() throws Exception {
		discoverer.addCandidateFinder(finder);
		List<ModCandidateImpl> discovered = discoverer.discoverMods(loader, new HashMap<>());
		ModCandidateImpl api = find(discovered, "fabric-api");
		Assertions.assertEquals("0.92.12+1.20.1", api.getVersion().getFriendlyString());
		Assertions.assertEquals("Fabric API", api.getMetadata().getName());
		Assertions.assertFalse(api.getNestedMods().isEmpty());
		Assertions.assertEquals(api.getMetadata().getJars().size(), api.getNestedMods().size());
		Assertions.assertNotNull(find(discovered, "fabric-api-base"));

		List<ModCandidateImpl> resolved = ModResolver.resolve(discovered, EnvType.CLIENT, new HashMap<>());
		Path processed = Files.createDirectory(tempDir.resolve("processed"));

		for (ModCandidateImpl candidate : resolved) {
			Assertions.assertNotNull(candidate);

			if (!candidate.hasPath() && !candidate.isBuiltin()) {
				candidate.setPaths(Collections.singletonList(candidate.copyToDir(processed, false)));
			}

			Assertions.assertNotNull(new ModContainerImpl(candidate).getMetadata());
		}

		Assertions.assertNotNull(find(resolved, "fabric-api"));
		Assertions.assertNotNull(find(resolved, "fabric-api-base"));
	}

	@Test
	void developmentDependencyUsesItsOriginalCodeSource() throws Exception {
		discoverer.addCandidateFinder(new BundledModCandidateFinder(tempDir, true));
		List<ModCandidateImpl> discovered = discoverer.discoverMods(loader, new HashMap<>());
		Path library = LoaderUtil.normalizeExistingPath(UrlUtil.getCodeSource(MixinExtrasBootstrap.class));
		Assertions.assertTrue(discovered.stream().anyMatch(mod -> mod.hasPath() && mod.getPaths().contains(library)));
	}

	@Test
	void externalCopyWithSameFileNameAndModIdIsRejected() throws Exception {
		Path external = Files.copy(finder.extractBundledApi(), tempDir.resolve(BundledModCandidateFinder.API_FILE_NAME));
		discoverer.addCandidateFinder(out -> out.accept(LoaderUtil.normalizeExistingPath(external), false));
		List<ModCandidateImpl> discovered = discoverer.discoverMods(loader, new HashMap<>());
		Assertions.assertFalse(discovered.stream().anyMatch(mod -> mod.getId().equals("fabric-api")));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void externalNestedJarNeverInheritsBundledExemption(boolean bundledFirst) throws Exception {
		Path bundledApi = finder.extractBundledApi();
		Path external = tempDir.resolve("external.jar");

		try (ZipFile api = new ZipFile(bundledApi.toFile());
				ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(external))) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"schemaVersion\":1,\"id\":\"external_parent\",\"version\":\"1.0.0\","
					+ "\"neofabric\":true,\"jars\":[{\"file\":\"child.jar\"}]}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			ZipEntry base = api.stream().filter(entry -> entry.getName().startsWith("META-INF/jars/fabric-api-base-"))
					.findFirst().orElseThrow(AssertionError::new);
			zip.putNextEntry(new ZipEntry("child.jar"));

			try (InputStream input = api.getInputStream(base)) {
				byte[] buffer = new byte[8192];
				int count;

				while ((count = input.read(buffer)) != -1) {
					zip.write(buffer, 0, count);
				}
			}

			zip.closeEntry();
		}

		ModCandidateFinder externalFinder = out -> out.accept(LoaderUtil.normalizeExistingPath(external), false);
		discoverer.addCandidateFinder(bundledFirst ? finder : externalFinder);
		discoverer.addCandidateFinder(bundledFirst ? externalFinder : finder);
		List<ModCandidateImpl> discovered = discoverer.discoverMods(loader, new HashMap<>());
		Assertions.assertTrue(find(discovered, "external_parent").getNestedMods().isEmpty());
		Assertions.assertFalse(find(discovered, "fabric-api-base").getParentMods().stream()
				.anyMatch(mod -> mod.getId().equals("external_parent")));
	}

	private static ModCandidateImpl find(List<ModCandidateImpl> mods, String id) {
		return mods.stream().filter(mod -> mod.getId().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing mod: " + id));
	}
}
