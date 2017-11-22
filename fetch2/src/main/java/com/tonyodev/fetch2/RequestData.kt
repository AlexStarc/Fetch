package com.tonyodev.fetch2

class RequestData(val url: String, val absoluteFilePath: String, name: String, status: Int,
                  error: Int, val downloadedBytes: Long, val totalBytes: Long, val headers: MutableMap<String, String>, val groupId: String) {
    val status: Status = Status.valueOf(status)
    val error: Error = Error.valueOf(error)
    val progress: Int = DownloadHelper.calculateProgress(downloadedBytes, totalBytes)
    val request: Request = Request(url, absoluteFilePath, name, headers)

    val id: Long
        get() = request.id

    override fun toString(): String = request.toString()
}
