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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public final class BundledModCandidateFinder implements ModCandidateFinder {
	private final Path cacheRoot;
	private final boolean requiresRemap;
	private final String minecraftVersion;
	private final BundledApiRegistry registry;

	public BundledModCandidateFinder(Path gameDir, boolean requiresRemap, String minecraftVersion) {
		this(gameDir, requiresRemap, minecraftVersion, null);
	}

	BundledModCandidateFinder(Path gameDir, boolean requiresRemap, String minecraftVersion, BundledApiRegistry registry) {
		this.cacheRoot = gameDir.resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve("neofabric").resolve("bundled");
		this.requiresRemap = requiresRemap;
		this.minecraftVersion = minecraftVersion;
		this.registry = registry;
	}

	@Override
	public void findCandidates(ModCandidateConsumer out) {
		try {
			BundledApiRegistry.Entry entry = selectedApi();
			Path api = extractBundledApi(entry);
			Log.info(LogCategory.DISCOVERY, "加载 Minecraft %s 的内置 NeoFabric API：%s", minecraftVersion, entry.getFileName());
			out.accept(LoaderUtil.normalizeExistingPath(api), requiresRemap);

			if (requiresRemap) {
				// Development runs use the same Loader dependency from the classpath instead of its nested jar.
				Path mixinExtras = UrlUtil.getCodeSource(MixinExtrasBootstrap.class);
				if (mixinExtras == null) throw new IOException("无法定位内置 MixinExtras 开发依赖");
				out.accept(LoaderUtil.normalizeExistingPath(mixinExtras), false);
			}
		} catch (IOException e) {
			throw new FormattedException("NeoFabric API 加载失败", "Minecraft " + minecraftVersion + " 的内置 NeoFabric API 无法加载：" + e.getMessage(), e);
		}
	}

	Path extractBundledApi() throws IOException {
		return extractBundledApi(selectedApi());
	}

	private BundledApiRegistry.Entry selectedApi() throws IOException {
		return (registry != null ? registry : BundledApiRegistry.load()).require(minecraftVersion);
	}

	private Path extractBundledApi(BundledApiRegistry.Entry entry) throws IOException {
		// Require the selected resource even when a previous run left a valid cache file.
		try (InputStream resource = BundledModCandidateFinder.class.getResourceAsStream("/" + entry.getResource())) {
			if (resource == null) throw new IOException("缺少 Minecraft " + minecraftVersion + " 的内置资源 " + entry.getResource());
			Path cacheDir = cacheRoot.resolve(minecraftVersion).resolve(entry.getSha512().substring(0, 16));
			Files.createDirectories(cacheDir);
			Path target = cacheDir.resolve(entry.getFileName());

			if (Files.isRegularFile(target) && matchesChecksum(target, entry.getSha512())) {
				return target;
			}

			Path temporary = Files.createTempFile(cacheDir, "neofabric-api-", ".tmp");

			try {
				Files.copy(resource, temporary, StandardCopyOption.REPLACE_EXISTING);

				if (!matchesChecksum(temporary, entry.getSha512())) {
					throw new IOException("Minecraft " + minecraftVersion + " 的内置 NeoFabric API 的 SHA-512 校验失败");
				}

				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				return target;
			} finally {
				Files.deleteIfExists(temporary);
			}
		}
	}

	private static boolean matchesChecksum(Path path, String expected) throws IOException {
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance("SHA-512");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-512 is unavailable", e);
		}

		try (InputStream input = Files.newInputStream(path)) {
			byte[] buffer = new byte[8192];
			int count;

			while ((count = input.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
			}
		}

		StringBuilder hex = new StringBuilder(128);

		for (byte value : digest.digest()) {
			hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
			hex.append(Character.forDigit(value & 0xf, 16));
		}

		return expected.equals(hex.toString());
	}
}
