package pea.app.util

import java.io.{File, RandomAccessFile}

object FileUtils {
  //  显示的字节大小
  val textSize = 1024 * 1024

  def readHead1K(file: File): String = {
    val bytes = Array.fill[Byte](textSize)(0)
    val access = new RandomAccessFile(file, "r")
    try {
      if (file.length() <= textSize) {
        access.readFully(bytes, 0, file.length().toInt)
        new String(bytes, 0, file.length().toInt)
      } else {
        access.readFully(bytes, 0, textSize)
        new String(bytes)
      }
    } finally {
      access.close()
    }
  }
}
