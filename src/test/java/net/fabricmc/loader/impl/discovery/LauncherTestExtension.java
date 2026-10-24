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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

class LauncherTestExtension implements BeforeEachCallback, AfterEachCallback {
	private Field launcherField;
	private FabricLauncher previousLauncher;

	@Override
	public void beforeEach(ExtensionContext context) throws ReflectiveOperationException {
		launcherField = FabricLauncherBase.class.getDeclaredField("launcher");
		launcherField.setAccessible(true);
		previousLauncher = (FabricLauncher) launcherField.get(null);
		launcherField.set(null, null);
		FabricLauncher launcher = mock();
		when(launcher.getEnvironmentType()).thenReturn(EnvType.CLIENT);
		FabricLauncherBase.setLauncher(launcher);
	}

	@Override
	public void afterEach(ExtensionContext context) throws IllegalAccessException {
		launcherField.set(null, previousLauncher);
	}
}
