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

package net.fabricmc.loader.impl.game;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.lib.gson.JsonReader;
import net.fabricmc.loader.impl.lib.gson.JsonToken;

class NeoFabricGameVersionTest {
	@ParameterizedTest
	@ValueSource(strings = {"1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11"})
	void acceptsSupportedMinecraftVersions(String version) {
		GameProvider provider = mock();
		when(provider.getGameId()).thenReturn("minecraft");
		when(provider.getNormalizedGameVersion()).thenReturn(version);
		Assertions.assertTrue(NeoFabricGameVersion.isSupported(version));
		Assertions.assertDoesNotThrow(() -> NeoFabricGameVersion.check(provider));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"1.19.4", "1.20", "1.20.0", "1.20.7", "1.20.10", "1.20.1-rc.1", "1.20.5-pre.1", "24w14a", "1.21.0", "1.21.12", "1.21.11-rc.1", "1.22"})
	void rejectsOtherVersionsWithoutNullPointer(String version) {
		GameProvider provider = mock();
		when(provider.getGameId()).thenReturn("minecraft");
		when(provider.getGameName()).thenReturn("Minecraft");
		when(provider.getNormalizedGameVersion()).thenReturn(version);
		Assertions.assertFalse(NeoFabricGameVersion.isSupported(version));
		FormattedException error = Assertions.assertThrows(FormattedException.class, () -> NeoFabricGameVersion.check(provider));
		Assertions.assertTrue(error.getMessage().contains("NeoFabric 仅支持 Minecraft 1.20.1–1.20.6、1.21–1.21.11（仅白名单中的正式版）"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"other_game", "Minecraft"})
	void rejectsOtherGamesWithSameVersion(String gameId) {
		GameProvider provider = mock();
		when(provider.getGameId()).thenReturn(gameId);
		when(provider.getNormalizedGameVersion()).thenReturn("1.20.1");
		Assertions.assertThrows(FormattedException.class, () -> NeoFabricGameVersion.check(provider));
	}

	@Test
	void rejectsMissingProviderWithoutNullPointer() {
		FormattedException error = Assertions.assertThrows(FormattedException.class, () -> NeoFabricGameVersion.check(null));
		Assertions.assertTrue(error.getMessage().contains("未知游戏 未知版本"));
	}

	@Test
	void supportedVersionsCannotBeChanged() {
		Assertions.assertThrows(UnsupportedOperationException.class, () -> NeoFabricGameVersion.SUPPORTED_VERSIONS.add("1.22"));
	}

	@Test
	void metadataMatchesSupportedVersions() throws IOException {
		Enumeration<URL> resources = getClass().getClassLoader().getResources("fabric.mod.json");
		List<String> loaderVersions = null;
		int loaderMetadataCount = 0;

		while (resources.hasMoreElements()) {
			URL resource = resources.nextElement();
			String modId = null;
			List<String> metadataVersions = new ArrayList<>();

			try (JsonReader reader = new JsonReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
				reader.beginObject();

				while (reader.hasNext()) {
					String name = reader.nextName();

					if ("id".equals(name)) {
						modId = reader.nextString();
						continue;
					} else if (!"depends".equals(name)) {
						reader.skipValue();
						continue;
					}

					reader.beginObject();

					while (reader.hasNext()) {
						if (!"minecraft".equals(reader.nextName())) {
							reader.skipValue();
							continue;
						}

						if (reader.peek() == JsonToken.STRING) {
							metadataVersions.add(reader.nextString());
						} else {
							reader.beginArray();

							while (reader.hasNext()) {
								metadataVersions.add(reader.nextString());
							}

							reader.endArray();
						}
					}

					reader.endObject();
				}

				reader.endObject();
			}

			if ("fabricloader".equals(modId)) {
				loaderMetadataCount++;
				loaderVersions = metadataVersions;
			}
		}

		Assertions.assertEquals(1, loaderMetadataCount, "Expected exactly one fabricloader metadata resource");
		Assertions.assertEquals(new ArrayList<>(NeoFabricGameVersion.SUPPORTED_VERSIONS), loaderVersions);
	}
}
