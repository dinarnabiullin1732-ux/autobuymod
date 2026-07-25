Step23.10

Исправление блокировки меню при старом shalk.js:
- старые проверки mc.field_1755 != null теперь переписываются на mabCompat.isLegacyBlockingScreenOpen();
- shulker-скрипт больше не считает любое меню контейнером, который надо закрыть;
- closeCurrentScreen() закрывает только handled/container экраны, а не GUI Malfix/настройки;
- восстановление после SpookyBuy.setState(true) больше не закрывает пользовательские меню, а отменяет ожидание /ah, если открыто меню пользователя.
