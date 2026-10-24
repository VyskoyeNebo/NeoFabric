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

import net.fabricmc.loader.impl.FormattedException;

public final class NeoFabricGameVersion {
	public static final String MINECRAFT_VERSION = "1.20.1";

	public static void check(GameProvider provider) {
		if (!"minecraft".equals(provider.getGameId()) || !MINECRAFT_VERSION.equals(provider.getNormalizedGameVersion())) {
			String game = provider.getGameName();
			String version = provider.getNormalizedGameVersion();
			throw new FormattedException("不支持的游戏版本", "NeoFabric 仅支持 Minecraft %s，当前为 %s %s。",
					MINECRAFT_VERSION, game != null ? game : "未知游戏", version != null ? version : "未知版本");
		}
	}

	private NeoFabricGameVersion() {
	}
}
