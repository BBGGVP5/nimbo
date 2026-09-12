# Deep Links для NebulaGuard

## Формат ссылок

Для добавления подписок через браузер используйте формат:

```
nebula://add/{SUBSCRIPTION_URL}
```

или

```
nebula://subscription/{SUBSCRIPTION_URL}
```

## Примеры использования

### 1. Прямая ссылка на подписку
```
nebula://add/https://sub.example.com/abc123xyz
```

### 2. С URL-encoded (для браузеров)
```
nebula://add/https%3A%2F%2Fsub.example.com%2Fabc123xyz
```

### 3. Альтернативный формат
```
nebula://subscription/https://sub.example.com/abc123xyz
```

## Как использовать

### Из браузера
1. Разместите ссылку на веб-странице:
   ```html
   <a href="nebula://add/https://sub.example.com/abc123xyz">Добавить подписку</a>
   ```

2. Или используйте в JavaScript:
   ```javascript
   window.location.href = 'nebula://add/https://sub.example.com/abc123xyz';
   ```

### Из Telegram
Отправьте ссылку в сообщении:
```
nebula://add/https://sub.example.com/abc123xyz
```

При нажатии откроется приложение NebulaGuard и подписка добавится автоматически.

### С веб-сайта
Создайте кнопку на сайте:
```html
<button onclick="window.location.href='nebula://add/https://sub.example.com/abc123xyz'">
    Подключиться к NebulaGuard
</button>
```

## Технические детали

### Обработка в приложении
- Схема: `nebula://`
- Host: `add` или `subscription`
- Path: URL подписки (например, `https://sub.example.com/abc123xyz`)

### AndroidManifest
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="nebula"/>
    <data android:host="add"/>
</intent-filter>
```

## Примечания

1. **URL должен начинаться с http:// или https://** - это проверяется при обработке
2. **URL автоматически декодируется** - если он URL-encoded
3. **Резервный вариант** - если path пустой, пробуем получить из query параметра `?url=`

## Примеры для разных сценариев

### Для Remnawave
```
nebula://add/https://sub.example.com/sub/abc123xyz
```

### Для 3X-UI панелей
```
nebula://add/https://your-panel.com/sub/username
```

### Для VLESS/VMess ссылок
Обычно не требуется, так как это отдельные протоколы. Используйте прямые ссылки на подписку.
