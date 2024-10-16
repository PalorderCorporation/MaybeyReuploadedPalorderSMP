package com.palorder.smp;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.TextFieldWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TntEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.crafting.ShapedRecipeBuilder;
import net.minecraftforge.common.crafting.CraftingBookCategory;
import net.minecraftforge.common.crafting.IRecipeCategory;
import net.minecraftforge.common.crafting.IRecipeSerializer;
import net.minecraft.world.item.crafting.Ingredient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.commands.CommandSourceStack;
import com.mojang.brigadier.Command;
import net.minecraft.server.commands.Commands;
import net.minecraft.server.commands.EntityArgument;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.event.InputEvent;
import com.mojang.blaze3d.vertex.PoseStack;

import java.lang.*;               // Fundamental classes automatically imported
import java.util.*;              // Utility classes, including collections
import java.io.*;                 // Input and output classes
import java.net.*;                // Networking classes
import java.nio.*;                // Non-blocking I/O classes
import java.sql.*;                // Database access classes
import java.awt.*;                // GUI and graphics classes
import javax.swing.*;            // Lightweight GUI components
import java.security.*;           // Security classes
import java.time.*;               // Date and time classes
import java.beans.*;              // JavaBeans classes
import java.rmi.*;                // Remote method invocation classes
import javafx.*;                  // JavaFX classes for rich internet applications
import java.util.concurrent.*;     // Concurrent programming classes
import java.util.logging.*;        // Logging classes
import java.util.zip.*;           // ZIP and GZIP file classes
import java.util.prefs.*;         // Preference data classes
import java.lang.reflect.*;        // Reflection classes
import java.lang.annotation.*;      // Annotation classes
import javax.xml.*;                // XML processing classes
import javax.servlet.*;            // Servlet classes (Java EE)

