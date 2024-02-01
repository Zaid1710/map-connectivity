package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

class Map (mapView: SupportMapFragment?, activity: MainActivity) {
    class AverageMeasures(
        var avgLte: Double?,
        var avgWifi: Double?,
        var avgDb: Double?
    )

    private var mapView: SupportMapFragment? = mapView
    private var activity: MainActivity = activity
    private val gridPolygons: MutableList<Polygon> = mutableListOf()
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationFromListener: Location? = null
    private var semaphore = Semaphore(1)
    private var meters = 0.0

    private lateinit var database: MeasureDB

    val LTE = 0
    val WIFI = 1
    val DB = 2

    private var WIFI_BAD = -75.0
    private var WIFI_OPT = -55.0
    private var LTE_BAD = -95.0
    private var LTE_OPT = -80.0
    private var DB_BAD = -80.0
    private var DB_OPT = -60.0

    private var MILLIS = 2000L

    private val mFusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)

    private val prefs = PreferenceManager.getDefaultSharedPreferences(activity)

    private val gridInARow = 5.0

    /**
     * Carica la mappa
     * @param mode Modalità di visualizzazione
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    fun loadMap(mode: Int) {
        database = Room.databaseBuilder(activity, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()

        val manual = prefs.getBoolean("switch_preference_bounds", false)
        if (manual) {
            WIFI_OPT = prefs.getString("opt_wifi", (-55).toString())?.toDoubleOrNull() ?: -55.0
            WIFI_BAD = prefs.getString("bad_wifi", (-75).toString())?.toDoubleOrNull() ?: -75.0
            LTE_BAD = prefs.getString("bad_lte", (-95).toString())?.toDoubleOrNull() ?: -95.0
            LTE_OPT = prefs.getString("opt_lte", (-80).toString())?.toDoubleOrNull() ?: -80.0
            DB_BAD = -(prefs.getString("bad_db", 80.toString())?.toDoubleOrNull() ?: 80.0)
            DB_OPT = -(prefs.getString("opt_db", 60.toString())?.toDoubleOrNull() ?: 60.0)
            Log.d("Manual", "DB_BAD: $DB_BAD, DB_OPT: $DB_OPT")
        } else {
            WIFI_BAD = -75.0
            WIFI_OPT = -55.0
            LTE_BAD = -95.0
            LTE_OPT = -80.0
            DB_BAD = -80.0
            DB_OPT = -60.0
        }


        mFusedLocationClient.lastLocation
            .addOnSuccessListener(activity) { location ->
                if (location != null) {
                    Log.d("LOCATION", "LAT: ${location.latitude}, LONG: ${location.longitude}")
                    mapView?.getMapAsync { googleMap ->

                        // Da gennaio 2024 non funziona più :(
                        var style = MapStyleOptions.loadRawResourceStyle(activity, R.raw.default_theme)
                        when (prefs.getString("theme_preference", 2.toString())) {
                            "0" -> { Log.d("style", "sono in 0"); style = MapStyleOptions.loadRawResourceStyle(activity, R.raw.default_theme) }
                            "1" -> { Log.d("style", "sono in 1"); style = MapStyleOptions.loadRawResourceStyle(activity, R.raw.dark_theme) }
                            "2" -> {
                                when (activity.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
                                    Configuration.UI_MODE_NIGHT_YES -> {style = MapStyleOptions.loadRawResourceStyle(activity, R.raw.dark_theme)}
                                    Configuration.UI_MODE_NIGHT_NO -> {style = MapStyleOptions.loadRawResourceStyle(activity, R.raw.default_theme)}
                                    Configuration.UI_MODE_NIGHT_UNDEFINED -> {style = MapStyleOptions.loadRawResourceStyle(activity, R.raw.default_theme)}
                                }
                            }
                        }
                        val success = googleMap.setMapStyle(style)
                        Log.d("style", success.toString())

                        googleMap.uiSettings.isZoomControlsEnabled = true
                        googleMap.isMyLocationEnabled = true
                        googleMap.uiSettings.isMyLocationButtonEnabled = true
                        googleMap.uiSettings.isRotateGesturesEnabled = false

                        googleMap.setOnMapLoadedCallback {
                            val latlng = LatLng(location.latitude, location.longitude)
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 16F))
                        }

                        googleMap.setOnMapClickListener {
                            Log.d("TOCCO", "Hai toccato la mappa, $it")
                            val touchPos = Location(LocationManager.GPS_PROVIDER)
                            touchPos.latitude = it.latitude
                            touchPos.longitude = it.longitude
                            val touchedPolygon = getPolygon(touchPos)
                            Log.d("TOCCO", "Hai toccato il poligono $touchedPolygon")
                            showInfoDialog(touchedPolygon)
                        }

                        googleMap.setOnCameraIdleListener {
                            deleteGrid()
                            drawGridOnMap(googleMap, mode)
                        }
                    }
                }
            }
    }

    /**
     * Disegna la griglia sulla mappa visualizzata
     * @param googleMap Mappa
     * @param mode Modalità di visualizzazione (LTE, Wi-Fi o dB)
     * */
    // polygon.points = [tl, bl, br, tr]
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    fun drawGridOnMap(googleMap: GoogleMap, mode: Int) {
        googleMap.uiSettings.isZoomControlsEnabled = false
        googleMap.uiSettings.isZoomGesturesEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            val zoom: Float = withContext(Dispatchers.Main) { googleMap.cameraPosition.zoom }

            val bounds = withContext(Dispatchers.Main) { googleMap.projection.visibleRegion.latLngBounds }
            meters = calculateGridSize(zoom)
            Log.d("METERS", "METERS: $meters, ZOOM: $zoom")

            val tlPoint = generateTopLeftPoint(meters, bounds.northeast.latitude, bounds.southwest.longitude) // tl significa Top Left
            var lastGeneratedPolygon = createPolygon(tlPoint, meters, mode)

            withContext(Dispatchers.Main) {
                val polygon = googleMap.addPolygon(lastGeneratedPolygon)
                gridPolygons.add(polygon)
            }

            var tr = withContext(Dispatchers.Main) { googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[3])}
            var bl = withContext(Dispatchers.Main) { googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[1])}

            val offset = 500
            val screen = Rect(0 - offset,0 - offset, activity.resources.displayMetrics.widthPixels + offset, activity.resources.displayMetrics.heightPixels + offset) // Area dello schermo

            while (screen.contains(bl.x, bl.y)) {
                val firstPolygon = lastGeneratedPolygon  // Per fare i poligoni attaccati
                while (screen.contains(tr.x, tr.y)) {
                    lastGeneratedPolygon = createPolygon(lastGeneratedPolygon.points[3], meters, mode)

                    withContext(Dispatchers.Main) {
                        val polygon = googleMap.addPolygon(lastGeneratedPolygon)
                        gridPolygons.add(polygon)
                    }

                    tr = withContext(Dispatchers.Main) {
                        googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[3])
                    }
                }
                lastGeneratedPolygon = createPolygon(firstPolygon.points[1], meters, mode)

                withContext(Dispatchers.Main) {
                    val polygon = googleMap.addPolygon(lastGeneratedPolygon)
                    gridPolygons.add(polygon)
                }

                bl = withContext(Dispatchers.Main) {
                    googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[1])
                }
                tr = withContext(Dispatchers.Main) {
                    googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[3])
                }
            }
            withContext(Dispatchers.Main) {
                googleMap.uiSettings.isZoomControlsEnabled = true
                googleMap.uiSettings.isZoomGesturesEnabled = true
            }
        }
    }

    /**
     * Calcola la grandezza in metri di ogni riquadro
     * @param zoom Zoom attuale della mappa
     * @return Lunghezza in metri del lato di un riquadro
     * */
    private fun calculateGridSize(zoom: Float) : Double {
        val meters = 22 * (2.0.pow(-(zoom - 22) - 1.0)) // A partire da 21, ogni volta che lo zoom diminuisce di 1, meters raddoppia
        val cellSize = meters / gridInARow
        return cellSize
    }

    /**
     * Restituisce le coordinate del punto in alto a sinistra del poligono in cui è presente la posizione in input
     * @param meters Grandezza di un riquadro in base allo zoom
     * @param lat Latitudine della posizione
     * @param lon Longitudine della posizione
     * @return Coordinate del punto in alto a sinistra del poligono
     * */
    private fun generateTopLeftPoint(meters: Double, lat: Double, lon: Double) : LatLng {
        val offset = metersToOffset(meters)

        return LatLng(ceil(lat / offset) * offset, floor(lon / offset) * offset)
    }

    /**
     * Converte un offset da metri a gradi di latitudine/longitudine (1° lat/lon = 111km circa)
     * @return Offset in gradi lat/lon
     * */
    private fun metersToOffset(meters: Double) : Double {
        return meters / 111111.0
    }

    /**
     * Crea un poligono sulla mappa dato il punto in alto a sinistra e la grandezza di un riquadro in base allo zoom
     * @param tlPoint Punto in alto a sinistra del poligono
     * @param meters Grandezza di un riquadro in base allo zoom
     * @param mode Modalità di visualizzazione (LTE, Wi-Fi o dB)
     * @return Poligono creato
     * */
    private fun createPolygon(tlPoint: LatLng, meters: Double, mode: Int) : PolygonOptions {
        val trPoint = LatLng(tlPoint.latitude, tlPoint.longitude + metersToOffset(meters))
        val blPoint = LatLng(tlPoint.latitude - metersToOffset(meters), tlPoint.longitude)
        val brPoint = LatLng(tlPoint.latitude - metersToOffset(meters), tlPoint.longitude + metersToOffset(meters))

        var color = Color.TRANSPARENT

        val imported = prefs.getBoolean("view_imported", true)

        val measureDao = database.measureDao()
        var measurements = measureDao.getMeasuresInPolygon(
            trPoint.latitude,
            trPoint.longitude,
            blPoint.latitude,
            blPoint.longitude,
            imported
        )

        val limitMeasures = prefs.getBoolean("switch_preference_limit", false)
        if (limitMeasures) {
            val limit = prefs.getString("limit", 5.toString())!!.toInt()

            measurements = measurements.takeLast(limit)
        }

        val avgMeasures = getAvgMeasures(measurements)

        var avgModeMeasure: Double? = 0.0
        var lowerBound = 0.0
        var upperBound = 0.0
        when (mode) {
            LTE -> { avgModeMeasure = avgMeasures.avgLte; lowerBound = LTE_BAD; upperBound = LTE_OPT }
            WIFI -> { avgModeMeasure = avgMeasures.avgWifi; lowerBound = WIFI_BAD; upperBound = WIFI_OPT }
            DB -> { avgModeMeasure = avgMeasures.avgDb?.times(-1); lowerBound = DB_BAD; upperBound = DB_OPT }
        }
        if (avgModeMeasure != null) {
            color = getQuality(avgModeMeasure, lowerBound, upperBound)
        }

        val polygon = PolygonOptions()
            .add(tlPoint, trPoint, brPoint, blPoint)
            .strokeWidth(2f)
            .strokeColor(Color.BLACK) // Colore del bordo (nero)
            .fillColor(color) // Colore di riempimento

        return polygon
    }

    /**
     * Rimuove tutti i poligoni sulla griglia
     * */
    fun deleteGrid() {
        for (polygon in gridPolygons) {
            polygon.remove()
        }
        gridPolygons.clear()
    }

    /**
     * Fornisce la posizione attuale sottoforma di oggetto Location
     * @return Oggetto Location con la posizione attuale
     * */
    @SuppressLint("MissingPermission")
    fun getPosition(): Location? {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        return gpsLocation
    }

    /**
     * Data una posizione, restituisce il riquadro in cui si trova
     * @param currentPos Posizione da controllare
     * @return Riquadro in cui si trova la posizione
     * */
    private fun getPolygon(currentPos: Location?): Polygon? {
        if (currentPos != null) {
            for (polygon in gridPolygons) {
                if (polygon.points[0].latitude >= currentPos.latitude && polygon.points[2].latitude <= currentPos.latitude && polygon.points[0].longitude <= currentPos.longitude && polygon.points[2].longitude >= currentPos.longitude) {
                    return polygon
                }
            }
        }
        return null
    }

    /**
     * Controlla se due posizioni si trovano nello stesso riquadro
     * @param pos1 Prima posizione
     * @param pos2 Seconda posizione
     * @return True se si trovano nello stesso riquadro, false altrimenti
     * */
    private fun areInTheSamePolygon(pos1: Location?, pos2: Location?): Boolean {
        return if (pos1 == null || pos2 == null) {
            true
        } else {
            generateTopLeftPoint(meters, pos1.latitude, pos1.longitude) == generateTopLeftPoint(meters, pos2.latitude, pos2.longitude)
        }
    }

    /**
     * Decide e restituisce il colore in base al valore in input
     * @param value Valore medio delle misure in un riquadro
     * @param bad Valore pessimo della categoria
     * @param optimal Valore ottimale della categoria
     * @return Colore da applicare al riquadro
     * */
    private fun getQuality(value: Double, bad: Double, optimal: Double): Int {
        return if (value <= bad) {
            Color.argb(90, 255, 0, 0)
        } else if (value >= optimal) {
            Color.argb(90, 0, 255, 0)
        } else {
            Color.argb(90, 255, 255, 0)
        }
    }

    /**
     * Gestisce il listener selezionando una modalità di esecuzione tra misurazione automatica e notifica di misure obsolete
     * @param googleMap Mappa
     * @param isInFetchingMode True se la modalità di esecuzione è misurazione automatica, false altrimenti (notifica)
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    fun listenerHandler(googleMap: GoogleMap, isInFetchingMode: Boolean) {
        var on: Boolean
        on = if (isInFetchingMode) {
            prefs.getBoolean("automatic_fetch", false)
        } else {
            prefs.getBoolean("notifyAbsentMeasure", true)
        }
        Log.d("LOCLISTENER", "Sono entrato in automaticFetch")
        semaphore.acquire()
        var lastLocation = locationFromListener
        semaphore.release()

        CoroutineScope(Dispatchers.IO).launch {
            while(on) {
                Log.d("LOCLISTENER", "Sono entrato nel while di listenerHandler")
                delay(MILLIS)
                on = if (isInFetchingMode) {
                    prefs.getBoolean("automatic_fetch", false)
                } else {
                    prefs.getBoolean("notifyAbsentMeasure", true)
                }

                withContext(Dispatchers.IO) {
                    semaphore.acquire()
                }

                val isOutside = withContext(Dispatchers.Main) { getPolygon(locationFromListener) } == null
                Log.d("LOCLISTENER", "${locationFromListener?.latitude}, ${locationFromListener?.longitude}. METERS = $meters")
                if (isOutside && lastLocation != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val newPolygon = createPolygon(
                            generateTopLeftPoint(
                                meters,
                                locationFromListener!!.latitude,
                                locationFromListener!!.longitude
                            ), meters, 0
                        )
                        withContext(Dispatchers.Main) {
                            val polygon = googleMap.addPolygon(newPolygon)
                            gridPolygons.add(polygon)
                        }
                    }
                }

                if (lastLocation != null && (withContext(Dispatchers.Main) { !areInTheSamePolygon(locationFromListener, lastLocation) })) {
                    if (isInFetchingMode) {
                        activity.manageMeasurePermissions(isOutside)
                    } else {
                        withContext(Dispatchers.Main) {
                            val polygon = getPolygon(locationFromListener)!!
                            val trPoint = polygon.points[1]
                            val blPoint = polygon.points[3]
                            val imported = prefs.getBoolean("view_imported", true)

                            val measureDao = withContext(Dispatchers.IO) { database.measureDao() }
                            val measurements = withContext(Dispatchers.IO) { measureDao.getMeasuresInPolygon(
                                trPoint.latitude,
                                trPoint.longitude,
                                blPoint.latitude,
                                blPoint.longitude,
                                imported
                            ) }
                            if (!checkForRecentMeasures(measurements)) {
                                //Manda notifica
                                Log.d("NOTIFICA", "NON CI SONO MISURE RECENTI!")
                                sendNotification()

                            }
                        }
                    }
                }

                lastLocation = locationFromListener
                semaphore.release()
            }
        }
    }

    /**
     * Effettua una misurazione ogni intervallo predefinito o scelto dall'utente
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    fun periodicFetch() {
        var periodic = prefs.getBoolean("periodic_fetch", false)
        val seconds = prefs.getString("periodic_fetch_interval", 10.toString())!!.toInt()
        Log.d("LOCLISTENER", "Sono entrato in periodicFetch")

        CoroutineScope(Dispatchers.IO).launch {
            while(periodic) {
                Log.d("LOCLISTENER", "Sono entrato nel while di periodicFetch")
                delay(seconds * 1000L)
                periodic = prefs.getBoolean("periodic_fetch", false)

                withContext(Dispatchers.IO) {
                    semaphore.acquire()
                }

                val isOutside = withContext(Dispatchers.Main) { getPolygon(locationFromListener) } == null
                activity.manageMeasurePermissions(isOutside)

                semaphore.release()
            }
        }

    }

    /**
     * Inizializza il location listener
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    fun initLocationListener() {
        locationListener =
            LocationListener { location ->
                semaphore.acquire()
                locationFromListener = location
                semaphore.release()

                Log.d(
                    "LOCATION",
                    "LAT: ${locationFromListener?.latitude}, LONG: ${locationFromListener?.longitude}"
                )
            }
        locationManager = activity.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager?
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, MILLIS, 0f, locationListener!!)
        Log.d("LOCLISTENER", "Listener avviato")
    }

    /**
     * Crea un dialog che viene visualizzato quando si clicca su un riquadro
     * @param polygon Riquadro premuto
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun showInfoDialog(polygon: Polygon?) {
        if (polygon != null) {
            val imported = prefs.getBoolean("view_imported", true)
            CoroutineScope(Dispatchers.IO).launch {
                val trPoint = withContext(Dispatchers.Main) { polygon.points[1] }
                val blPoint = withContext(Dispatchers.Main) { polygon.points[3] }
                var measures =
                    database.measureDao().getMeasuresInPolygon(
                        trPoint.latitude,
                        trPoint.longitude,
                        blPoint.latitude,
                        blPoint.longitude,
                        imported
                    )

                val limitMeasures = prefs.getBoolean("switch_preference_limit", false)
                if (limitMeasures) {
                    val limit = prefs.getString("limit", 5.toString())!!.toInt()

                    measures = measures.takeLast(limit)
                }


                val avgMeasures = getAvgMeasures(measures)
                Log.d("MISUREQUADRATO", measures.toString())
                val dialogBuilder = AlertDialog.Builder(activity, R.style.DialogTheme)
                dialogBuilder.setTitle("${measures.size} " + if (measures.size == 1) "MISURA TROVATA" else "MISURE TROVATE")

                var info: String
                if (measures.isEmpty()) {
                    info = "Nessuna misura trovata"
                } else {
                    info = "Media DB: ${avgMeasures.avgDb}\n\n" +
                            "Media LTE: ${avgMeasures.avgLte}\n\n" +
                            "Media WiFi: ${avgMeasures.avgWifi}"

                    if (imported) { info += "\n\nNumero misure importate: ${database.measureDao().countImportedMeasuresInPolygon(
                        trPoint.latitude,
                        trPoint.longitude,
                        blPoint.latitude,
                        blPoint.longitude
                    )} su ${measures.size}"}
                }

                dialogBuilder.setMessage(info)

                dialogBuilder.setNegativeButton("Chiudi") { _, _ -> }
                if (measures.isNotEmpty()) {
                    dialogBuilder.setNeutralButton("Mostra di più") { _, _ ->
                        showMoreDialog(measures)
                    }
                }
                withContext(Dispatchers.Main) { dialogBuilder.create().show() }
            }

        }
    }

    /**
     * Crea un dialog che mostra in elenco le misure all'interno del riquadro premuto
     * @param measures Lista di misure da mostrare
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun showMoreDialog(measures: List<Measure>) {
        val dialogBuilder = AlertDialog.Builder(activity, R.style.DialogTheme)
        val measures_titles = createExpandedString(measures)
        dialogBuilder.setTitle("${measures.size} " + if (measures.size == 1) "MISURA TROVATA" else "MISURE TROVATE")
        dialogBuilder.setNegativeButton("Chiudi") { _, _ -> }
        dialogBuilder.setItems(measures_titles) { _, which ->
            showDetailsDialog(measures[which])
        }
        dialogBuilder.create().show()
    }

    /**
     * Crea un dialog che visualizza ulteriori informazioni di una misura
     * @param measure Misura da visualizzare
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun showDetailsDialog(measure: Measure) {
        val dialogBuilder = AlertDialog.Builder(activity, R.style.DialogTheme)
        dialogBuilder.setTitle("MISURA IN DATA ${formatTimestamp(measure.timestamp)}")

        var info = "LAT: ${measure.lat}\n\n" +
                "LON: ${measure.lon}\n\n" +
                "DB: ${measure.db}\n\n" +
                "LTE: ${measure.lte}\n\n" +
                "WiFi: ${measure.wifi}"

        val imported = prefs.getBoolean("view_imported", true)
        if (imported) { info += "\n\nImportata: ${if (measure.imported) "Si" else "No"}" }

        dialogBuilder.setMessage(info)
        dialogBuilder.setNegativeButton("Chiudi") { _, _ -> }
        dialogBuilder.setNeutralButton("Elimina misura") { _, _ ->
            deleteMeasureDialog(measure)
        }
        dialogBuilder.create().show()
    }

    /**
     * Crea un dialog che chiede all'utente se si è sicuri di voler cancellare la misura selezionata
     * @param measure Misura da cancellare
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun deleteMeasureDialog(measure: Measure) {
        val dialogBuilder = AlertDialog.Builder(activity, R.style.DialogTheme)
        dialogBuilder.setMessage("Sei sicuro di voler eliminare la misura?")
        dialogBuilder.setNeutralButton("No") { _, _ -> }
        dialogBuilder.setPositiveButton("Si") { _, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                database.measureDao().deleteMeasureWithId(measure.id)
            }
            val toast = Toast.makeText(activity, "Misura cancellata con successo", Toast.LENGTH_SHORT)
            toast.show()
            mapView?.getMapAsync { googleMap ->
                deleteGrid()
                drawGridOnMap(googleMap, prefs.getString("mode_preference", 0.toString())!!.toInt())
            }
        }
        dialogBuilder.create().show()

    }

    /**
     * Data una lista di misure, crea e restituisce un array contenente le date delle misure
     * @param measures Lista di misure
     * @return Array di stringhe contenente le date formattate delle misure
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createExpandedString(measures: List<Measure>): Array<String> {
        var array = arrayOf<String>()
        for (measure in measures) {
            val date = formatTimestamp(measure.timestamp)
            array += date
        }
        return array
    }

    /**
     * Dato una stringa contenente un timestamp, trasformandolo da yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z' a dd/MM/yyyy HH:mm:ss
     * @param timestamp Stringa contenente il timestamp da formattare
     * @return Stringa contenente il timestamp formattato
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatTimestamp(timestamp: String): String {
        val l = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")).format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))

        return l.toString()
    }

    /**
     * Crea un oggetto di tipo AverageMeasures, lo popola con le medie delle misure in input e lo restituisce
     * @param measures Lista di misure di cui fare le medie
     * @return Oggetto AverageMeasures con le medie delle misure
     * */
    private fun getAvgMeasures(measures: List<Measure>) : AverageMeasures {
        if (measures.isEmpty()) {
            return AverageMeasures(null, null,null)
        }

        val avg = AverageMeasures(0.0, 0.0,0.0)
        for (measure in measures) {
            avg.avgLte = avg.avgLte?.plus(measure.lte)
            avg.avgWifi = avg.avgWifi?.plus(measure.wifi)
            avg.avgDb = avg.avgDb?.plus(measure.db)
        }
        avg.avgLte = avg.avgLte?.div(measures.size)
        avg.avgWifi = avg.avgWifi?.div(measures.size)
        avg.avgDb = avg.avgDb?.div(measures.size)

        return avg
    }

    /**
     * Data una lista di misure, controlla se la più recente è datata al giorno odierno
     * @param measures Lista di misure da controllare
     * @return True se l'ultima misura è datata al giorno odierno, false altrimenti
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkForRecentMeasures(measures: List<Measure>) : Boolean {
        Log.d("DATE", "sono entrato nella funzione")
        if (measures.isEmpty()) { return false }

        val lastMeasure = measures.last()
        val today = LocalDate.now()
        val lastMeasureDate = LocalDate.parse(lastMeasure.timestamp.take(10)) // Stringa tipo: 2024-01-25T19:08:29.627463Z, la data corrisponde ai primi 10 chars

        Log.d("DATE", "Today: $today, lastMeasureDate: $lastMeasureDate, non è di oggi? ${today > lastMeasureDate}")

        return today == lastMeasureDate
    }

    /**
     * Invia una notifica segnalando che non sono state trovate misure recenti
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendNotification() {
        val CHANNEL_ID = "RecentMeasuresChannel"
        val SERVICE_NOTIFICATION_ID = 2
        val notificationManager = NotificationManagerCompat.from(activity)
        val name = "Misure recenti"
        val description = "Notifica in caso di misure recenti assenti"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = description
        notificationManager.createNotificationChannel(channel)

        val mBuilder = NotificationCompat.Builder(activity, CHANNEL_ID)
        mBuilder
            .setContentTitle("Misure assenti o obsolete")
            .setContentText("Non sono state trovate misure risalenti a oggi, effettuane qualcuna!")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .priority = NotificationCompat.PRIORITY_DEFAULT


        val notification = mBuilder.build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }
}
