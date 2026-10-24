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
	static final String API_FILE_NAME = "NeoFabric-API-0.92.12+1.20.1.jar";
	static final String API_RESOURCE = "/META-INF/neofabric/" + API_FILE_NAME;
	// Original release: https://modrinth.com/mod/fabric-api/version/rvI2dfzR
	private static final String API_SHA512 = "de9da2344043b3e9ef9fb7dbfff5f2577b06000c4f73f2a37959a80123141169084"
			+ "cece19133bd299b45feaaa4648bdaca2ea085684888cc8c55cc9134a477ca";

	private final Path cacheDir;
	private final boolean requiresRemap;

	public BundledModCandidateFinder(Path gameDir, boolean requiresRemap) {
		this.cacheDir = gameDir.resolve(FabricLoaderImpl.CACHE_DIR_NAME).resolve("neofabric").resolve("bundled");
		this.requiresRemap = requiresRemap;
	}

	@Override
	public void findCandidates(ModCandidateConsumer out) {
		try {
			Path api = extractBundledApi();
			Log.info(LogCategory.DISCOVERY, "加载内置 NeoFabric API：%s", API_FILE_NAME);
			out.accept(LoaderUtil.normalizeExistingPath(api), requiresRemap);

			if (requiresRemap) {
				// Development runs use the same Loader dependency from the classpath instead of its nested jar.
				Path mixinExtras = UrlUtil.getCodeSource(MixinExtrasBootstrap.class);
				if (mixinExtras == null) throw new IOException("无法定位内置 MixinExtras 开发依赖");
				out.accept(LoaderUtil.normalizeExistingPath(mixinExtras), false);
			}
		} catch (IOException e) {
			throw new FormattedException("NeoFabric API 加载失败", "无法释放或校验内置 NeoFabric API：" + e.getMessage(), e);
		}
	}

	Path extractBundledApi() throws IOException {
		Files.createDirectories(cacheDir);
		Path target = cacheDir.resolve(API_FILE_NAME);

		if (Files.isRegularFile(target) && matchesChecksum(target)) {
			return target;
		}

		Path temporary = Files.createTempFile(cacheDir, "neofabric-api-", ".tmp");

		try {
			try (InputStream resource = BundledModCandidateFinder.class.getResourceAsStream(API_RESOURCE)) {
				if (resource == null) throw new IOException("缺少内置资源 " + API_RESOURCE);
				Files.copy(resource, temporary, StandardCopyOption.REPLACE_EXISTING);
			}

			if (!matchesChecksum(temporary)) {
				throw new IOException("内置 NeoFabric API 的 SHA-512 校验失败");
			}

			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			return target;
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static boolean matchesChecksum(Path path) throws IOException {
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

		return API_SHA512.equals(hex.toString());
	}
}