@Mod("palordersmp")
public class PalorderSMPMain {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "palordersmp");
    public static final RegistryObject<Item> REVIVAL_ITEM = ITEMS.register("revival_item", () -> new Item(new Item.Properties()));

    private static final UUID OWNER_UUID = UUID.fromString("78d8e34d-5d1a-4b2d-85e2-f0792d9e1a6c");
    public static final KeyMapping OPEN_OWNER_PANEL_KEY = new KeyMapping(
            "key.palordersmp.open_owner_panel",
            GLFW.GLFW_KEY_O, "key.categories.palordersmp"
    );

    private final Map<UUID, Long> deathBans = new HashMap<>();
    private final Map<UUID, Boolean> immortalityToggles = new HashMap<>();

    public PalorderSMPMain() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::doClientStuff);
        modEventBus.addListener(this::onServerStarting);
        ITEMS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Custom setup code here
        });
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        net.minecraftforge.client.ClientRegistry.registerKeyBinding(OPEN_OWNER_PANEL_KEY);
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        MinecraftServer server = event.getServer();

        server.getCommands().getDispatcher().register(
            Commands.literal("nuke")
                .requires(cs -> cs.getPlayerOrException().getUUID().equals(OWNER_UUID))
                .executes(context -> {
                    spawnTNTNuke(context.getSource().getPlayerOrException());
                    return 1;
                })
        );

        // Other command registrations...
    }

    private void spawnTNTNuke(Player player) {
        ServerLevel world = (ServerLevel) player.level;

        // Get the crosshair (player's look vector) to find the target block for TNT center
        Vec3 lookVec = player.getLookAngle(); // Get the direction the player is looking
        Vec3 eyePos = player.getEyePosition(1.0f); // Get the player's eye position

        // Raytrace to find the block the player is looking at
        Vec3 targetPos = eyePos.add(lookVec.scale(100)); // Cast 100 blocks ahead
        BlockHitResult hitResult = world.clip(new ClipContext(eyePos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 hitLocation = hitResult.getLocation(); // This is the center of the TNT circle

        // Define parameters for TNT spawning
        int numTNT = 1000;
        double radius = 100.0;
        double tntHeightOffset = 50.0; // Start TNT higher up in the sky

        // Spawn TNT in a circular pattern
        for (int i = 0; i < numTNT; i++) {
            double angle = 2 * Math.PI * i / numTNT; // Spread the TNT evenly in a circle
            double xOffset = radius * Math.cos(angle); // X offset from the center
            double zOffset = radius * Math.sin(angle); // Z offset from the center

            // Calculate TNT spawn position relative to the hit location
            double tntX = hitLocation.x + xOffset;
            double tntZ = hitLocation.z + zOffset;
            double tntY = hitLocation.y + tntHeightOffset; // Spawn TNT in the sky

            // Create and configure TNT entity
            TntEntity tnt = EntityType.TNT.create(world);
            if (tnt != null) {
                tnt.setPos(tntX, tntY, tntZ); // Set the position of the TNT
                tnt.setFuse(40); // Set a 2-second fuse (40 ticks)
                world.addFreshEntity(tnt); // Add TNT to the world
            }
        }
    }

    private static class KeyInputHandler {
        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (OPEN_OWNER_PANEL_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.player.getUUID().equals(OWNER_UUID)) {
                    minecraft.setScreen(new OwnerPanelScreen());
                }
            }
        }
    }

    private static class OwnerPanelScreen extends Screen {
        private TextFieldWidget inputField;

        protected OwnerPanelScreen() {
            super(new TextComponent("Owner Panel"));
        }

        @Override
        protected void init() {
            super.init();
            inputField = new TextFieldWidget(font, width / 2 - 100, height / 2 - 10, 200, 20, new TextComponent("Enter Command"));
            inputField.setMaxLength(100);
            addRenderableWidget(inputField);

            addRenderableWidget(new Button(width / 2 - 100, height / 2 + 20, 200, 20, new TextComponent("Shutdown Server"), button -> {
                Minecraft.getInstance().setScreen(new ConfirmationDialogScreen(this));
            }));
        }

        @Override
        public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
            renderBackground(matrices);
            super.render(matrices, mouseX, mouseY, delta);
            inputField.render(matrices, mouseX, mouseY, delta);
        }
    }

    private static class ConfirmationDialogScreen extends Screen {
        private final OwnerPanelScreen parentScreen;
        private TextFieldWidget hashInputField;

        protected ConfirmationDialogScreen(OwnerPanelScreen parentScreen) {
            super(new TextComponent("Confirm Shutdown"));
            this.parentScreen = parentScreen;
        }

        @Override
        protected void init() {
            super.init();
            addRenderableWidget(new Button(width / 2 - 100, height / 2 - 10, 200, 20, new TextComponent("Yes"), button -> {
                // Check for the hash confirmation
                if (hashInputField.getValue().equals("09784fd8a67dff62b977a3218af931fff53e3ff48cff1d43272e3bcb7a74714b1580fb884a1bd15b3b943fe72eb182c38e11484e6676c23ac564dcec5f311952")) {
                    MinecraftServer server = Minecraft.getInstance().getSingleplayerServer(); // Get the server instance
                    if (server != null) {
                        try {
                            // Attempt to stop the server gracefully
                            server.getCommands().performCommand(server.createCommandSourceStack(), "stop");
                        } catch (Exception e) {
                            // If the command fails, forcefully shut down the server
                            System.out.println("Graceful shutdown failed, forcing shutdown...");
                            System.exit(0); // This will kill the process
                        }
                    }
                    Minecraft.getInstance().setScreen(parentScreen); // Go back to the owner panel
                } else {
                    // Handle incorrect hash input
                    Minecraft.getInstance().player.sendSystemMessage(new TextComponent("Invalid hash! Please try again."));
                }
            }));

            // Hash input field
            hashInputField = new TextFieldWidget(font, width / 2 - 100, height / 2 + 20, 200, 20, new TextComponent("Paste Hash Here"));
            hashInputField.setMaxLength(100);
            addRenderableWidget(hashInputField);

            addRenderableWidget(new Button(width / 2 - 100, height / 2 + 50, 200, 20, new TextComponent("No"), button -> {
                Minecraft.getInstance().setScreen(parentScreen); // Go back to the owner panel
            }));
        }

        @Override
        public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
            renderBackground(matrices);
            super.render(matrices, mouseX, mouseY, delta);
            hashInputField.render(matrices, mouseX, mouseY, delta);
        }
    }
}
