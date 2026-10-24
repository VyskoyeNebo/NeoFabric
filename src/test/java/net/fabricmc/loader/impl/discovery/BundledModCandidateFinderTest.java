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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.UrlUtil;

@ExtendWith(LauncherTestExtension.class)
class BundledModCandidateFinderTest {
	@TempDir
	Path tempDir;

	private FabricLoaderImpl loader;
	private ModDiscoverer discoverer;
	private BundledModCandidateFinder finder;
	private BundledApiRegistry.Entry apiEntry;

	@BeforeEach
	void setUp() throws IOException {
		GameProvider provider = mock();
		when(provider.getBuiltinMods()).thenReturn(Arrays.asList(
				new BuiltinMod(Collections.singletonList(tempDir), new BuiltinModMetadata.Builder("minecraft", "1.20.1").build()),
				new BuiltinMod(Collections.singletonList(tempDir), new BuiltinModMetadata.Builder("fabricloader", FabricLoaderImpl.VERSION).build())));
		loader = mock();
		when(loader.getGameProvider()).thenReturn(provider);
		discoverer = new ModDiscoverer(mock(), mock());
		finder = new BundledModCandidateFinder(tempDir, false, "1.20.1");
		apiEntry = BundledApiRegistry.load().require("1.20.1");
	}

	@Test
	void extractsOriginalBytesAndRepairsCorruptCache() throws IOException {
		Path original = tempDir.resolve("original.jar");

		try (InputStream resource = BundledModCandidateFinder.class.getResourceAsStream("/" + apiEntry.getResource())) {
			Assertions.assertNotNull(resource);
			Files.copy(resource, original);
		}

		Path extracted = finder.extractBundledApi();
		Assertions.assertEquals(cachePath(apiEntry), extracted);
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
		discoverer.addCandidateFinder(new BundledModCandidateFinder(tempDir, true, "1.20.1"));
		List<ModCandidateImpl> discovered = discoverer.discoverMods(loader, new HashMap<>());
		Path library = LoaderUtil.normalizeExistingPath(UrlUtil.getCodeSource(MixinExtrasBootstrap.class));
		Assertions.assertTrue(discovered.stream().anyMatch(mod -> mod.hasPath() && mod.getPaths().contains(library)));
	}

	@Test
	void externalCopyWithSameFileNameAndModIdIsRejected() throws Exception {
		Path external = Files.copy(finder.extractBundledApi(), tempDir.resolve(apiEntry.getFileName()));
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

	static Stream<Arguments> missingApiCases() {
		List<Arguments> cases = new ArrayList<>();

		for (String version : Arrays.asList("1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6")) {
			cases.add(Arguments.of(version, false));
			cases.add(Arguments.of(version, true));
		}

		return cases.stream();
	}

	@ParameterizedTest
	@MethodSource("missingApiCases")
	void missingApiRejectsLegacyAndDisguisedCachesBeforeEmittingCandidates(String version, boolean requiresRemap) throws IOException {
		Path original = finder.extractBundledApi();
		Path cacheRoot = tempDir.resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve("neofabric").resolve("bundled");
		Path legacyCache = Files.copy(original, cacheRoot.resolve(apiEntry.getFileName()));
		Path disguisedCache = cacheRoot.resolve(version).resolve(apiEntry.getSha512().substring(0, 16)).resolve(apiEntry.getFileName());
		Files.createDirectories(disguisedCache.getParent());
		Files.copy(original, disguisedCache);
		List<Path> candidates = new ArrayList<>();
		BundledApiRegistry configuredFor1201 = registry(apiEntry.getResource(), apiEntry.getSha512());
		BundledModCandidateFinder missingFinder = new BundledModCandidateFinder(tempDir, requiresRemap, version, configuredFor1201);
		FormattedException error = Assertions.assertThrows(FormattedException.class,
				() -> missingFinder.findCandidates((paths, remap) -> candidates.addAll(paths)));
		Assertions.assertTrue(error.getMessage().contains(version));
		Assertions.assertTrue(error.getMessage().contains("不会回退"));
		Assertions.assertTrue(candidates.isEmpty(), "No API or MixinExtras candidate may be emitted after a missing selection");
		Assertions.assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(legacyCache));
		Assertions.assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(disguisedCache));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void missingResourceCannotReuseValidSameVersionCache(boolean requiresRemap) throws IOException {
		Path original = finder.extractBundledApi();
		String missingResource = BundledApiRegistry.RESOURCE_ROOT + "1.20.1/missing.jar";
		BundledApiRegistry registry = registry(missingResource, apiEntry.getSha512());
		Path cached = cachePath(registry.require("1.20.1"));
		Files.copy(original, cached);
		List<Path> candidates = new ArrayList<>();
		BundledModCandidateFinder missingFinder = new BundledModCandidateFinder(tempDir, requiresRemap, "1.20.1", registry);
		FormattedException error = Assertions.assertThrows(FormattedException.class,
				() -> missingFinder.findCandidates((paths, remap) -> candidates.addAll(paths)));
		Assertions.assertTrue(error.getMessage().contains("缺少 Minecraft 1.20.1 的内置资源"));
		Assertions.assertTrue(error.getMessage().contains(missingResource));
		Assertions.assertTrue(candidates.isEmpty());
		Assertions.assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(cached));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void changedChecksumCannotReusePreviouslyValidCache(boolean requiresRemap) throws IOException {
		Path original = finder.extractBundledApi();
		String wrongChecksum = String.join("", Collections.nCopies(128, "0"));
		BundledApiRegistry registry = registry(apiEntry.getResource(), wrongChecksum);
		Path cached = cachePath(registry.require("1.20.1"));
		Files.createDirectories(cached.getParent());
		Files.copy(original, cached);
		List<Path> candidates = new ArrayList<>();
		BundledModCandidateFinder invalidFinder = new BundledModCandidateFinder(tempDir, requiresRemap, "1.20.1", registry);
		FormattedException error = Assertions.assertThrows(FormattedException.class,
				() -> invalidFinder.findCandidates((paths, remap) -> candidates.addAll(paths)));
		Assertions.assertTrue(error.getMessage().contains("SHA-512 校验失败"));
		Assertions.assertTrue(candidates.isEmpty());
		Assertions.assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(cached));

		try (Stream<Path> files = Files.list(cached.getParent())) {
			Assertions.assertEquals(Collections.singletonList(cached), files.collect(Collectors.toList()));
		}
	}

	private BundledApiRegistry registry(String resource, String checksum) throws IOException {
		return BundledApiRegistry.parse(new StringReader("{\"schemaVersion\":1,\"apis\":{\"1.20.1\":{"
				+ "\"apiVersion\":\"" + apiEntry.getApiVersion() + "\",\"resource\":\"" + resource
				+ "\",\"sha512\":\"" + checksum + "\"}}}"));
	}

	private Path cachePath(BundledApiRegistry.Entry entry) {
		return tempDir.resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve("neofabric").resolve("bundled")
				.resolve(entry.getMinecraftVersion()).resolve(entry.getSha512().substring(0, 16)).resolve(entry.getFileName());
	}

	private static ModCandidateImpl find(List<ModCandidateImpl> mods, String id) {
		return mods.stream().filter(mod -> mod.getId().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing mod: " + id));
	}
}
