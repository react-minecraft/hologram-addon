<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=782DFF&background=FFFFFF00&center=true&vCenter=true&width=700&lines=Hologram+Addon" alt="Hologram Addon Typing SVG"/></a>

<p>Голограммы над игроками для React</p>

<p>
  <a href="https://github.com/react-minecraft/hologram-addon/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/react-minecraft/hologram-addon?style=flat&logo=opensourceinitiative&logoColor=white" alt="License">
  </a>
  <a href="https://github.com/react-minecraft/hologram-addon/actions/workflows/build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/hologram-addon/build.yml?branch=master&style=flat&logo=github" alt="Build">
  </a>
</p>

<p>
  <a href="https://react-ac.space">Сайт</a>
  &nbsp;•&nbsp;
  <a href="https://t.me/react_ac">Канал</a>
  &nbsp;•&nbsp;
  <a href="https://t.me/react_ac_support">Поддержка</a>
</p>

</div>

---

## Что это?

- Аддон для плагина [React](https://github.com/react-minecraft/plugin): показывает над игроком голограмму со
  статистикой проверок (средняя и максимальная вероятность, последние результаты).
- На 1.19.4+ использует Text Display, на старых версиях — стойки для брони.
- Видят голограммы только игроки с правом `react.hologram.view`.

## Команды

| Команда | Право |
|---|---|
| `/react hologram reload` | без отдельного права (только базовое `/react`) |
| `/react hologram resync [игроки]` | без отдельного права (только базовое `/react`) |

## Конфиг

```yaml
settings:
  text:
    lines:
      - "<white>ᴀᴠɢ ᴘʀᴏʙ </white>{statistic:avg-probability:colored}"
      - "{history:last-5}"
  text-display:
    y-offset: 2.45
    text-scale: 0.65
```

## Установка

1. Соберите аддон или скачайте jar из релизов.
2. Положите `reacthologramaddon-common-<версия>.jar` в `plugins/React/addons/`.
3. Перезапустите сервер. Конфиг появится в папке аддона.

## Сборка

```bash
./gradlew build
```

Готовый jar лежит в `common/build/libs/`.

## Лицензия

См. [LICENSE](LICENSE).
