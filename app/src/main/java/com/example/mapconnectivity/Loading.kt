package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.opengl.Visibility
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Loading : Fragment() {

    private var timer : MaterialTextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

//        Log.d("Bundle", "FRAGMENT: $bundle")

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_loading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cancelBtn : Button? = getView()?.findViewById(R.id.cancelBtn)
//        changeTimer("")
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
                cancelBtn?.visibility = View.GONE
                timer?.visibility = View.GONE
                Log.d("BOTTONE", "Import")
            } else {
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

    private fun changeTitle(newText: String) {
        val text : TextView? = view?.findViewById(R.id.loadingTitle)
        if (text != null) {
            text.text = newText
        }
    }

    private fun changeTimer(newText: String) {
        if (timer != null) {
            timer?.text = newText
        }
    }
}