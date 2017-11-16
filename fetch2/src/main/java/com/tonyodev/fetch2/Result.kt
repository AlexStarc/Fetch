package com.tonyodev.fetch2

import android.os.Parcel
import android.os.Parcelable


data class Result(val id: Long, val status: Status, val progress: Int = 0, val downloadedBytes: Long = 0, val totalBytes: Long = 0, val error: Error = Error.NONE) : Parcelable {

    constructor(parcel: Parcel) : this(
            parcel.readLong(),
            Status.valueOf(parcel.readString()),
            parcel.readInt(),
            parcel.readLong(),
            parcel.readLong(),
            Error.valueOf(parcel.readString()))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(status.name)
        parcel.writeInt(progress)
        parcel.writeLong(downloadedBytes)
        parcel.writeLong(totalBytes)
        parcel.writeString(error.name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Result> {
        override fun createFromParcel(parcel: Parcel): Result = Result(parcel)

        override fun newArray(size: Int): Array<Result?> = arrayOfNulls(size)
    }

    override fun toString(): String =
            "Result(id=$id, status=$status, progress=$progress ($downloadedBytes / $totalBytes), error=$error)"
}