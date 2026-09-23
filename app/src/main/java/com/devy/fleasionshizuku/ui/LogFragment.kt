package com.devy.fleasionshizuku.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.devy.fleasionshizuku.R

class LogFragment : Fragment() {

    private lateinit var fullLog: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_log, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fullLog = view.findViewById(R.id.fullLog)
        fullLog.text = "Devy Fleasion log — events appear here.\n"
        view.findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            fullLog.text = ""
        }
    }
}
