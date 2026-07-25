Step 22.7 — FIX ASM / Nashorn crash

Исправлено падение при запуске:
loader constraint violation: org.objectweb.asm.tree.ClassNode

Причина:
В Step 22.6 в мод были добавлены Nashorn + отдельные org.ow2.asm jar.
Fabric/Mixin уже использует ASM, поэтому второй ASM в моде ломал загрузку Minecraft ещё до главного меню.

Что изменено:
- удалены отдельные зависимости org.ow2.asm из build.gradle;
- Nashorn оставлен без transitive ASM-зависимостей;
- папка scripts и bridge под shalk.js сохранены;
- FullAuto/AntiAFK/FPS/tooltip/highlight не трогались.

Важно:
Перед проверкой удали старый malfix_autobuy_step22_6*.jar из папки mods.
Если старый jar останется рядом с новым, краш может сохраниться.

Скрипты:
.minecraft/malfix_autobuy/scripts/
Команды:
.mab scripts status
.mab scripts reload
.mab scripts dir
