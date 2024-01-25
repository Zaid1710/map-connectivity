package com.example.mapconnectivity

import android.content.Intent
import android.os.Build
import android.os.Bundle
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

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val editor = prefs.edit()

            findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled =
                !(periodic?.isChecked!! || background_periodic?.isChecked!!)

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


            periodic?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener {_, newValue ->
                    if (newValue == true) {
                        findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled = false

                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("periodic", "start")

                        if (automatic?.isChecked == true) {
                            automatic.isChecked = false
                            i.putExtra("automatic", "stop")
                        }
                        if (background_periodic?.isChecked == true) {
                            background_periodic.isChecked = false
                            i.putExtra("automatic", "stop")
                        }

                        startActivity(i)
                    } else {
                        findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled = true
                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("periodic", "stop")
                        startActivity(i)
                    }

                    true
                }

            background_periodic?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener {_, newValue ->
                    if (newValue == true) {
                        findPreference<ListPreference>("periodic_fetch_interval")?.isEnabled = false

                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("background_periodic", "start")

                        if (periodic?.isChecked == true) {
                            periodic.isChecked = false
                            i.putExtra("periodic", "stop")
                        }
                        if (automatic?.isChecked == true) {
                            automatic.isChecked = false
                            i.putExtra("automatic", "stop")
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

            automatic?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener {_, newValue ->
                    if (newValue == true) {
                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("automatic", "start")

                        if (periodic?.isChecked == true) {
                            periodic.isChecked = false
                            i.putExtra("periodic", "stop")
                        }
                        if (background_periodic?.isChecked == true) {
                            background_periodic.isChecked = false
                            i.putExtra("automatic", "stop")
                        }

                        startActivity(i)
                        false
                    } else {
                        val i = Intent(context, MainActivity::class.java)
                        i.putExtra("automatic", "stop")
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


        }

        private fun validateInputOpt(input: String, bad: String?, isDb: Boolean): Boolean {
            if (!isNumber(input)) {
                val toast = Toast.makeText(requireContext(), "Input non valido", Toast.LENGTH_SHORT)
                toast.show()
                return false
            }

            return if (bad != null && input.isNotEmpty() && abs(input.toInt()) < abs(bad.toInt())) {
                true
            } else {
                val toast: Toast = if (isDb) {
                    Toast.makeText(requireContext(), "Inserire un valore inferiore a $bad", Toast.LENGTH_SHORT)
                } else {
                    Toast.makeText(requireContext(), "Inserire un valore superiore a $bad", Toast.LENGTH_SHORT)
                }
                toast.show()
                false
            }
        }

        private fun validateInputBad(input: String, opt: String?, isDb: Boolean): Boolean {
            if (!isNumber(input)) {
                val toast = Toast.makeText(requireContext(), "Input non valido", Toast.LENGTH_SHORT)
                toast.show()
                return false
            }

            return if (opt != null && input.isNotEmpty() && abs(input.toInt()) > abs(opt.toInt())) {
                true
            } else {
                val toast: Toast = if (isDb) {
                    Toast.makeText(requireContext(), "Inserire un valore superiore a $opt", Toast.LENGTH_SHORT)
                } else {
                    Toast.makeText(requireContext(), "Inserire un valore inferiore a $opt", Toast.LENGTH_SHORT)
                }
                toast.show()
                false
            }
        }

        private fun validateInputLimit(input: String): Boolean {
            if (!isNumber(input) || input.toInt() < 1) {
                val toast = Toast.makeText(requireContext(), "Input non valido", Toast.LENGTH_SHORT)
                toast.show()
                return false
            }

            return true
        }

        private fun isNumber(input: String): Boolean {
            return input.toIntOrNull() != null
        }

    }

}