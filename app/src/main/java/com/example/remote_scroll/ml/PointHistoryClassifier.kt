package com.example.remote_scroll.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class PointHistoryClassifier(context: Context) {

    private var interpreter: Interpreter
    private val inputSize = 32      // history_length(16) * 2(x, y)
    private var outputSize = 0
    private val labels: List<String>

    init {
        val model = loadModelFile(context, "point_history_classifier.tflite")
        interpreter = Interpreter(model)

        val outputTensor = interpreter.getOutputTensor(0)
        val shape = outputTensor.shape()
        outputSize = shape[1]

        labels = context.assets.open("point_history_classifier_label.csv").bufferedReader()
            .readLines()
            .map { it.trim() }
    }

    fun classify(input: FloatArray): String {
        if (input.isEmpty()) return "None"

        val inputBuffer = ByteBuffer.allocateDirect(inputSize * 4)
            .order(ByteOrder.nativeOrder())

        for (v in input) inputBuffer.putFloat(v)

        val output = Array(1) { FloatArray(outputSize) }
        interpreter.run(inputBuffer, output)

        val maxEntry = output[0].withIndex().maxByOrNull { it.value }
        val maxIndex = maxEntry?.index ?: 0

        return labels.getOrElse(maxIndex) { "Unknown" }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}