Malfix AutoBuy — step23_01 preview порт под Minecraft 1.21.4

База:
- взят рабочий step22_86_never_movement_lock как актуальный исходник;
- проект поднят на Minecraft 1.21.4 / Yarn 1.21.4+build.8 / Java 21;
- это отдельный портовый архив, рабочую 1.16.5-ветку он не заменяет.

Что уже перенесено:
- build.gradle / gradle.properties / fabric.mod.json под 1.21.4;
- чтение ItemStack через Data Components вместо старого getTag();
- строгая логика распознавания сфер/талисманов сохранена через componentString;
- tooltip чтение переведено на 1.21.4 API;
- clickSlot переведён на ClientPlayerInteractionManager.clickSlot;
- команды /ah и другие отправки переведены на networkHandler.sendChatCommand/sendChatMessage;
- GUI переведён с MatrixStack на DrawContext;
- иконки player_head в GUI используют DataComponentTypes.PROFILE;
- Never-style movement lock оставлен;
- outgoing/incoming chat mixin адаптирован под ClientPlayNetworkHandler.

Важно:
- этот архив является первым портовым проходом. В среде ChatGPT не было локального Gradle/Minecraft dependency cache, поэтому полный Gradle build здесь не прогнан;
- если сборка у тебя выдаст ошибки, отправь полный лог compileJava. Следующим шагом надо добить именно compile errors под твои реальные Yarn/Fabric зависимости;
- после успешной сборки нужно отдельно тестировать: открытие /ah, скан слотов, покупку, продажу, расстакивание, sphere/talisman recognition и movement lock.
