package ru.malfix.autobuy.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Never-style movement guard.
 *
 * While automation is active the mod must not let accidental WASD/space/shift
 * movement push the player away from the auction NPC or interfere with server GUI
 * actions. The guard is intentionally always-on and has no GUI toggle: it only
 * touches vanilla movement key bindings and the current player input object.
 */
public final class MovementLock {

    private static boolean wasLocked = false;

    private MovementLock() {
    }

    public static void tick(MinecraftClient client, boolean locked) {
        if (client == null) {
            wasLocked = false;
            return;
        }

        if (!locked) {
            if (wasLocked) {
                wasLocked = false;
                refreshPressedStates();
            }
            return;
        }

        wasLocked = true;
        releaseMovementKeyBindings(client);
        zeroCurrentPlayerInput(client);
    }

    public static boolean wasLocked() {
        return wasLocked;
    }

    private static void releaseMovementKeyBindings(MinecraftClient client) {
        if (client.options == null) {
            return;
        }

        // Yarn names changed between 1.16.x and 1.21.x. Use reflection so the
        // movement lock compiles and keeps working across both naming layouts.
        releaseOptionKey(client.options, "forwardKey", "keyForward");
        releaseOptionKey(client.options, "backKey", "keyBack");
        releaseOptionKey(client.options, "leftKey", "keyLeft");
        releaseOptionKey(client.options, "rightKey", "keyRight");
        releaseOptionKey(client.options, "jumpKey", "keyJump");
        releaseOptionKey(client.options, "sneakKey", "keySneak");
        releaseOptionKey(client.options, "sprintKey", "keySprint");
    }

    private static void releaseOptionKey(Object options, String modernName, String legacyName) {
        release(readKeyBinding(options, modernName));
        release(readKeyBinding(options, legacyName));
    }

    private static KeyBinding readKeyBinding(Object options, String fieldName) {
        if (options == null || fieldName == null) {
            return null;
        }
        try {
            java.lang.reflect.Field field;
            try {
                field = options.getClass().getField(fieldName);
            } catch (NoSuchFieldException ignored) {
                field = options.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
            }
            Object value = field.get(options);
            return value instanceof KeyBinding ? (KeyBinding) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void release(KeyBinding keyBinding) {
        if (keyBinding != null) {
            keyBinding.setPressed(false);
        }
    }

    private static void zeroCurrentPlayerInput(MinecraftClient client) {
        if (client.player == null || client.player.input == null) {
            return;
        }

        Object input = client.player.input;
        setFloat(input, "movementForward", 0.0F);
        setFloat(input, "movementSideways", 0.0F);
        setBoolean(input, "pressingForward", false);
        setBoolean(input, "pressingBack", false);
        setBoolean(input, "pressingLeft", false);
        setBoolean(input, "pressingRight", false);
        setBoolean(input, "jumping", false);
        setBoolean(input, "sneaking", false);
        try {
            client.player.setSprinting(false);
        } catch (Throwable ignored) {
        }
    }

    private static void setFloat(Object target, String fieldName, float value) {
        try {
            java.lang.reflect.Field field = target.getClass().getField(fieldName);
            field.setFloat(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setBoolean(Object target, String fieldName, boolean value) {
        try {
            java.lang.reflect.Field field = target.getClass().getField(fieldName);
            field.setBoolean(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshPressedStates() {
        try {
            KeyBinding.updatePressedStates();
        } catch (Throwable ignored) {
            // Some mappings/loader combinations may not expose the static refresh
            // method. The lock still works; the player may only need to tap a key
            // once after automation ends if the method is unavailable.
        }
    }
}
