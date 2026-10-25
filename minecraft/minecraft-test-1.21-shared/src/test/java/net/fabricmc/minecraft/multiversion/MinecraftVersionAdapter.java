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

package net.fabricmc.minecraft.multiversion;

import net.minecraft.GameVersion;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;

// These explicit boundaries come from the official Yarn mappings, rather than exception-driven fallbacks.
final class MinecraftVersionAdapter {
	static int minorVersion() {
		String version = System.getProperty("neofabric.test.minecraftVersion");
		return version.equals("1.21") ? 0 : Integer.parseInt(version.substring("1.21.".length()));
	}

	static String gameVersionName() throws ReflectiveOperationException {
		String method = minorVersion() >= 6 ? "name" : "getName";
		return (String) GameVersion.class.getMethod(method).invoke(SharedConstants.getGameVersion());
	}

	static String nbtString(NbtCompound compound, String key) throws ReflectiveOperationException {
		if (minorVersion() >= 5) {
			return (String) NbtCompound.class.getMethod("getString", String.class, String.class).invoke(compound, key, "");
		}

		return (String) NbtCompound.class.getMethod("getString", String.class).invoke(compound, key);
	}

	static String resourceLoaderClass() {
		return minorVersion() >= 9 ? "net.fabricmc.fabric.api.resource.v1.ResourceLoader" : "net.fabricmc.fabric.api.resource.ResourceManagerHelper";
	}

	private MinecraftVersionAdapter() {
	}
}
