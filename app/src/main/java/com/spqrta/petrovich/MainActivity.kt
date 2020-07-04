package com.spqrta.petrovich

import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

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

    private val petrovichSpeed = 50f
    private val boxSpeed = 70f

    private val sleep = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        width = size.x.toFloat()
        height = size.y.toFloat()


        val thread = object : Thread() {
            override fun run() {
                while (true) {
                    sleep(sleep)

                    runOnUiThread {
//                        if (Math.random() > 0.50) {
//                            petrovichDirection = randomDirection()
//                        }

                        move(petrovich, direction = petrovichDirection, speed = petrovichSpeed)
                        handleScreenCollisionBounce(petrovich)

                        for (i in 0 until boxes.size) {
                            val it = boxes[i]
                            move(it.view, it.direction, speed = boxSpeed)

                            if(isCollided(petrovich, it.view)) {
                                it.view.setBackgroundColor(Color.RED)
                            }
                        }

                        if (Math.random() > 0.8) {
                            val boxView = LayoutInflater.from(this@MainActivity)
                                .inflate(R.layout.box, root, false)
                            root.addView(boxView)

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
        }
        thread.start()

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
}
