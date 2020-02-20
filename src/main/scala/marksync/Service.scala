package marksync

import java.io.File

/**
 * Service trait.
 */
trait Service {
  /**
   * Filename of meta file.
   */
  val META_FILENAME: String

  /**
   * Get all documents.
   *
   * @return Documents by keys
   */
  def getDocuments: Map[String, ServiceDocument]

  /**
   * Get one document.
   *
   * @param id Document identifier
   * @return Document
   */
  def getDocument(id: String): Option[ServiceDocument]

  /**
   * Get service document from Document.
   *
   * @param doc      Document
   * @param uploader Uploader
   * @return ServiceDocument object and expect digest
   */
  def toServiceDocument(doc: Document, uploader: Option[Uploader]): Option[(ServiceDocument, Option[String])]

  /**
   * Save meta data to file.
   *
   * @param file     Target file
   * @param doc      Document
   * @param uploader Uploader
   */
  def saveMeta(file: File, doc: ServiceDocument, uploader: Option[Uploader] = None): Unit

  /**
   * Update document to service.
   *
   * @param doc ServiceDocument object to update
   * @return Updated ServiceDocument object
   */
  def update(doc: ServiceDocument, uploader: Option[Uploader]): Option[ServiceDocument]

  /**
   * Sync documents to service.
   *
   * @param doc      Document
   * @param uploader Uploader
   * @param check    Set true to check only
   * @param verbose  Output verbose message
   */
  def sync(doc: Document, uploader: Option[Uploader], check: Boolean, verbose: Boolean): Unit = {
    val target = doc.dir.getPath
    this.toServiceDocument(doc, uploader).foreach { case (newDoc, expectDigestOpt) =>
      // check document
      var doSync = false
      if (newDoc.getId.isDefined) {
        // update item
        val docId = newDoc.getId.get
        val oldDocOpt = getDocument(docId)
        if (oldDocOpt.isDefined) {
          val oldDoc = oldDocOpt.get
          val differ = newDoc.isModified(oldDoc, uploader) ||
            doc.files.exists { case (filename, file) =>
              uploader.exists(_.isModified(filename, file))
            }
          val driftDetected = expectDigestOpt.isDefined &&
            !expectDigestOpt.contains(oldDoc.getDigest())
          if (differ) {
            if (driftDetected) {
              println(s"x $target: modified externally")
            } else {
              println(s"! $target")
              doSync = true
            }
            if (verbose) {
              newDoc.isModified(oldDoc, uploader, printDiff = true)
            }
          } else { // not differ
            if (driftDetected) {
              println(s"  $target: not modified but digest mismatch.")
              println(s"  -${expectDigestOpt.get}")
              println(s"  +${oldDoc.getDigest()}")
            } else {
              println(s"  $target: not modified")
            }
          }
        } else {
          println(s"? $target: ($docId) not exists.")
        }
      } else {
        // new
        println(s"+ $target")
        doSync = true
      }

      // update document
      if (doSync && !check) {
        if (uploader.isDefined) {
          // upload files
          doc.files.foreach { case (filename, file) =>
            if (uploader.get.isModified(filename, file)) {
              val url = uploader.get.sync(filename, file)
              if (url.isDefined) {
                println(s"  ->uploaded. $filename to ${url.get}")
              } else {
                println(s"  ->upload failed. $filename")
              }
            }
          }
        }
        val updatedDocOpt = update(newDoc, uploader)
        updatedDocOpt match {
          case Some(item) =>
            println(s"  ->updated. ${item.getUrl.get}")
            saveMeta(new File(doc.dir, META_FILENAME), item, uploader)
          case None =>
            println(s"  ->failed.")
        }
      }
    }
  }
}
