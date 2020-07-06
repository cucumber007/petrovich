package com.spqrta.petrovich

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import org.kaldi.Assets
import org.kaldi.Model
import org.kaldi.RecognitionListener
import org.kaldi.Vosk
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    companion object {
        init {
            System.loadLibrary("kaldi_jni")
        }

        const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }

    private var width: Float = 0f
    private var height: Float = 0f

    private enum class Direction {
        UP, DOWN, LEFT, RIGHT
    }

    private class Box(
        val view: View,
        val direction: Direction
    )

    private var petrovichDirection = Direction.UP

    private val boxes = mutableListOf<Box>()

    private var petrovichSpeed = 50f
    private val petrovichAcceleration = 15f
    private val petrovichTop = 100f
    private val petrovichLow = 0f
    private val boxSpeed = 70f

    private val sleep = 300L

    private lateinit var model: Model
    private lateinit var recognizer: SpeechRecognizerV2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        width = size.x.toFloat()
        height = size.y.toFloat()

        recognitionSetup()

        thread {
            while (true) {
                Thread.sleep(sleep)

                runOnUiThread {
//                        if (Math.random() > 0.50) {
//                            petrovichDirection = randomDirection()
//                        }

                    move(petrovich, direction = petrovichDirection, speed = petrovichSpeed)
                    handleScreenCollisionBounce(petrovich)

                    for (i in 0 until boxes.size) {
                        val it = boxes[i]
                        move(it.view, it.direction, speed = boxSpeed)

                        if (isCollided(petrovich, it.view)) {
                            it.view.setBackgroundColor(Color.RED)
                        }
                    }

                    if (Math.random() > 0.95) {
                        val boxView = LayoutInflater.from(this@MainActivity)
                            .inflate(R.layout.box, boxesContainer, false)
                        boxesContainer.addView(boxView)

                        if (Math.random() > 0.5) {
                            boxView.x = (Math.random() * width).toFloat()
                            boxes.add(Box(boxView, Direction.DOWN))
                        } else {
                            boxView.y = (Math.random() * height).toFloat()
                            boxes.add(Box(boxView, Direction.RIGHT))
                        }
                    }
                }
            }
        }

        bUp.setOnClickListener {
            petrovichDirection = Direction.UP
        }

        bDown.setOnClickListener {
            petrovichDirection = Direction.DOWN
        }

        bLeft.setOnClickListener {
            petrovichDirection = Direction.LEFT
        }

        bRight.setOnClickListener {
            petrovichDirection = Direction.RIGHT
        }
    }

    private fun isCollided(view: View, view1: View): Boolean {
        return view1.x > (view.x - view1.width) &&
                view1.x < (view.x + view1.width + view.width) &&
                view1.y > (view.y - view1.height) &&
                view1.y < (view.y + view1.height + view.height)
    }

    private fun move(view: View, direction: Direction, speed: Float = 100f) {
        when (direction) {
            Direction.UP -> view.y -= speed
            Direction.DOWN -> view.y += speed
            Direction.LEFT -> view.x -= speed
            Direction.RIGHT -> view.x += speed
        }
    }

    private fun handleScreenCollisionBounce(view: View) {
        if (view.x < 0) {
            petrovichDirection = Direction.RIGHT
            view.x = 0f
        }

        if (view.x > width) {
            petrovichDirection = Direction.LEFT
            view.x = width - view.width
        }

        if (view.y < 0) {
            petrovichDirection = Direction.DOWN
            view.y = 0f
        }

        if (view.y > height) {
            petrovichDirection = Direction.UP
            view.y = height - view.height
        }
    }

    private fun handleScreenCollisionDestroy(view: View): Boolean {
        if (view.x < 0 ||
            view.x > width ||
            view.y < 0 ||
            view.y > height
        ) {
            root.removeView(view)
            return true
        }
        return false
    }

    private fun randomDirection(): Direction {
        return Direction.values()[(Math.random() * Direction.values().size).toInt()]
    }

    private fun recognitionSetup() {

        // Check if user has given permission to record audio
        val permissionCheck = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
            return
        }

        setupAudio()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupAudio()
            } else {
                toast("ну и пошел нахуй тогда")
                finish()
            }
        }
    }

    private fun setupAudio() {
        thread {
            try {
                val assets = Assets(this)
                val assetDir = assets.syncAssets()
                Logger.d("Sync files in the folder $assetDir")
                Vosk.SetLogLevel(0)
                model = Model("$assetDir/model-android")

                recognizer =
                    SpeechRecognizerV2(model, "петрович налево направо вниз наверх блять пиздец")
                recognizer.addListener(object : RecognitionListener {
                    override fun onResult(result: String) {
                        Logger.d("Final result: $result")

                        val fullText = JSONObject(result).getString("text")

                        runOnUiThread {
                            resultText.text = fullText.ifBlank { "<<EMPTY FULL>>" }
                        }

                        changeDirection(fullText)
                    }

                    override fun onPartialResult(partial: String) {

                        Logger.d("Partial result: $partial")

                        val partialText = JSONObject(partial).getString("partial")
                            .trim().replace(Regex(" +"), " ")

                        runOnUiThread {
                            resultText.text = "<<${partialText.ifBlank { "EMPTY" }}>>"
                        }

                        changeDirection(partialText)
                    }

                    override fun onTimeout() {
                        toast("таймаут")
                    }

                    override fun onError(err: Exception) {
                        Logger.e(err)
                        toast("чет пошло не так в обработчике")
                    }
                })
                recognizer.startListening()

                toast("начинай пиздеть")
            } catch (e: Exception) {
                Logger.e(e)
                toast("чет пошло не так")
            }
        }
    }

    private fun changeDirection(command: String) {
        if (command.isBlank()) {
            return
        }

        val cmd = command.split(" ").last()

        runOnUiThread {
            when (cmd) {
                "налево" -> petrovichDirection = Direction.LEFT
                "направо" -> petrovichDirection = Direction.RIGHT
                "вниз" -> petrovichDirection = Direction.DOWN
                "наверх" -> petrovichDirection = Direction.UP
                "блять" -> petrovichSpeed =
                    min(petrovichSpeed + petrovichAcceleration, petrovichTop)
                "пиздец" -> petrovichSpeed =
                    max(petrovichSpeed - petrovichAcceleration, petrovichLow)
            }
        }
    }

    private fun toast(str: String) {
        runOnUiThread {
            Toast.makeText(this, str, Toast.LENGTH_LONG).show()
        }
    }
}
