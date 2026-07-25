Step 23.02 — 1.21.4 compile fix

Исправлены ошибки первого портового прохода:
- ClientPlayerInteractionManager.interactItem теперь вызывается как interactItem(player, Hand).
- MovementLock больше не обращается напрямую к старым GameOptions.keyForward/keyBack/... и использует reflection для forwardKey/backKey/leftKey/rightKey/jumpKey/sneakKey/sprintKey.
- GUI Screen методы обновлены под 1.21.4: onClose -> close, isPauseScreen -> shouldPause.
- TargetsConfigScreen.mouseScrolled обновлён до сигнатуры mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount).
- Potato entity render mixin переписан с устаревшего Redirect на HEAD Inject, чтобы не вызывать старую EntityRenderer.render(Entity, ...).
- Убран устаревший renderEntity hook из block entity potato mixin.
