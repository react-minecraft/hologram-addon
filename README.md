<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=782DFF&background=FFFFFF00&center=true&vCenter=true&width=700&lines=Hologram+Addon" alt="Hologram Addon"/></a>

<p>Голограммы со статистикой проверок React</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

<p>
  <a href="https://github.com/react-minecraft/hologram-addon/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/hologram-addon/build.yml?style=plastic&logo=github&logoColor=white&branch=master&label=build" alt="Build"/></a>
  <a href="https://github.com/react-minecraft/hologram-addon/releases/tag/nightly"><img src="https://img.shields.io/badge/Nightly-EB2544?style=plastic&logo=github&logoColor=white" alt="Nightly"/></a>
  <a href="https://github.com/react-minecraft/hologram-addon/blob/master/LICENSE"><img src="https://img.shields.io/github/license/react-minecraft/hologram-addon?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License"/></a>
</p>

</div>

## Возможности

Аддон показывает над игроком результаты проверок, включая среднюю и максимальную вероятность и последние результаты. Для клиентов Minecraft 1.19.4 и новее используется Text Display, для более старых клиентов используются стойки для брони.

| Команда или действие | Право |
|---|---|
| Просмотр голограмм | `react.hologram.view` |
| `/react hologram reload` | `react.hologram.reload` |
| `/react hologram resync [игроки]` | `react.hologram.resync` |

В `main-config.yml` ключ `settings.text.lines` задаёт строки, `settings.text-display` задаёт параметры Text Display, а `settings.armor-stand` задаёт параметры стоек для брони.


## Установка

1. Возьмите `hologram-addon-*.jar` со страницы [Nightly](https://github.com/react-minecraft/hologram-addon/releases/tag/nightly) или соберите его из исходников.
2. Положите JAR в `plugins/React/addons/`. Нужен React с API 2.
3. Перезапустите сервер или выполните `/react reboot addons`. Настройки появятся в `plugins/React/addons/ReactHologramAddon/`.

React также может установить отсутствующий аддон из стабильного канала, когда доступен подписанный релиз для API 2.


## Сборка

Для сборки используйте JDK 21. Выполните `./gradlew build` или `.\gradlew.bat build` на Windows.

Готовый `hologram-addon-*.jar` лежит в `common/build/libs/`.


## Лицензия

[MIT](LICENSE).
