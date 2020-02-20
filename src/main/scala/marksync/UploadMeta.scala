package marksync

import java.io.File

case class UploadMeta(var files: Seq[FileInfo] = Seq()) {
  def getFileInfo(filename: String): Option[FileInfo] = files.find(_.filename == filename)

  /**
   * Set file info.
   *
   * @param filename file name
   * @param file     File
   * @param url      URL
   */
  def setFileInfo(filename: String, file: File, url: Option[String] = None): Unit = {
    files = files.filter(_.filename != filename) :+ FileInfo(filename, file, url)
  }
}
