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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.loader.impl.game.NeoFabricGameVersion;
import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;

public final class BundledApiRegistry {
	public static final String RESOURCE_ROOT = "META-INF/neofabric/apis/";
	public static final String INDEX_RESOURCE = RESOURCE_ROOT + "index.json";
	private final Map<String, Entry> entries;

	private BundledApiRegistry(Map<String, Entry> entries) {
		this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
	}

	public static BundledApiRegistry load() throws IOException {
		try (InputStream input = BundledApiRegistry.class.getResourceAsStream("/" + INDEX_RESOURCE)) {
			if (input == null) throw new IOException("缺少内置 NeoFabric API 索引：" + INDEX_RESOURCE);
			return parse(new InputStreamReader(input, StandardCharsets.UTF_8));
		}
	}

	static BundledApiRegistry parse(Reader input) throws IOException {
		try (JsonReader reader = new JsonReader(input)) {
			Map<String, Entry> entries = new LinkedHashMap<>();
			boolean foundSchema = false;
			boolean foundApis = false;
			reader.beginObject();

			while (reader.hasNext()) {
				String name = reader.nextName();

				switch (name) {
				case "schemaVersion":
					if (foundSchema || reader.peek() != JsonToken.NUMBER || reader.nextInt() != 1) {
						throw new IOException("NeoFabric API 索引 schemaVersion 必须为 1，且不能重复");
					}

					foundSchema = true;
					break;
				case "apis":
					if (foundApis) throw new IOException("NeoFabric API 索引存在重复的 apis 字段");
					foundApis = true;
					reader.beginObject();

					while (reader.hasNext()) {
						String minecraftVersion = reader.nextName();
						if (entries.containsKey(minecraftVersion)) throw new IOException("重复的 API 版本记录：" + minecraftVersion);
						entries.put(minecraftVersion, readEntry(reader, minecraftVersion));
					}

					reader.endObject();
					break;
				default:
					throw new IOException("NeoFabric API 索引存在未知字段：" + name);
				}
			}

			reader.endObject();

			if (!foundSchema || !foundApis || reader.peek() != JsonToken.END_DOCUMENT) {
				throw new IOException("NeoFabric API 索引缺少必要字段或包含多余数据");
			}

			return new BundledApiRegistry(entries);
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw new IOException("无效的 NeoFabric API 索引：" + e.getMessage(), e);
		}
	}

	private static Entry readEntry(JsonReader reader, String minecraftVersion) throws IOException {
		Map<String, String> fields = new LinkedHashMap<>();
		reader.beginObject();

		while (reader.hasNext()) {
			String field = reader.nextName();

			if (!"apiVersion".equals(field) && !"resource".equals(field) && !"sha512".equals(field) && !"source".equals(field)) {
				throw new IOException("Minecraft " + minecraftVersion + " 的 API 索引存在未知字段：" + field);
			}

			if (fields.containsKey(field) || reader.peek() != JsonToken.STRING) {
				throw new IOException("Minecraft " + minecraftVersion + " 的 API 字段重复或不是字符串：" + field);
			}

			fields.put(field, reader.nextString());
		}

		reader.endObject();
		return new Entry(minecraftVersion, fields.get("apiVersion"), fields.get("resource"), fields.get("sha512"));
	}

	public Entry require(String minecraftVersion) throws IOException {
		if (!NeoFabricGameVersion.isSupported(minecraftVersion)) {
			throw new IOException("不支持的 Minecraft 版本：" + minecraftVersion);
		}

		Entry entry = entries.get(minecraftVersion);

		if (entry == null) {
			throw new IOException("Minecraft " + minecraftVersion + " 尚未内置对应版本的 NeoFabric API，已停止加载；不会回退使用其他版本的 API。");
		}

		return entry;
	}

	public Map<String, Entry> getEntries() {
		return entries;
	}

	public static final class Entry {
		private final String minecraftVersion;
		private final String apiVersion;
		private final String resource;
		private final String sha512;

		Entry(String minecraftVersion, String apiVersion, String resource, String sha512) {
			if (!NeoFabricGameVersion.isSupported(minecraftVersion)) {
				throw new IllegalArgumentException("API 记录的 Minecraft 版本不在白名单中：" + minecraftVersion);
			}

			String prefix = RESOURCE_ROOT + minecraftVersion + "/";

			if (resource == null || !resource.startsWith(prefix) || !resource.substring(prefix.length()).matches("[A-Za-z0-9._+\\-]+\\.jar")) {
				throw new IllegalArgumentException("API 资源必须位于当前 Minecraft 版本目录：" + prefix);
			}

			if (apiVersion == null || apiVersion.trim().isEmpty() || sha512 == null || !sha512.matches("[0-9a-f]{128}")) {
				throw new IllegalArgumentException("Minecraft " + minecraftVersion + " 的 API 版本或 SHA-512 无效");
			}

			this.minecraftVersion = minecraftVersion;
			this.apiVersion = apiVersion;
			this.resource = resource;
			this.sha512 = sha512;
		}

		public String getMinecraftVersion() {
			return minecraftVersion;
		}

		public String getApiVersion() {
			return apiVersion;
		}

		public String getResource() {
			return resource;
		}

		public String getFileName() {
			return resource.substring(resource.lastIndexOf('/') + 1);
		}

		public String getSha512() {
			return sha512;
		}
	}
}
