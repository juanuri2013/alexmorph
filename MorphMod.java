package com.example.alexmorph;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MorphMod.MODID)
public class MorphMod {
    public static final String MODID = "alexmorph";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MorphMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(new MorphEvents());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MorphNetwork::register);
    }
}
