package com.example.mapconnectivity

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
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
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val manual = findPreference<SwitchPreference>("switch_preference_bounds")
            val optimal_db = findPreference<EditTextPreference>("opt_db")
            val bad_db = findPreference<EditTextPreference>("bad_db")
            val optimal_lte = findPreference<EditTextPreference>("opt_lte")
            val bad_lte = findPreference<EditTextPreference>("bad_lte")
            val optimal_wifi = findPreference<EditTextPreference>("opt_wifi")
            val bad_wifi = findPreference<EditTextPreference>("bad_wifi")

            var last_optimal_db = optimal_db?.text
            var last_bad_db = bad_db?.text
            var last_optimal_lte = optimal_lte?.text
            var last_bad_lte = bad_lte?.text
            var last_optimal_wifi = optimal_wifi?.text
            var last_bad_wifi = bad_wifi?.text

            manual?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue == false) {
                        last_optimal_db = optimal_db?.text
                        last_bad_db = bad_db?.text
                        last_optimal_lte = optimal_lte?.text
                        last_bad_lte = bad_lte?.text
                        last_optimal_wifi = optimal_wifi?.text
                        last_bad_wifi = bad_wifi?.text

                        optimal_db?.text = resources.getString(R.string.opt_db_defaultValue)
                        bad_db?.text = resources.getString(R.string.bad_db_defaultValue)
                        optimal_lte?.text = resources.getString(R.string.opt_lte_defaultValue)
                        bad_lte?.text = resources.getString(R.string.bad_lte_defaultValue)
                        optimal_wifi?.text = resources.getString(R.string.opt_wifi_defaultValue)
                        bad_wifi?.text = resources.getString(R.string.bad_wifi_defaultValue)
                    } else {
                        optimal_db?.text = last_optimal_db
                        bad_db?.text = last_bad_db
                        optimal_lte?.text = last_optimal_lte
                        bad_lte?.text = last_bad_lte
                        optimal_wifi?.text = last_optimal_wifi
                        bad_wifi?.text = last_bad_wifi
                    }
                    true
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

        private fun isNumber(input: String): Boolean {
            return input.toIntOrNull() != null
        }

    }

}