package com.example.comfyapp

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivityMainBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import java.math.BigDecimal
import java.math.RoundingMode

class MainActivity : AppCompatActivity(), Robot.TtsListener {

    private lateinit var robot: Robot
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        robot = Robot.getInstance()
        robot.addTtsListener(this)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)


    }


    override fun onResume() {
        super.onResume()
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onStart() {
        super.onStart()
        robot.addTtsListener(this)
    }

    override fun onStop() {
        robot.removeTtsListener(this)
        super.onStop()
    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {}
}