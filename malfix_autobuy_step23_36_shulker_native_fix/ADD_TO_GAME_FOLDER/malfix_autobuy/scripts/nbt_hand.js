// Malfix NBT helper for 1.21.4
// F8 -> runs local .nbt command. The native command writes item id/name/count/components
// to latest.log and .minecraft/malfix_autobuy/nbt_dumps/.

var MinecraftClient = Java.type("net.minecraft.class_310");
var GLFW = Java.type("org.lwjgl.glfw.GLFW");

var mc = MinecraftClient.method_1551();
var KEY_DUMP_NBT = GLFW.GLFW_KEY_F8;
var keyWasDown = false;

function log(msg) {
    try { print.accept(String(msg)); } catch (e) {}
}

function getWindowHandle() {
    try { return mc.method_22683().method_4490(); } catch (e) { return 0; }
}

function sendLocalNbtCommand() {
    try {
        chat.accept(".nbt");
    } catch (e) {
        log("NBT helper failed to run .nbt: " + e);
    }
}

on.accept("ru.nedan.neverapi.event.impl.EventPlayerTick", function (e) {
    try {
        if (mc == null || mc.field_1724 == null) return;
        var down = GLFW.glfwGetKey(getWindowHandle(), KEY_DUMP_NBT) == GLFW.GLFW_PRESS;
        if (down && !keyWasDown) {
            sendLocalNbtCommand();
        }
        keyWasDown = down;
    } catch (err) {
        log("NBT helper tick error: " + err);
    }
});
