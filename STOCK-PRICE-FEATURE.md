# פיצ'ר: טיקר → מחיר מניה חי

מסמך ייחוס. כשמתחילים אפליקציה חדשה שצריכה את הפיצ'ר הזה כחלק ממנה —
מצביעים על הקובץ הזה ואומרים "תשתמש בזה" ולא צריך להסביר שוב.

מימוש עובד ונבדק בפועל נמצא ב-`index.html` באותו רפו (`yanivamir1/TEST-FILES`),
חי בכתובת https://yanivamir1.github.io/TEST-FILES/

---

## מה זה עושה

מקבלים סימול מניה (טיקר), מחזירים מחיר נוכחי + שינוי יומי + טווח יום.
בלי שרת, בלי בניית פרויקט — קריאת `fetch` אחת מהדפדפן ישירות למקור הנתונים.

## מקור הנתונים: Finnhub

```
GET https://finnhub.io/api/v1/quote?symbol=<TICKER>&token=<API_KEY>
```

תשובה:
```json
{ "c": 261.74, "d": 2.29, "dp": 0.88, "h": 263.31, "l": 260.68, "o": 261.07, "pc": 259.45, "t": 1582641000 }
```

| שדה | משמעות |
|---|---|
| `c` | מחיר נוכחי |
| `d` | שינוי בדולרים |
| `dp` | שינוי באחוזים |
| `h` / `l` | גבוה / נמוך של היום |
| `o` | פתיחה |
| `pc` | סגירה קודמת |
| `t` | חותמת זמן Unix של הנתון |

### למה Finnhub ולא משהו אחר — נבדק בפועל, לא הונח

| מקור | תוצאה |
|---|---|
| Yahoo Finance / Stooq | ❌ הדפדפן חסום מלפנות ישירות (אין CORS) |
| Alpha Vantage (תוכנית חינם) | ✅ פונה, אבל מחזיר רק סגירה יומית — לא מחיר בזמן אמת, ומוגבל ל-25 קריאות/יום |
| **Finnhub (תוכנית חינם)** | ✅ פונה ישירות מהדפדפן, **מחירים אמריקאיים בזמן אמת**, 60 קריאות/דקה, רישיון לשימוש אישי |

**מגבלה ידועה:** מניות אמריקאיות בלבד בתוכנית החינמית. מניות בבורסות אחרות
(תל אביב, לונדון וכו') לא יחזירו נתונים.

### מפתח API

מפתח אישי וחינמי מ-[finnhub.io/register](https://finnhub.io/register).
בפרויקט הנוכחי הוחלט להטמיע אותו ישירות בקוד (client-side) — פשוט להתחלה,
ובתוכנית החינמית אין אמצעי תשלום מקושר כך שהסיכון הוא רק ניצול מכסה,
לא כסף. אם אפליקציה עתידית תרצה להסתיר את המפתח, הפתרון הוא שרת-ביניים
קטן (Cloudflare Worker וכו') שמעביר את הבקשה — בלי לשנות שום דבר בצד
הלקוח מלבד כתובת ה-fetch.

**המפתח הפעיל בפרויקט הזה נמצא כרגע מוטמע בתוך `index.html` באותו רפו.**

---

## קוד לשימוש חוזר

פונקציית JavaScript טהורה, בלי תלות בשום UI — מכניסים טיקר, מקבלים
אובייקט מעובד או שגיאה מזוהה. זה הליבה שאפשר להדביק לתוך כל אפליקציה.

```javascript
/**
 * מחזיר מחיר מניה חי או זורק שגיאה עם code מזוהה.
 * code אפשריים: "not_found" | "invalid_key" | "rate_limited" | "network" | "server_error"
 */
async function getStockPrice(ticker, apiKey) {
  const symbol = ticker.trim().toUpperCase();
  if (!symbol) throw { code: "empty" };

  let res;
  try {
    res = await fetch(`https://finnhub.io/api/v1/quote?symbol=${encodeURIComponent(symbol)}&token=${apiKey}`);
  } catch (e) {
    throw { code: "network" };
  }

  if (res.status === 401) throw { code: "invalid_key" };
  if (res.status === 429) throw { code: "rate_limited" };
  if (!res.ok) throw { code: "server_error" };

  const data = await res.json();
  if (!data.c || data.c === 0) throw { code: "not_found", symbol };

  return {
    symbol,
    price: data.c,
    change: data.d,
    changePercent: data.dp,
    open: data.o,
    high: data.h,
    low: data.l,
    previousClose: data.pc,
    updatedAt: new Date(data.t * 1000),
  };
}
```

שימוש:
```javascript
try {
  const q = await getStockPrice("AAPL", API_KEY);
  console.log(`${q.symbol}: $${q.price.toFixed(2)} (${q.changePercent.toFixed(2)}%)`);
} catch (e) {
  // e.code בדיוק כמו בטבלה למעלה — מתאים ישירות להודעה למשתמש
}
```

## מצבי כשל שכדאי לטפל בהם (ולא לאחד להודעה גנרית אחת)

| מצב | זיהוי | הודעה מוצעת |
|---|---|---|
| טיקר לא קיים | `data.c === 0` (Finnhub מחזיר אפסים, לא שגיאת HTTP) | "לא נמצאה מניה בשם X" |
| מפתח שגוי/חסר | HTTP 401 | "מפתח ה-API לא תקין" |
| חריגה ממכסה | HTTP 429 | "יותר מדי בקשות, נסה בעוד רגע" |
| אין אינטרנט / fetch נכשל | `catch` | "אין חיבור לאינטרנט" |
| קלט ריק | לפני הקריאה | לא שולחים בכלל |

## דברים לזכור באפליקציה הבאה

- **בלי לולאת רענון תכופה.** Finnhub ממליץ לא לתשאל ברצף; אם רוצים "חי"
  באמת, המנגנון הנכון הוא WebSocket שלהם, לא polling — לבדוק מחדש אם
  זה נדרש.
- **נרמול קלט**: `trim()` + `toUpperCase()` על הטיקר לפני השליחה.
- אם האפליקציה הבאה תזדקק למניות מחוץ לארה"ב, זה דורש בדיקת מקור
  נתונים חדש — Finnhub החינמי לא מכסה את זה.
