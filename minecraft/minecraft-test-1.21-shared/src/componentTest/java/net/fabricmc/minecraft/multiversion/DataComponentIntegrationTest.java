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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

class DataComponentIntegrationTest {
	@BeforeAll
	static void bootstrapComponentRegistries() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	void componentTypesLoadThroughKnotOnJava21() {
		assertEquals(21, Runtime.version().feature());
		assertSame(getClass().getClassLoader(), DataComponentTypes.class.getClassLoader());
		assertSame(getClass().getClassLoader(), NbtComponent.class.getClassLoader());
	}

	@Test
	void customNameSurvivesCopyWithoutSharingChanges() {
		ItemStack original = new ItemStack(Items.DIAMOND);
		original.set(DataComponentTypes.CUSTOM_NAME, Text.literal("NeoFabric component original"));
		ItemStack copy = original.copy();
		assertEquals("NeoFabric component original", copy.get(DataComponentTypes.CUSTOM_NAME).getString());
		copy.set(DataComponentTypes.CUSTOM_NAME, Text.literal("NeoFabric component copy"));
		assertEquals("NeoFabric component original", original.get(DataComponentTypes.CUSTOM_NAME).getString());
		assertEquals("NeoFabric component copy", copy.get(DataComponentTypes.CUSTOM_NAME).getString());
	}

	@Test
	void customNameCanBeRemovedThroughComponentApi() {
		ItemStack stack = new ItemStack(Items.DIAMOND);
		assertFalse(stack.contains(DataComponentTypes.CUSTOM_NAME));
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("NeoFabric removable component"));
		assertTrue(stack.contains(DataComponentTypes.CUSTOM_NAME));
		assertEquals("NeoFabric removable component", stack.remove(DataComponentTypes.CUSTOM_NAME).getString());
		assertFalse(stack.contains(DataComponentTypes.CUSTOM_NAME));
		assertNull(stack.get(DataComponentTypes.CUSTOM_NAME));
	}

	@Test
	void customNbtUsesCustomDataComponent() throws Exception {
		NbtCompound data = new NbtCompound();
		data.putString("neofabric-test", "component payload");
		ItemStack original = new ItemStack(Items.DIAMOND);
		original.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
		ItemStack copy = original.copy();
		NbtComponent component = copy.get(DataComponentTypes.CUSTOM_DATA);
		assertNotNull(component);
		NbtCompound snapshot = component.copyNbt();
		assertEquals("component payload", MinecraftVersionAdapter.nbtString(snapshot, "neofabric-test"));
		snapshot.putString("neofabric-test", "detached snapshot");
		assertEquals("component payload", MinecraftVersionAdapter.nbtString(original.get(DataComponentTypes.CUSTOM_DATA).copyNbt(), "neofabric-test"));
		copy.remove(DataComponentTypes.CUSTOM_DATA);
		assertNull(copy.get(DataComponentTypes.CUSTOM_DATA));
		assertNotNull(original.get(DataComponentTypes.CUSTOM_DATA));
	}
}
