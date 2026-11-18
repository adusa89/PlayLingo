@file:Suppress("unused", "UNUSED_PARAMETER")
package com.google.mlkit.vision.text

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class Text {
    val text: String = ""
    val textBlocks: List<TextBlock> = emptyList()
    class TextBlock { val text: String = ""; val lines: List<Line> = emptyList(); val boundingBox: android.graphics.Rect? = null }
    class Line { val text: String = ""; val boundingBox: android.graphics.Rect? = null }
}
interface TextRecognizer { fun process(i: InputImage): Task<Text>; fun close() }
object TextRecognition { fun getClient(o: TextRecognizerOptions): TextRecognizer = TODO() }
