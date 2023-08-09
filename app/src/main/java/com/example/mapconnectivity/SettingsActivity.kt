package com.example.mapconnectivity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.takisoft.preferencex.EditTextPreference
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
                    val valid = validateInputOpt(newValue.toString(), bad_db?.text)
                    valid
                }

            bad_db?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputBad(newValue.toString(), optimal_db?.text)
                    valid
                }

            optimal_lte?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputOpt(newValue.toString(), bad_lte?.text)
                    valid
                }

            bad_lte?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputBad(newValue.toString(), optimal_lte?.text)
                    valid
                }

            optimal_wifi?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputOpt(newValue.toString(), bad_wifi?.text)
                    valid
                }

            bad_wifi?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val valid = validateInputBad(newValue.toString(), optimal_wifi?.text)
                    valid
                }
        }

        private fun validateInputOpt(input: String, bad: String?): Boolean {
            return if (bad != null && input.isNotEmpty() && abs(input.toInt()) < abs(bad.toInt())) {
                true
            } else {
                val toast = Toast.makeText(requireContext(), "Inserire un valore inferiore a $bad", Toast.LENGTH_SHORT)
                toast.show()
                false
            }
        }

        private fun validateInputBad(input: String, opt: String?): Boolean {
            return if (opt != null && input.isNotEmpty() && abs(input.toInt()) > abs(opt.toInt())) {
                true
            } else {
                val toast = Toast.makeText(requireContext(), "Inserire un valore superiore a $opt", Toast.LENGTH_SHORT)
                toast.show()
                false
            }
        }

    }

}