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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import net.fabricmc.loader.impl.FormattedException;

class NeoFabricGameVersionTest {
	@Test
	void acceptsOnlyMinecraft1201() {
		GameProvider provider = mock();
		when(provider.getGameId()).thenReturn("minecraft");
		when(provider.getNormalizedGameVersion()).thenReturn("1.20.1");
		Assertions.assertDoesNotThrow(() -> NeoFabricGameVersion.check(provider));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"1.20", "1.20.2", "1.20.10", "1.20.1-rc.1", "1.21"})
	void rejectsOtherVersionsWithoutNullPointer(String version) {
		GameProvider provider = mock();
		when(provider.getGameId()).thenReturn("minecraft");
		when(provider.getGameName()).thenReturn("Minecraft");
		when(provider.getNormalizedGameVersion()).thenReturn(version);
		FormattedException error = Assertions.assertThrows(FormattedException.class, () -> NeoFabricGameVersion.check(provider));
		Assertions.assertTrue(error.getMessage().contains("NeoFabric 仅支持 Minecraft 1.20.1"));
	}

	@Test
	void rejectsOtherGamesWithSameVersion() {
		GameProvider provider = mock();
		when(provider.getGameId()).thenReturn("other_game");
		when(provider.getNormalizedGameVersion()).thenReturn("1.20.1");
		Assertions.assertThrows(FormattedException.class, () -> NeoFabricGameVersion.check(provider));
	}
}
