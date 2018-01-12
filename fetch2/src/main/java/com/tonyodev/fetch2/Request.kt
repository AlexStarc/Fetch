package com.tonyodev.fetch2

import android.os.Parcel
import android.os.Parcelable
import android.support.v4.util.ArrayMap

data class Request @JvmOverloads constructor(val url: String,
                                             val absoluteFilePath: String,
                                             val name: String = "",
                                             private val headers: MutableMap<String, String> = ArrayMap()) : Parcelable {

    val id: Long
    var groupId: String

    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            ArrayMap()) {
        groupId = parcel.readString()

        val keys = ArrayList<String>()
        val values = ArrayList<String>()

        parcel.readStringList(keys)
        parcel.readStringList(values)

        if (keys.size != values.size) {
            throw IllegalStateException("Wrong parcel received for Request")
        }

        keys.forEachIndexed { index, key -> headers.put(key, values[index]) }
    }

    init {
        if (url.isEmpty()) {
            throw IllegalArgumentException("Url cannot be null or empty")
        }

        if (absoluteFilePath.isEmpty()) {
            throw IllegalArgumentException("AbsoluteFilePath cannot be null or empty")
        }

        this.groupId = ""
        this.id = generateId()
    }

    @Suppress("unused")
    fun putHeader(key: String, value: String?) {
        var realValue = value

        if (realValue == null) realValue = ""

        headers.put(key, realValue)
    }

    private fun generateId(): Long {
        var code1: Long = 0
        var code2: Long = 0

        for (c in url.toCharArray()) {
            code1 = code1 * 31 + c.toLong()
        }

        for (c in absoluteFilePath.toCharArray()) {
            code2 = code2 * 31 + c.toLong()
        }

        return Math.abs(code1 + code2)
    }

    override fun toString(): String = "{\"url\":\"$url\",\"absolutePath\":$absoluteFilePath\"}"

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(absoluteFilePath)
        parcel.writeString(name)

        parcel.writeString(groupId)
        parcel.writeStringList(ArrayList(headers.keys))
        parcel.writeStringList(ArrayList(headers.values))
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Request> {
        override fun createFromParcel(parcel: Parcel): Request = Request(parcel)

        override fun newArray(size: Int): Array<Request?> = arrayOfNulls(size)
    }
}
