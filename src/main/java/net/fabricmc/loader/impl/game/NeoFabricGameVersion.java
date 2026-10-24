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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.fabricmc.loader.impl.FormattedException;

public final class NeoFabricGameVersion {
	public static final Set<String> SUPPORTED_VERSIONS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
			"1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6")));

	public static boolean isSupported(String version) {
		return SUPPORTED_VERSIONS.contains(version);
	}

	public static void check(GameProvider provider) {
		String version = provider != null ? provider.getNormalizedGameVersion() : null;

		if (provider == null || !"minecraft".equals(provider.getGameId()) || !isSupported(version)) {
			String game = provider != null ? provider.getGameName() : null;
			throw new FormattedException("不支持的游戏版本", "NeoFabric 仅支持 Minecraft 1.20.1 至 1.20.6（仅正式版），当前为 %s %s。",
					game != null ? game : "未知游戏", version != null ? version : "未知版本");
		}
	}

	private NeoFabricGameVersion() {
	}
}
