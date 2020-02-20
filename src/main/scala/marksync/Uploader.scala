package marksync

import java.io.File

import com.fasterxml.jackson.annotation.JsonIgnore

trait Uploader {
  /**
   * Meta data
   */
  var meta: UploadMeta = UploadMeta()

  /**
   * Check modified.
   *
   * @param filename filename
   * @param file     file
   * @return true if modified
   */
  def isModified(filename: String, file: File): Boolean =
    !meta.getFileInfo(filename).exists(_.isIdenticalTo(file))

  /**
   * Get filename to URL map.
   *
   * @return filename to URL map
   */
  @JsonIgnore
  def getFileMap: Map[String, String] =
    meta.files.filter(_.url.isDefined).map(fileInfo => (fileInfo.filename, fileInfo.url.get)).toMap

  /**
   * Sync file.
   *
   * @param filename filename (relative to document directory)
   * @param file     file to upload
   * @return URL
   */
  def sync(filename: String, file: File): Option[String] = {
    val url = upload(file)
    if (url.isDefined) {
      meta.setFileInfo(filename, file, url)
    }
    url
  }

  /**
   * Upload file.
   *
   * @param file file to upload
   * @return URL
   */
  def upload(file: File): Option[String]
}
