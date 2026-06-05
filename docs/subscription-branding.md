# Брендирование и кастомизация через подписку Nimbo

Nimbo поддерживает динамическое применение стилей (акцентные цвета, градиенты, блюр, логотипы и стиль интерфейса) непосредственно от провайдера подписки. Для этого сервер подписки должен возвращать специальные HTTP-заголовки ответа.

Приложение принимает как стандартные названия заголовков, так и их варианты с префиксом `x-` (все значения регистронезависимы).

---

## HTTP-заголовки

| Заголовок / Заголовок с префиксом | Формат / Значение | Описание |
| :--- | :--- | :--- |
| `nimbo-theme` / `x-nimbo-theme` | `<filter>,<accent>,<orb1>,<orb2>,<blur>` | Цветовая схема и эффекты |
| `nimbo-logo` / `x-nimbo-logo` | URL / Data URI / Raw Base64 | Логотип провайдера (бренда) |
| `nimbo-style` / `x-nimbo-style` | `glass` (или `nebula`) / `material` (или `md`) | Стиль интерфейса (дизайн-система) |

---

## Подробное описание форматов

### 1. `nimbo-theme` (Тема и оформление)
Формат значения представляет собой строку с 5 параметрами, разделенными запятыми:
```http
nimbo-theme: <filter>,<accent>,<orb1>,<orb2>,<blur>
```
* **`<filter>`** (строка, опционально) — название css-фильтра для графики (например, `saturate` или оставьте пустым).
* **`<accent>`** (HEX-цвет, обязательно для применения схемы) — основной акцентный цвет приложения (например, `#7c5dfa` или `7c5dfa` без решетки). Поддерживаются 3- и 6-значные HEX-коды.
* **`<orb1>`** (HEX-цвет, опционально) — цвет первого декоративного светящегося шара на фоне.
* **`<orb2>`** (HEX-цвет, опционально) — цвет второго декоративного светящегося шара на фоне.
* **`<blur>`** (число, опционально) — радиус размытия заднего плана в пикселях (целое число).

**Примеры:**
* Полная конфигурация:
  ```http
  nimbo-theme: saturate,#7c5dfa,#ff007c,#00f0ff,40
  ```
* Без фильтра (первый параметр пустой):
  ```http
  nimbo-theme: ,7c5dfa,ff007c,00f0ff,40
  ```

---

### 2. `nimbo-logo` (Логотип бренда)
Заголовок передает изображение логотипа, которое будет отображаться на карточке подписки в приложении.

Поддерживаются три формата значения:
1. **HTTP/HTTPS URL**: Прямая ссылка на изображение (PNG, JPG, SVG).
   ```http
   nimbo-logo: https://myprovider.com/assets/logo.png
   ```
2. **Data URI**: Изображение, закодированное прямо в строку.
   ```http
   nimbo-logo: data:image/png;base64,iVBORw0KGgoAAAANS...
   ```
3. **Raw Base64**: Чистый Base64-код изображения PNG (Nimbo автоматически обернет его в Data URI на стороне клиента).
   ```http
   nimbo-logo: iVBORw0KGgoAAAANS...
   ```

---

### 3. `nimbo-style` (Стиль интерфейса)
Заголовок определяет, какую дизайн-систему активировать для пользователя при применении темы провайдера:

1. **Nimbo Glass (Морфизм)**:
   * Значения: `glass`, `nebula`, `nimboglass`
   * Результат: Включает стеклянный полупрозрачный интерфейс со скруглениями и размытием.
   ```http
   nimbo-style: glass
   ```
2. **Material You (Expressive)**:
   * Значения: `material`, `materialyou`, `md3`, `mdyou`, `md`
   * Результат: Включает плоский лаконичный интерфейс в стиле Material Design 3 с акцентным окрашиванием элементов.
   ```http
   nimbo-style: material_you
   ```

---

## Примеры настройки серверов

### 1. Nginx
Добавьте строки в блок `location` вашего конфигурационного файла:
```nginx
location /sub {
    # ... ваши настройки отдачи подписки ...
    
    add_header nimbo-theme "saturate,#7c5dfa,#ff007c,#00f0ff,40";
    add_header nimbo-logo "https://myprovider.com/assets/logo.png";
    add_header nimbo-style "glass";
    
    # Разрешаем чтение заголовков CORS (если клиент запрашивает через веб)
    add_header Access-Control-Expose-Headers "nimbo-theme, nimbo-logo, nimbo-style, x-nimbo-theme, x-nimbo-logo, x-nimbo-style";
}
```

### 2. Cloudflare Workers (JavaScript)
```javascript
addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request))
})

async fn handleRequest(request) {
  // Получаем исходную подписку
  const response = await fetch("https://raw-subscription-link.com/data")
  
  // Создаем новые заголовки на основе существующих
  const newHeaders = new Headers(response.headers)
  newHeaders.set('nimbo-theme', 'saturate,#7c5dfa,#ff007c,#00f0ff,40')
  newHeaders.set('nimbo-logo', 'https://myprovider.com/assets/logo.png')
  newHeaders.set('nimbo-style', 'glass')
  newHeaders.set('Access-Control-Expose-Headers', 'nimbo-theme, nimbo-logo, nimbo-style')
  
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: newHeaders
  })
}
```

### 3. Node.js (Express)
```javascript
app.get('/sub', (req, res) => {
  res.setHeader('nimbo-theme', 'saturate,#7c5dfa,#ff007c,#00f0ff,40');
  res.setHeader('nimbo-logo', 'https://myprovider.com/assets/logo.png');
  res.setHeader('nimbo-style', 'glass');
  res.setHeader('Access-Control-Expose-Headers', 'nimbo-theme, nimbo-logo, nimbo-style');
  
  res.send(getSubscriptionData());
});
```

### 4. PHP
```php
<?php
header('nimbo-theme: saturate,#7c5dfa,#ff007c,#00f0ff,40');
header('nimbo-logo: https://myprovider.com/assets/logo.png');
header('nimbo-style: glass');
header('Access-Control-Expose-Headers: nimbo-theme, nimbo-logo, nimbo-style');

echo getSubscriptionData();
?>
```
