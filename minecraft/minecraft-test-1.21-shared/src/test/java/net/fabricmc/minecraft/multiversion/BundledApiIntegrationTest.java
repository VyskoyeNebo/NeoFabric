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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3i;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.impl.discovery.BundledApiRegistry;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;

class BundledApiIntegrationTest {
	private static final String MINECRAFT_VERSION = System.getProperty("neofabric.test.minecraftVersion");
	private static final String API_VERSION = System.getProperty("neofabric.test.apiVersion");
	private static final FabricLoader LOADER = FabricLoader.getInstance();

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	void actualJavaRuntime() {
		int expectedJavaVersion = Integer.parseInt(System.getProperty("neofabric.test.javaVersion"));
		assertEquals(expectedJavaVersion, Runtime.version().feature());
	}

	@Test
	void actualMinecraftVersion() throws Exception {
		assertNotNull(MINECRAFT_VERSION);
		assertEquals(MINECRAFT_VERSION, MinecraftVersionAdapter.gameVersionName());
		assertEquals(MINECRAFT_VERSION, LOADER.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
	}

	@Test
	void exactBundledApiSelection() throws Exception {
		BundledApiRegistry.Entry expected = BundledApiRegistry.load().require(MINECRAFT_VERSION);
		ModContainer api = LOADER.getModContainer("fabric-api").orElseThrow();
		assertEquals(API_VERSION, expected.getApiVersion());
		assertEquals(API_VERSION, api.getMetadata().getVersion().getFriendlyString());
		assertEquals(ModOrigin.Kind.PATH, api.getOrigin().getKind());
		List<Path> origins = api.getOrigin().getPaths();
		assertEquals(1, origins.size());
		Path origin = origins.get(0);
		assertEquals(expected.getFileName(), origin.getFileName().toString());
		assertEquals(expected.getSha512().substring(0, 16), origin.getParent().getFileName().toString());
		assertEquals(MINECRAFT_VERSION, origin.getParent().getParent().getFileName().toString());
		assertFalse(api.getMetadata().containsCustomValue("neofabric"));
	}

	@ParameterizedTest(name = "{0} resolves from the selected bundled API")
	@ValueSource(strings = { "fabric-api-base", "fabric-lifecycle-events-v1", "fabric-resource-loader-v0", "fabric-registry-sync-v0" })
	void nestedApiModulesResolve(String modId) {
		ModContainer module = LOADER.getModContainer(modId).orElseThrow();
		assertEquals(ModOrigin.Kind.NESTED, module.getOrigin().getKind());
		assertEquals("fabric-api", module.getOrigin().getParentModId());
		assertEquals("fabric-api", module.getContainingMod().orElseThrow().getMetadata().getId());
		assertFalse(module.getMetadata().containsCustomValue("neofabric"));
	}

	@Test
	void nestedModuleContainersAreConsistent() {
		ModContainer api = LOADER.getModContainer("fabric-api").orElseThrow();
		assertTrue(api.getContainedMods().size() > 20);
		api.getContainedMods().forEach(module -> {
			assertSame(api, module.getContainingMod().orElseThrow());
			assertSame(module, LOADER.getModContainer(module.getMetadata().getId()).orElseThrow());
		});
	}

	static Stream<String> apiClassNames() {
		return Stream.of("net.fabricmc.fabric.api.event.EventFactory",
				"net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents", MinecraftVersionAdapter.resourceLoaderClass());
	}

	@Test
	void resourceLoaderModuleMatchesMinecraftGeneration() {
		String module = MinecraftVersionAdapter.minorVersion() >= 9 ? "fabric-resource-loader-v1" : "fabric-resource-loader-v0";
		assertEquals("fabric-api", LOADER.getModContainer(module).orElseThrow().getContainingMod().orElseThrow().getMetadata().getId());
	}

	@ParameterizedTest(name = "{0} loads through Knot")
	@MethodSource("apiClassNames")
	void apiClassesLoadThroughKnot(String className) throws Exception {
		Class<?> apiClass = Class.forName(className, true, getClass().getClassLoader());
		assertSame(getClass().getClassLoader(), apiClass.getClassLoader());
	}

	@Test
	void fabricApiEventDispatchWorks() throws Exception {
		Class<?> factory = Class.forName("net.fabricmc.fabric.api.event.EventFactory");
		Class<?> eventType = Class.forName("net.fabricmc.fabric.api.event.Event");
		Function<Runnable[], Runnable> invokerFactory = callbacks -> () -> {
			for (Runnable callback : callbacks) {
				callback.run();
			}
		};
		Object event = factory.getMethod("createArrayBacked", Class.class, Function.class).invoke(null, Runnable.class, invokerFactory);
		AtomicInteger invocations = new AtomicInteger();
		eventType.getMethod("register", Object.class).invoke(event, (Runnable) invocations::incrementAndGet);
		((Runnable) eventType.getMethod("invoker").invoke(event)).run();
		assertEquals(1, invocations.get());
	}

	@Test
	void fabricApiMainEntrypointsInstantiate() throws Exception {
		List<EntrypointContainer<ModInitializer>> entries = LOADER.getEntrypointContainers("main", ModInitializer.class);
		int apiEntries = 0;

		for (EntrypointContainer<ModInitializer> entry : entries) {
			if (!entry.getProvider().getContainingMod().map(parent -> parent.getMetadata().getId().equals("fabric-api")).orElse(false)) continue;
			apiEntries++;
			LoaderModMetadata metadata = (LoaderModMetadata) entry.getProvider().getMetadata();
			EntrypointMetadata declaration = metadata.getEntrypoints("main").stream()
					.filter(candidate -> candidate.getValue().equals(entry.getDefinition())).findFirst().orElseThrow();
			assertEquals("default", declaration.getAdapter());
			ModInitializer initializer = entry.getEntrypoint();
			assertNotNull(initializer);
			String[] selector = declaration.getValue().split("::", -1);
			assertTrue(selector.length == 1 || selector.length == 2);
			Class<?> implementation = Class.forName(selector[0], true, getClass().getClassLoader());
			assertSame(getClass().getClassLoader(), implementation.getClassLoader());

			if (selector.length == 1) {
				assertSame(implementation, initializer.getClass());
			} else {
				// DefaultLanguageAdapter wraps Class::method selectors in a JDK proxy; the target class must still use Knot.
				Method declaredMethod = implementation.getDeclaredMethod(selector[1]);
				assertTrue(Modifier.isStatic(declaredMethod.getModifiers()));
				assertEquals(void.class, declaredMethod.getReturnType());
				assertTrue(MethodHandleProxies.isWrapperInstance(initializer));
				assertSame(ModInitializer.class, MethodHandleProxies.wrapperInstanceType(initializer));
				MethodHandle target = MethodHandleProxies.wrapperInstanceTarget(initializer);
				assertEquals(MethodType.methodType(void.class), target.type());
				MethodHandleInfo targetInfo = MethodHandles.lookup().revealDirect(target);
				assertSame(implementation, targetInfo.getDeclaringClass());
				assertEquals(declaredMethod.getName(), targetInfo.getName());
			}
		}

		assertTrue(apiEntries > 0);
	}

	@Test
	void customModEntrypointInvokes() {
		ModInitializer entrypoint = LOADER.getEntrypointContainers("main", ModInitializer.class).stream()
				.filter(entry -> entry.getProvider().getMetadata().getId().equals("neofabric-multiversion-test"))
				.findFirst().orElseThrow().getEntrypoint();
		int previous = TestEntrypoint.getInitializationCount();
		entrypoint.onInitialize();
		assertEquals(previous + 1, TestEntrypoint.getInitializationCount());
	}

	@Test
	void minecraftRegistryBootstraps() {
		assertEquals("minecraft:diamond", Registries.ITEM.getId(Items.DIAMOND).toString());
	}

	@Test
	void mixinTransformsMinecraft() {
		assertEquals(1234567, new Vec3i(-1234567, 0, 0).getX());
		assertEquals(17, new Vec3i(17, 0, 0).getX());
	}

	@Test
	void mixinExtrasTransformsMinecraft() {
		assertEquals(7654321, new Vec3i(0, -1234567, 0).getY());
		assertEquals(17, new Vec3i(0, 17, 0).getY());
	}
}
