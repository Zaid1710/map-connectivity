package com.example.mapconnectivity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
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

            val optimal_db = findPreference<EditTextPreference>("opt_db")
            val bad_db = findPreference<EditTextPreference>("bad_db")
            val optimal_lte = findPreference<EditTextPreference>("opt_lte")
            val bad_lte = findPreference<EditTextPreference>("bad_lte")
            val optimal_wifi = findPreference<EditTextPreference>("opt_wifi")
            val bad_wifi = findPreference<EditTextPreference>("bad_wifi")

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