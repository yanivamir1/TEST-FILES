# פיצ'ר: ברומטר (לחץ + גובה) + מהירות GPS + זווית טלפון

מסמך ייחוס. כשמתחילים אפליקציה חדשה שצריכה חלק מהפיצ'רים האלה —
מצביעים על הקובץ הזה ואומרים "תשתמש בזה" ולא צריך להסביר שוב.

מימוש עובד נמצא בתיקייה `barometer-gps-speed-app/` באותו רפו
(`yanivamir1/TEST-FILES`) - אפליקציית Android נייטיבית (Kotlin +
Jetpack Compose).

---

## מה זה עושה

מסך יחיד שמציג בזמן אמת:
1. **לחץ אטמוספרי** (hPa) מחיישן הברומטר.
2. **גובה משוער** מעל פני הים, מחושב מהלחץ.
3. **מהירות תנועה** (קמ"ש) לפי GPS.
4. **זווית הטיה של הטלפון** (pitch/roll, במעלות) מהאיצן (accelerometer).

## למה זה נייטיבי (Kotlin) ולא דף HTML כמו הפיצ'ר של מחיר המניה

בניגוד ל-GPS (שיש לו `navigator.geolocation` בדפדפן), **אין API סטנדרטי
לברומטר בשום דפדפן נייד** - ה-Generic Sensor API (`Barometer`
interface) מעולם לא נשלח (shipped) יציב באף דפדפן. לכן זה חייב
אפליקציה נייטיבית עם גישה ישירה לחיישני המכשיר.

## קוד לשימוש חוזר

שלושה "Repository" עצמאיים, כל אחד עוטף Flow שאפשר לאסוף
(collect) ולהתנתק ממנו (unregister) בקלות. אפשר להעתיק כל אחד בנפרד
לאפליקציה חדשה בלי תלות בשניים האחרים.

### 1. ברומטר - לחץ וגובה (`SensorRepository.kt`)
```kotlin
class SensorRepository(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    fun pressureFlow(): Flow<PressureReading> = callbackFlow {
        val sensor = pressureSensor
        if (sensor == null) {
            trySend(PressureReading.Unavailable)
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(PressureReading.Value(event.values[0])) // hPa
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    // גובה משוער במטרים מעל פני הים, מחושב מהלחץ הנוכחי
    fun altitudeMeters(hPa: Float): Float =
        sensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa)
}
```
**מגבלה ידועה:** לא כל מכשיר Android כולל חיישן לחץ (`TYPE_PRESSURE`)
- טלפונים חסכוניים לרוב לא כוללים אותו. חובה לטפל ב-`pressureSensor == null`.
**דיוק הגובה:** הנוסחה מניחה לחץ ים סטנדרטי (1013.25 hPa) - זו הערכה,
לא מדידת GPS/גובה אמיתית. אם צריך דיוק, יש לכייל מול נקודת ייחוס ידועה.

### 2. מהירות GPS (`LocationRepository.kt`)
```kotlin
class LocationRepository(context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun speedFlow(): Flow<SpeedReading> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val speed = if (location.hasSpeed()) location.speed * 3.6f else null // מ'/ש' -> קמ"ש
                trySend(SpeedReading(speedKmh = speed))
            }
            override fun onProviderDisabled(provider: String) { trySend(SpeedReading(speedKmh = null)) }
            // onStatusChanged, onProviderEnabled - ריקים
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
        awaitClose { locationManager.removeUpdates(listener) }
    }
}
```
- שימוש ב-`LocationManager` הגולמי (לא Fused Location של Google Play
  Services) - בלי תלות חיצונית.
- דורש הרשאת `ACCESS_FINE_LOCATION` (runtime permission) לפני הקריאה.
- `location.hasSpeed()` יכול להיות `false` (עמידה במקום / אין נעילת
  GPS) - להציג "ממתין לאיתות GPS" ולא 0 מטעה.

### 3. זווית הטיה של הטלפון (`AngleRepository.kt`)
```kotlin
class AngleRepository(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun angleFlow(): Flow<AngleReading> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            trySend(AngleReading.Unavailable)
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val pitch = Math.toDegrees(atan2(-x.toDouble(), sqrt((y*y + z*z).toDouble()))).toFloat()
                val roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
                trySend(AngleReading.Value(pitchDeg = pitch, rollDeg = roll))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
```
- מבוסס על `TYPE_ACCELEROMETER` (זמין כמעט בכל מכשיר) - לא דורש הרשאת
  runtime.
- `pitch` = הטיה קדימה/אחורה, `roll` = נטייה ימינה/שמאלה, שתיהן במעלות.
- אם צריך דיוק גבוה יותר / התנגדות לרעש תנועה - השדרוג הבא הוא
  `TYPE_ROTATION_VECTOR` (משלב ג'יירו + מגנטומטר), אבל זה דורש יותר
  קוד (rotation matrix) ולא כל מכשיר תומך בו.

## הרשאות (`AndroidManifest.xml`)
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```
ברומטר ואיצן (accelerometer) לא דורשים הרשאת runtime באנדרואיד.

## מחזור חיים
לרשום את כל ה-listeners ב-`ON_RESUME` ולהסיר אותם ב-`ON_PAUSE` (דרך
`LifecycleEventObserver`) - כדי לא לרוקן סוללה כשהאפליקציה ברקע. ראו
`MainActivity.kt` באפליקציה לדוגמה מלאה עם Jetpack Compose.

## בנייה והתקנה בלי מחשב (CI)
ב-`.github/workflows/build-apk.yml` יש workflow שבונה APK debug
אוטומטית בכל push ומפרסם אותו כ-artifact להורדה - כדי שאפשר יהיה
להתקין ישירות מהטלפון בלי Android Studio.

**תקלה ידועה בהתקנה:** אם ההתקנה "נתקעת" לזמן ארוך (יותר מכמה דקות)
בלי להתקדם - זה כמעט תמיד **Google Play Protect** שמנסה לסרוק את
ה-APK מול שרתי גוגל ותקוע בגלל בעיית רשת, לא בעיה באפליקציה עצמה.
פתרון: לוודא חיבור אינטרנט תקין, או לכבות זמנית
Settings → Google → Security → Google Play Protect → Scan apps.

## דברים לזכור באפליקציה הבאה
- אין תמיכה ב-iOS/Swift במימוש הזה - Android בלבד.
- ה-Artifact שנבנה ב-CI נשמר ב-GitHub לפרק זמן מוגבל (כ-90 יום כברירת
  מחדל) - אם עובר הרבה זמן, להריץ מחדש את ה-workflow.
- אם אפליקציה עתידית צריכה למזג את שלושת החיישנים לפיצ'ר אחד (למשל
  "מד גובה + מהירות טיפוס"), אפשר לשלב את `SensorRepository.altitudeMeters()`
  יחד עם `LocationRepository.speedFlow()` באותו מסך בלי שינוי בקוד
  הקיים - כל Repository עצמאי ולא תלוי באחרים.
