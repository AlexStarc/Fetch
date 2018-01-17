package com.tonyodev.fetch2

import android.os.Parcel
import android.os.Parcelable

/**
 * Use this class to create a request that is used by Fetch to enqueue a download and
 * begin the download process.
 * */
open class Request constructor(
        /** The url where the file will be downloaded from.*/
        val url: String,

        /** The file eg(/files/download.txt) where the file will be
         * downloaded to and saved on disk.*/
        val file: String,
        /**
         * Request symbolic name (e.g. if needed to be displayed in UI)
         */
        val name: String = "") : RequestInfo(), Parcelable {

    /** Used to identify a download.*/
    val id: Int = (url.hashCode() * 31) + file.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Request
        if (url != other.url) return false
        if (file != other.file) return false
        if (id != other.id) return false
        if (groupId != other.groupId) return false
        if (headers != other.headers) return false
        if (priority != other.priority) return false
        if (networkType != other.networkType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + file.hashCode()
        result = 31 * result + id
        result = 31 * result + groupId
        result = 31 * result + headers.hashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + networkType.hashCode()
        return result
    }

    override fun toString(): String {
        return "Request(url='$url', file='$file', id=$id, groupId=$groupId, " +
                "headers=$headers, priority=$priority, networkType=$networkType)"
    }

    constructor(/** The url where the file will be downloaded from.*/
                url: String,
                /** The file eg(/files/download.txt) where the file will be
                 * downloaded to and saved on disk.*/
                file: String) : this(url, file, "")

    constructor(parcel: Parcel) : this(parcel.readString(),
            parcel.readString(),
            parcel.readString()) {
        initFromInfo(RequestInfo(parcel))
    }

    private fun initFromInfo(requestInfo: RequestInfo) {
        groupId = requestInfo.groupId
        headers.clear()
        headers.putAll(requestInfo.headers)
        priority = requestInfo.priority
        networkType = requestInfo.networkType
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(file)
        parcel.writeString(name)
        super.writeToParcel(parcel, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Request> {
        override fun createFromParcel(parcel: Parcel): Request = Request(parcel)

        override fun newArray(size: Int): Array<Request?> = arrayOfNulls(size)
    }
}
