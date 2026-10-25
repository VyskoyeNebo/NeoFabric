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

package net.fabricmc.minecraft.multiversion.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.math.Vec3i;

@Mixin(Vec3i.class)
public class MixinVec3i {
	@Inject(method = "getX", at = @At("RETURN"), cancellable = true)
	private void testMixin(CallbackInfoReturnable<Integer> result) {
		if (result.getReturnValueI() == -1234567) result.setReturnValue(1234567);
	}

	@ModifyReturnValue(method = "getY", at = @At("RETURN"))
	private int testMixinExtras(int original) {
		return original == -1234567 ? 7654321 : original;
	}
}
