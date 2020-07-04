package com.spqrta.petrovich

import android.graphics.Point
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private enum class Direction {
        UP, DOWN, LEFT, RIGHT
    }

    private var direction = Direction.UP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x.toFloat()
        val height = size.y.toFloat()

        val speed = 200f

        val thread = object : Thread() {
            override fun run() {
                while (true) {
                    Logger.d("lol")
                    sleep(200)
                    if(Math.random() > 0.50) {
                        direction = Direction.values()[(Math.random()*Direction.values().size).toInt()]
                    }

                    runOnUiThread {
                        when(direction) {
                            Direction.UP -> petrovich.y -= speed
                            Direction.DOWN -> petrovich.y += speed
                            Direction.LEFT -> petrovich.x -= speed
                            Direction.RIGHT -> petrovich.x += speed
                        }

                        if(petrovich.x < 0) {
                            direction = Direction.RIGHT
                            petrovich.x = 0f
                        }

                        if(petrovich.x > width) {
                            direction = Direction.LEFT
                            petrovich.x = width - petrovich.width
                        }

                        if(petrovich.y < 0) {
                            direction = Direction.DOWN
                            petrovich.y = 0f
                        }

                        if(petrovich.y > height) {
                            direction = Direction.UP
                            petrovich.y = height - petrovich.height
                        }
                    }
                }
            }
        }
        thread.start()
    }
}
