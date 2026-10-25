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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

class MappingResolverTest {
	private final MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();

	@Test
	void namespaces() {
		assertIterableEquals(List.of("named", "official", "intermediary"), mappings.getNamespaces());
		assertEquals("named", mappings.getCurrentRuntimeNamespace());
	}

	@Test
	void classNames() {
		assertEquals("net.minecraft.util.math.Vec3i", mappings.mapClassName("intermediary", "net.minecraft.class_2382"));
		assertEquals("net.minecraft.util.math.BlockPos", mappings.mapClassName("intermediary", "net.minecraft.class_2338"));
		assertEquals("net.minecraft.Unknown", mappings.mapClassName("intermediary", "net.minecraft.Unknown"));
	}

	@Test
	void reverseClassName() {
		assertEquals("net.minecraft.class_2382", mappings.unmapClassName("intermediary", "net.minecraft.util.math.Vec3i"));
	}

	@Test
	void fieldName() {
		assertEquals("x", mappings.mapFieldName("intermediary", "net.minecraft.class_2382", "field_11175", "I"));
	}

	@Test
	void methodName() {
		assertEquals("getX", mappings.mapMethodName("intermediary", "net.minecraft.class_2382", "method_10263", "()I"));
	}
}
