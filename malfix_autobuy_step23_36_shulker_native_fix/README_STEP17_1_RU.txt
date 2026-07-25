Malfix AutoBuy Step 17.1 — sellreal chat screen fix

Исправлено:
- .mab sellreal больше не блокируется, когда команда вводится из обычного чата.
- Раньше runtime видел экран чата как net.minecraft.class_408 и ошибочно считал его открытым GUI.
- Теперь ChatScreen/class_408 разрешён, но другие GUI всё ещё блокируют sellreal.

Проверка:
1. Возьми предмет в руку.
2. Укажи цену продажи в нижнем поле GUI.
3. .mab selltest — handPlan должен быть READY_IN_HAND.
4. .mab sellreal — должен отправить /ah sell <цена>.
