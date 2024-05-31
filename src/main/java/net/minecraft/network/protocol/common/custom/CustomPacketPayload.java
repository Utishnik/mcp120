package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public interface CustomPacketPayload {
   void write(FriendlyByteBuf p_297598_);

   ResourceLocation id();
}