<!-- versionCode: 6 -->

<div align="center">
  <img src="https://raw.githubusercontent.com/BBGGVP5/nimbo/v1.1.0-beta.2/nimbo.png" width="132" alt="Nimbo">
  <h1>Nimbo 1.1.0 Beta 2</h1>
  <p><em>Обход блокировок из подписки и более аккуратный Android-интерфейс.</em></p>
</div>

| Платформа | Скачать |
|:--|:--|
| 🤖 **Android** | [![ARM64](https://img.shields.io/badge/ARM64--V8A-APK-00a844?style=for-the-badge&logo=android&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.1.0-beta.2/Nimbo_v1.1.0-beta.2_arm64_v8a_release.apk) [![UNIVERSAL](https://img.shields.io/badge/UNIVERSAL-APK-4b5563?style=for-the-badge&logo=android&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.1.0-beta.2/Nimbo_v1.1.0-beta.2_universal_release.apk) [![ARMV7](https://img.shields.io/badge/ARMEABI--V7A-APK-4b5563?style=for-the-badge&logo=android&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.1.0-beta.2/Nimbo_v1.1.0-beta.2_armeabi_v7a_release.apk) |
| 🪟 **Windows** | [![WINDOWS X64](https://img.shields.io/badge/УСТАНОВЩИК-X64-00a844?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.1.0-beta.2/NimboSetup_1.1.0-beta.2_x64.exe) [![WINDOWS X86](https://img.shields.io/badge/УСТАНОВЩИК-X86-4b5563?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.1.0-beta.2/NimboSetup_1.1.0-beta.2_x86.exe) [![WINDOWS ARM64](https://img.shields.io/badge/УСТАНОВЩИК-ARM64-4b5563?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.1.0-beta.2/NimboSetup_1.1.0-beta.2_arm64.exe) |
| 🐧 **Linux** | [![INSTALLER X64](https://img.shields.io/badge/УСТАНОВЩИК-X64-00a844?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.1.0-beta.2/NimboSetup_1.1.0-beta.2_x64) |

<div align="center">
  🛡️ Для каждого установочного файла приложена отдельная контрольная сумма <code>.sha256</code>.
</div>

> [!IMPORTANT]
> Beta 2 доступна в канале «Бета». Это тестовая сборка: если важнее максимальная предсказуемость, оставайтесь на стабильном канале.

<!-- nimbo:android:start -->
# 🤖 Что нового на Android

## 🛡 Обход блокировок по TLS

Начало защищённого соединения теперь может уходить несколькими частями, поэтому фильтрам труднее определить, к какому сайту вы обращаетесь. Это помогает там, где страница раньше подвисала на подключении или обрывалась в самом начале.

## 🔄 Параметры обхода приходят с подпиской

Настройки обхода теперь может подбирать сам сервис и менять их без обновления приложения. Включать ничего не нужно — свежие параметры приезжают вместе с подпиской и применяются к её серверам автоматически. Ручной переключатель сохранён как запасной вариант для обычных подписок.

## 🎨 Нормальные иконки Nimbo

- Вместо разрозненных экспериментальных значков теперь доступно шесть аккуратных вариантов с фирменным облаком Nimbo.
- Все готовые варианты корректно вписываются в адаптивную форму Android и сохраняют пометку Beta.
- Исправлены обрезанные края, лишние полосы и неверный размер превью.
- Экран стал проще: выбор основной иконки и конструктор собственного ярлыка больше не дублируют друг друга.
- Для уведомлений и отдельного ярлыка по-прежнему можно выбрать форму, цвет, вид облака или загрузить своё изображение.

## ✨ Больше жизни, меньше помех

- Экран синхронизации получил живой таймер, движение между устройствами и понятную анимацию этапов передачи.
- Исправлен слишком светлый прямоугольник в блоке прямой синхронизации.
- Нижняя панель может плавно скрываться при прокрутке вниз и возвращаться после остановки или движения вверх. Поведение отключается в настройках оформления.
<!-- nimbo:android:end -->

<!-- nimbo:desktop:start -->
# 🖥️ Что нового на Windows и Linux

## 🛡 Обход блокировок из подписки

- Nimbo получает параметры TLS-обхода вместе с подпиской и автоматически применяет их к нужным серверам.
- Сервис может менять параметры без новой версии приложения — достаточно обычного обновления подписки.
- Настройки работают и с подписками, где внутри несколько маршрутов или серверов.
- Ручной TLS Fragment остаётся запасным режимом, если сервис не передал свои параметры.
<!-- nimbo:desktop:end -->

# Небольшие исправления

- Настройки Beta 2 одинаково понимаются телефоном, Windows и Linux.
- Некорректные параметры подписки безопасно игнорируются и не мешают обычному подключению.
- Подписка с отключённым обходом не включает его из-за старой локальной настройки.

> [!NOTE]
> Параметры обхода применяются только когда их передаёт ваш сервис. Самостоятельно менять что-либо большинству пользователей не нужно.
