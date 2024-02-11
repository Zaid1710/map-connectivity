package com.example.mapconnectivity

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val manual = findPreference<SwitchPreference>("switch_preference_bounds")
            val optimal_db = findPreference<EditTextPreference>("opt_db")
            val bad_db = findPreference<EditTextPreference>("bad_db")
            val optimal_lte = findPreference<EditTextPreference>("opt_lte")
            val bad_lte = findPreference<EditTextPreference>("bad_lte")
            val optimal_wifi = findPreference<EditTextPreference>("opt_wifi")
            val bad_wifi = findPreference<EditTextPreference>("bad_wifi")

            val theme = findPreference<ListPreference>("theme_preference")

            val periodic = findPreference<SwitchPreference>("periodic_fetch")
            val background_periodic = findPreference<SwitchPreference>("background_periodic_fetch")
            val automatic = findPreference<SwitchPreference>("automatic_fetch")

            val limit = findPreference<EditTextPreference>("limit")

            val delete = findPreference<Preference>("deleteMeasures")

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val editor = prefs.edit()

            // L'impostazione per scegliere la durata del fetch periodico è attiva se entrambi
            // gli switch sia per periodic che background periodic sono off
            findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled =
                !(periodic?.isChecked!! || background_periodic?.isChecked!!)

            // Impostazione soglie manuali
            manual?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue == false) {
                        editor.putString("last_optimal_db", optimal_db?.text)
                        editor.putString("last_bad_db", bad_db?.text)
                        editor.putString("last_optimal_lte", optimal_lte?.text)
                        editor.putString("last_bad_lte", bad_lte?.text)
                        editor.putString("last_optimal_wifi", optimal_wifi?.text)
                        editor.putString("last_bad_wifi", bad_wifi?.text)
                        editor.apply()

                        optimal_db?.text = resources.getString(R.string.opt_db_defaultValue)
                        bad_db?.text = resources.getString(R.string.bad_db_defaultValue)
                        optimal_lte?.text = resources.getString(R.string.opt_lte_defaultValue)
                        bad_lte?.text = resources.getString(R.string.bad_lte_defaultValue)
                        optimal_wifi?.text = resources.getString(R.string.opt_wifi_defaultValue)
                        bad_wifi?.text = resources.getString(R.string.bad_wifi_defaultValue)
                    } else {
                        optimal_db?.text = prefs.getString("last_optimal_db", resources.getString(R.string.opt_db_defaultValue))
                        bad_db?.text = prefs.getString("last_bad_db", resources.getString(R.string.bad_db_defaultValue))
                        optimal_lte?.text = prefs.getString("last_optimal_lte", resources.getString(R.string.opt_lte_defaultValue))
                        bad_lte?.text = prefs.getString("last_bad_lte", resources.getString(R.string.bad_lte_defaultValue))
                        optimal_wifi?.text = prefs.getString("last_optimal_wifi", resources.getString(R.string.opt_wifi_defaultValue))
                        bad_wifi?.text = prefs.getString("last_bad_wifi", resources.getString(R.string.bad_wifi_defaultValue))
                    }
                    true
                }

            // Gestione misurazione periodica
            periodic.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener {_, newValue ->
                    if (newValue == true) {
                        findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled = false

                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("periodic", "start")

                        // Se la misurazione periodica viene avviata mentre quella automatica è attiva, quest'ultima viene disattivata
                        if (automatic?.isChecked == true) {
                            automatic.isChecked = false
                        }
                        // Se la misurazione periodica viene avviata mentre quella periodica in background è attiva, quest'ultima viene disattivata
                        if (background_periodic?.isChecked == true) {
                            background_periodic.isChecked = false
                            i.putExtra("background_periodic", "stop")
                        }

                        startActivity(i)
                    } else {
                        findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled = true
                        val i = Intent(context, MainActivity::class.java)
                        startActivity(i)
                    }

                    true
                }

            // Gestione misurazione periodica in background
            background_periodic?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener {_, newValue ->
                    if (newValue == true) {
                        findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled = false

                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("background_periodic", "start")

                        // Se la misurazione periodica in background viene avviata mentre quella periodica è attiva, quest'ultima viene disattivata
                        if (periodic.isChecked) {
                            periodic.isChecked = false
                        }
                        // Se la misurazione periodica in background viene avviata mentre quella automatica è attiva, quest'ultima viene disattivata
                        if (automatic?.isChecked == true) {
                            automatic.isChecked = false
                        }
                        startActivity(i)
                    } else {
                        findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled = true
                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("background_periodic", "stop")
                        startActivity(i)
                    }

                    true
                }

            // Gestione misurazione automatica
            automatic?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener {_, newValue ->
                    if (newValue == true) {
                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("automatic", "start")

                        // Se la misurazione automatica viene avviata mentre quella periodica è attiva, quest'ultima viene disattivata
                        if (periodic.isChecked) {
                            periodic.isChecked = false
                        }
                        // Se la misurazione automatica viene avviata mentre quella periodica in background è attiva, quest'ultima viene disattivata
                        if (background_periodic?.isChecked == true) {
                            background_periodic.isChecked = false
                            i.putExtra("background_periodic", "stop")
                        }

                        startActivity(i)
                        false
                    }
                    else {
                        val i = Intent(context, MainActivity::class.java)
                        startActivity(i)
                        true
                    }
                }


            optimal_db?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputOpt(newValue.toString(), bad_db?.text, true)
                    valid
                }

            bad_db?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputBad(newValue.toString(), optimal_db?.text, true)
                    valid
                }

            optimal_lte?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputOpt(newValue.toString(), bad_lte?.text, false)
                    valid
                }

            bad_lte?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputBad(newValue.toString(), optimal_lte?.text, false)
                    valid
                }

            optimal_wifi?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputOpt(newValue.toString(), bad_wifi?.text, false)
                    valid
                }

            bad_wifi?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputBad(newValue.toString(), optimal_wifi?.text, false)
                    valid
                }

            limit?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputLimit(newValue.toString())
                    valid
                }

            theme?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    when (newValue as String) {
                        "0" -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
                        "1" -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
                        "2" -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
                    }
                    true
                }

            delete?.setOnPreferenceClickListener {
                Log.d("Delete", "DELETE ALL")
                val database = Room.databaseBuilder(requireContext(), MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
                val measureDao = database.measureDao()



                val dialogBuilder = AlertDialog.Builder(activity, R.style.DialogTheme)
                dialogBuilder.setTitle("Scegli un'opzione")
                dialogBuilder.setNegativeButton("Chiudi") { _, _ -> }
                dialogBuilder.setItems(arrayOf("Elimina solo le mie misure", "Elimina solo le misure importate", "Elimina tutte le misure")) { _, which ->
                    Log.d("Delete", which.toString())
                    CoroutineScope(Dispatchers.IO).launch {
                        var deletedMeasures = 0
                        when (which) {
                            0 -> {
                                deletedMeasures = measureDao.getAllMeasuresImported(false).size
                                measureDao.deleteAllMeasuresImported(false)
                            }

                            1 -> {
                                deletedMeasures = measureDao.getAllMeasuresImported(true).size
                                measureDao.deleteAllMeasuresImported(true)
                            }

                            2 -> {
                                deletedMeasures = measureDao.getAllMeasures().size
                                measureDao.deleteAllMeasures()
                            }
                        }
                        val string = if (deletedMeasures == 0) "Nessuna misura da cancellare" else "$deletedMeasures ${if (deletedMeasures == 1) "misura cancellata" else "misure cancellate"} con successo"
                        withContext(Dispatchers.Main) {
                            val toast = Toast.makeText(activity, string, Toast.LENGTH_SHORT)
                            toast.show()
                        }
                    }

                }
                dialogBuilder.create().show()
                true
            }
        }

        /**
         * Verifica che la stringa impostata come soglia per il caso ottimale in input sia un valore valido
         * @param input Stringa da verificare
         * @param bad Valore pessimo da confrontare con quello in input
         * @param isDb Se il valore da controllare è in Db
         * @return Se la stringa in input è un valore valido
         * */
        private fun validateInputOpt(input: String, bad: String?, isDb: Boolean): Boolean {
            if (!isNumber(input)) {
                val toast = Toast.makeText(requireContext(), "Input non valido", Toast.LENGTH_SHORT)
                toast.show()
                return false
            }

            return if (bad != null && input.isNotEmpty() && abs(input.toInt()) < abs(bad.toInt())) {
                true
            } else {
                val toast: Toast = if (isDb) { // Se il valore è in Db il controllo è inverso
                    Toast.makeText(requireContext(), "Inserire un valore inferiore a $bad", Toast.LENGTH_SHORT)
                } else {
                    Toast.makeText(requireContext(), "Inserire un valore superiore a $bad", Toast.LENGTH_SHORT)
                }
                toast.show()
                false
            }
        }

        /**
         * Verifica che la stringa impostata come soglia per il caso pessimo in input sia un valore valido
         * @param input Stringa da verificare
         * @param opt Valore ottimale da confrontare con quello in input
         * @param isDb Se il valore da controllare è in Db
         * @return Se la stringa in input è un valore valido
         * */
        private fun validateInputBad(input: String, opt: String?, isDb: Boolean): Boolean {
            if (!isNumber(input)) {
                val toast = Toast.makeText(requireContext(), "Input non valido", Toast.LENGTH_SHORT)
                toast.show()
                return false
            }

            return if (opt != null && input.isNotEmpty() && abs(input.toInt()) > abs(opt.toInt())) {
                true
            } else {
                val toast: Toast = if (isDb) { // Se il valore è in Db il controllo è inverso
                    Toast.makeText(requireContext(), "Inserire un valore superiore a $opt", Toast.LENGTH_SHORT)
                } else {
                    Toast.makeText(requireContext(), "Inserire un valore inferiore a $opt", Toast.LENGTH_SHORT)
                }
                toast.show()
                false
            }
        }

        /**
         * Verifica che la stringa in input sia un numero e sia positivo
         * @param input Stringa da verificare
         * @return True se la stringa in input è un numero positivo, false altrimenti
         * */
        private fun validateInputLimit(input: String): Boolean {
            if (!isNumber(input) || input.toInt() < 1) {
                val toast = Toast.makeText(requireContext(), "Input non valido", Toast.LENGTH_SHORT)
                toast.show()
                return false
            }

            return true
        }

        /**
         * Verifica che la stringa in input sia un numero
         * @param input Stringa da verificare
         * @return True se la stringa in input è un numero, false altrimenti
         * */
        private fun isNumber(input: String): Boolean {
            return input.toIntOrNull() != null
        }

    }

}