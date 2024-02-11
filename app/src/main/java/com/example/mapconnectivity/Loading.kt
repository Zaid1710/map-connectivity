package com.example.mapconnectivity

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import com.google.android.material.textview.MaterialTextView

class Loading : Fragment() {

    private var timer : MaterialTextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_loading, container, false)
    }

    /**
     * Alla creazione della view, vengono inizializzate le variabili e creato il bottone per interrompere l'esportazione tramite Bluetooth
     * @param view View del fragment caricamento
     * @param savedInstanceState Bundle ereditato da super
     * */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cancelBtn : Button? = getView()?.findViewById(R.id.cancelBtn)
        timer = view.findViewById(R.id.timerTxt)
        var countdown: CountDownTimer? = null
        val swapActivity = activity as SwapActivity
        val bundle = arguments
        if (bundle != null) {

            val message = bundle.getString("swap")
            if (message != null) {
                changeTitle(message)
            }
            val mode = bundle.getBoolean("mode")
            if (mode) {
                requireActivity().onBackPressedDispatcher.addCallback(this) {}
                cancelBtn?.visibility = View.GONE
                timer?.visibility = View.GONE
                Log.d("BOTTONE", "Import")
            } else {
                requireActivity().onBackPressedDispatcher.addCallback(this) {
                    swapActivity.stopDiscoverable()
                    countdown?.cancel()
                }
                cancelBtn?.visibility = View.VISIBLE
                timer?.visibility = View.VISIBLE
                cancelBtn?.setOnClickListener {
                    swapActivity.stopDiscoverable()
                    countdown?.cancel()
                }

                val timerBundle = bundle.getLong("timer") * 1000
                countdown = object: CountDownTimer(timerBundle, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        changeTimer("Secondi rimanenti: ${millisUntilFinished / 1000}")
                    }

                    override fun onFinish() {
                        swapActivity.hideFragment()
                        countdown?.cancel()
                    }
                }
                countdown.start()
                Log.d("BOTTONE", "Export")
            }
        }
    }

    /**
     * Cambia il titolo al valore della stringa in input
     * @param newText Nuovo valore del titolo
     * */
    private fun changeTitle(newText: String) {
        val text : TextView? = view?.findViewById(R.id.loadingTitle)
        if (text != null) {
            text.text = newText
        }
    }

    /**
     * Imposta il timer al valore della stringa in input
     * @param newText Nuovo valore del timer
     * */
    private fun changeTimer(newText: String) {
        if (timer != null) {
            timer?.text = newText
        }
    }
}