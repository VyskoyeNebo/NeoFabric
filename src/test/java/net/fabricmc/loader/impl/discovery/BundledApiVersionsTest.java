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
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinMod;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.LoaderUtil;

@ExtendWith(LauncherTestExtension.class)
class BundledApiVersionsTest {
	@TempDir
	Path tempDir;

	static Stream<Arguments> apiVersions() {
		return Stream.of(
				Arguments.of("1.20.2", "0.91.6+1.20.2"),
				Arguments.of("1.20.3", "0.91.1+1.20.3"),
				Arguments.of("1.20.4", "0.97.3+1.20.4"),
				Arguments.of("1.20.5", "0.97.8+1.20.5"),
				Arguments.of("1.20.6", "0.100.8+1.20.6"));
	}

	static Stream<Arguments> environmentCases() {
		return apiVersions().flatMap(version -> Arrays.stream(EnvType.values())
				.map(environment -> Arguments.of(version.get()[0], version.get()[1], environment)));
	}

	static Stream<Arguments> missingResourceCases() {
		return apiVersions().flatMap(version -> Stream.of(false, true).flatMap(remap -> Stream.of(false, true)
				.map(legacyBytes -> Arguments.of(version.get()[0], remap, legacyBytes))));
	}

	@ParameterizedTest
	@MethodSource("apiVersions")
	void extractsSelectedOriginalBytesAndRepairsCacheWithoutTouching1201(String version, String apiVersion) throws IOException {
		BundledApiRegistry registry = BundledApiRegistry.load();
		BundledApiRegistry.Entry entry = registry.require(version);
		Assertions.assertEquals(apiVersion, entry.getApiVersion());
		BundledApiRegistry.Entry originalEntry = registry.require("1.20.1");
		Path originalCache = new BundledModCandidateFinder(tempDir, false, "1.20.1").extractBundledApi();
		byte[] originalBytes = Files.readAllBytes(originalCache);
		byte[] selectedBytes = bundledBytes(entry);
		BundledModCandidateFinder finder = new BundledModCandidateFinder(tempDir, false, version);
		Path selected = finder.extractBundledApi();
		Assertions.assertEquals(cachePath(entry), selected);
		Assertions.assertNotEquals(originalCache, selected);
		Assertions.assertEquals(cachePath(originalEntry), originalCache);
		Assertions.assertArrayEquals(selectedBytes, Files.readAllBytes(selected));
		Assertions.assertEquals(selected, finder.extractBundledApi());
		Files.write(selected, "corrupted selected cache".getBytes(StandardCharsets.UTF_8));
		Assertions.assertEquals(selected, finder.extractBundledApi());
		Assertions.assertArrayEquals(selectedBytes, Files.readAllBytes(selected));
		Files.write(selected, originalBytes);
		Assertions.assertEquals(selected, finder.extractBundledApi());
		Assertions.assertArrayEquals(selectedBytes, Files.readAllBytes(selected));
		Assertions.assertArrayEquals(originalBytes, Files.readAllBytes(originalCache));
	}

	@ParameterizedTest
	@MethodSource("environmentCases")
	void resolvesSelectedApiAndNestedModulesAndBuildsContainers(String version, String apiVersion, EnvType environment) throws Exception {
		when(FabricLauncherBase.getLauncher().getEnvironmentType()).thenReturn(environment);
		ModDiscoverer discoverer = new ModDiscoverer(mock(), mock());
		discoverer.addCandidateFinder(new BundledModCandidateFinder(tempDir, false, version));
		List<ModCandidateImpl> discovered = discoverer.discoverMods(loader(version), new HashMap<>());
		ModCandidateImpl api = find(discovered, "fabric-api");
		Assertions.assertEquals(apiVersion, api.getVersion().getFriendlyString());
		Assertions.assertFalse(api.getNestedMods().isEmpty());
		Assertions.assertEquals(api.getMetadata().getJars().size(), api.getNestedMods().size());
		Set<String> nestedIds = api.getNestedMods().stream()
				.filter(mod -> mod.getMetadata().loadsInEnvironment(environment))
				.map(ModCandidateImpl::getId).collect(Collectors.toSet());
		Assertions.assertTrue(nestedIds.contains("fabric-api-base"));
		List<ModCandidateImpl> resolved = ModResolver.resolve(discovered, environment, new HashMap<>());
		Assertions.assertEquals(apiVersion, find(resolved, "fabric-api").getVersion().getFriendlyString());
		Set<String> resolvedIds = resolved.stream().map(ModCandidateImpl::getId).collect(Collectors.toSet());
		Assertions.assertTrue(resolvedIds.containsAll(nestedIds), "All nested API modules enabled in this environment must resolve");
		Path processed = Files.createDirectory(tempDir.resolve("processed"));
		List<ModContainerImpl> containers = new ArrayList<>();

		for (ModCandidateImpl candidate : resolved) {
			Assertions.assertNotNull(candidate);

			if (!candidate.hasPath() && !candidate.isBuiltin()) {
				candidate.setPaths(Collections.singletonList(candidate.copyToDir(processed, false)));
			}

			ModContainerImpl container = new ModContainerImpl(candidate);
			Assertions.assertNotNull(container.getMetadata());
			Assertions.assertFalse(container.getCodeSourcePaths().isEmpty());
			containers.add(container);
		}

		Assertions.assertFalse(containers.isEmpty());
		Assertions.assertTrue(containers.stream().anyMatch(container -> container.getMetadata().getId().equals("fabric-api")));
		Assertions.assertTrue(containers.stream().anyMatch(container -> container.getMetadata().getId().equals("fabric-api-base")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"})
	void externalCopyOfNewApiIsStillRejectedByGate(String version) throws Exception {
		BundledApiRegistry.Entry entry = BundledApiRegistry.load().require(version);
		Path bundled = new BundledModCandidateFinder(tempDir, false, version).extractBundledApi();
		Path external = Files.copy(bundled, tempDir.resolve(entry.getFileName()));
		ModDiscoverer discoverer = new ModDiscoverer(mock(), mock());
		discoverer.addCandidateFinder(out -> out.accept(LoaderUtil.normalizeExistingPath(external), false));
		List<ModCandidateImpl> discovered = discoverer.discoverMods(loader(version), new HashMap<>());
		Assertions.assertFalse(discovered.stream().anyMatch(mod -> mod.getId().equals("fabric-api")));
		Assertions.assertFalse(discovered.stream().anyMatch(mod -> mod.getId().equals("fabric-api-base")));
	}

	@ParameterizedTest
	@MethodSource("missingResourceCases")
	void missingResourceRejectsEvenValidOrLegacyCachedBytes(String version, boolean requiresRemap, boolean legacyBytes) throws IOException {
		BundledApiRegistry registry = BundledApiRegistry.load();
		BundledApiRegistry.Entry entry = registry.require(version);
		String resource = BundledApiRegistry.RESOURCE_ROOT + version + "/missing.jar";
		BundledApiRegistry missingRegistry = BundledApiRegistry.parse(new StringReader("{\"schemaVersion\":1,\"apis\":{\""
				+ version + "\":{\"apiVersion\":\"" + entry.getApiVersion() + "\",\"resource\":\"" + resource
				+ "\",\"sha512\":\"" + entry.getSha512() + "\"}}}"));
		Path cached = cachePath(missingRegistry.require(version));
		Files.createDirectories(cached.getParent());
		byte[] cachedBytes = bundledBytes(legacyBytes ? registry.require("1.20.1") : entry);
		Files.write(cached, cachedBytes);
		List<Path> candidates = new ArrayList<>();
		BundledModCandidateFinder finder = new BundledModCandidateFinder(tempDir, requiresRemap, version, missingRegistry);
		FormattedException error = Assertions.assertThrows(FormattedException.class,
				() -> finder.findCandidates((paths, remap) -> candidates.addAll(paths)));
		Assertions.assertTrue(error.getMessage().contains("缺少 Minecraft " + version + " 的内置资源"));
		Assertions.assertTrue(error.getMessage().contains(resource));
		Assertions.assertTrue(candidates.isEmpty(), "Neither an API nor MixinExtras candidate may be emitted");
		Assertions.assertArrayEquals(cachedBytes, Files.readAllBytes(cached));
	}

	private FabricLoaderImpl loader(String version) {
		GameProvider provider = mock();
		when(provider.getBuiltinMods()).thenReturn(Arrays.asList(
				new BuiltinMod(Collections.singletonList(tempDir), new BuiltinModMetadata.Builder("minecraft", version).build()),
				new BuiltinMod(Collections.singletonList(tempDir), new BuiltinModMetadata.Builder("fabricloader", FabricLoaderImpl.VERSION).build())));
		FabricLoaderImpl loader = mock();
		when(loader.getGameProvider()).thenReturn(provider);
		return loader;
	}

	private Path cachePath(BundledApiRegistry.Entry entry) {
		return tempDir.resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve("neofabric").resolve("bundled")
				.resolve(entry.getMinecraftVersion()).resolve(entry.getSha512().substring(0, 16)).resolve(entry.getFileName());
	}

	private static byte[] bundledBytes(BundledApiRegistry.Entry entry) throws IOException {
		try (InputStream input = BundledApiVersionsTest.class.getResourceAsStream("/" + entry.getResource());
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Assertions.assertNotNull(input);
			byte[] buffer = new byte[8192];
			int count;

			while ((count = input.read(buffer)) != -1) {
				output.write(buffer, 0, count);
			}

			return output.toByteArray();
		}
	}

	private static ModCandidateImpl find(List<ModCandidateImpl> mods, String id) {
		return mods.stream().filter(mod -> mod.getId().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing mod: " + id));
	}
}
