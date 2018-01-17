package com.tonyodev.fetch2.database

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.os.Parcel
import android.os.Parcelable
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Status
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2.util.*
import java.util.Date


@Entity(tableName = DownloadDatabase.TABLE_NAME,
        indices = [(Index(value = [DownloadDatabase.COLUMN_FILE], unique = true)),
            (Index(value = [DownloadDatabase.COLUMN_GROUP, DownloadDatabase.COLUMN_STATUS], unique = false))])
class DownloadInfo() : Download, Parcelable {

    @PrimaryKey
    @ColumnInfo(name = DownloadDatabase.COLUMN_ID)
    override var id: Int = 0

    @ColumnInfo(name = DownloadDatabase.COLUMN_NAMESPACE)
    override var namespace: String = ""

    @ColumnInfo(name = DownloadDatabase.COLUMN_NAME)
    override var name: String = ""

    @ColumnInfo(name = DownloadDatabase.COLUMN_URL)
    override var url: String = ""

    @ColumnInfo(name = DownloadDatabase.COLUMN_FILE)
    override var file: String = ""

    @ColumnInfo(name = DownloadDatabase.COLUMN_GROUP)
    override var group: Int = 0

    @ColumnInfo(name = DownloadDatabase.COLUMN_PRIORITY)
    override var priority: Priority = defaultPriority

    @ColumnInfo(name = DownloadDatabase.COLUMN_HEADERS)
    override var headers: MutableMap<String, String> = HashMap(defaultEmptyHeaderMap)

    @ColumnInfo(name = DownloadDatabase.COLUMN_DOWNLOADED)
    override var downloaded: Long = 0L

    @ColumnInfo(name = DownloadDatabase.COLUMN_TOTAL)
    override var total: Long = -1L

    @ColumnInfo(name = DownloadDatabase.COLUMN_STATUS)
    override var status: Status = defaultStatus

    @ColumnInfo(name = DownloadDatabase.COLUMN_ERROR)
    override var error: Error = defaultNoError

    @ColumnInfo(name = DownloadDatabase.COLUMN_NETWORK_TYPE)
    override var networkType: NetworkType = defaultNetworkType

    @ColumnInfo(name = DownloadDatabase.COLUMN_CREATED)
    override var created: Long = Date().time

    override val progress: Int
        get() {
            return calculateProgress(downloaded, total)
        }

    override val request: Request
        get() {
            val request = Request(url, file, name)
            request.groupId = group
            request.headers.putAll(headers)
            request.networkType = networkType
            request.priority = priority
            return request
        }

    constructor(parcel: Parcel) : this() {
        id = parcel.readInt()
        namespace = parcel.readString()
        name = parcel.readString()
        url = parcel.readString()
        file = parcel.readString()
        group = parcel.readInt()
        priority = Priority.valueOf(parcel.readInt())

        val keys = ArrayList<String>()
        val values = ArrayList<String>()

        parcel.readStringList(keys)
        parcel.readStringList(values)

        if (keys.size != values.size) {
            throw IllegalStateException("Wrong parcel received for Request")
        }

        keys.forEachIndexed { index, key -> headers[key] = values[index] }

        downloaded = parcel.readLong()
        total = parcel.readLong()
        status = Status.valueOf(parcel.readInt())
        error = Error.valueOf(parcel.readInt())
        networkType = NetworkType.valueOf(parcel.readInt())
        created = parcel.readLong()
    }

    override fun toString(): String {
        return "DownloadInfo(id:$id, namespace:$namespace, name:$name, url:$url, file:$file, " +
                "group:$group, priority:$priority, headers:$headers, downloaded:$downloaded, " +
                "total:$total, status:$status, error:$error, progress:$progress)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DownloadInfo
        if (id != other.id) return false
        if (namespace != other.namespace) return false
        if (name != other.name) return false
        if (url != other.url) return false
        if (file != other.file) return false
        if (group != other.group) return false
        if (priority != other.priority) return false
        if (headers != other.headers) return false
        if (downloaded != other.downloaded) return false
        if (total != other.total) return false
        if (status != other.status) return false
        if (error != other.error) return false
        if (networkType != other.networkType) return false
        if (created != other.created) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + namespace.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + file.hashCode()
        result = 31 * result + group
        result = 31 * result + priority.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + downloaded.hashCode()
        result = 31 * result + total.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + error.hashCode()
        result = 31 * result + networkType.hashCode()
        result = 31 * result + created.hashCode()
        return result
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(namespace)
        parcel.writeString(name)
        parcel.writeString(url)
        parcel.writeString(file)
        parcel.writeInt(group)
        parcel.writeInt(priority.value)
        parcel.writeStringList(ArrayList(headers.keys))
        parcel.writeStringList(ArrayList(headers.values))
        parcel.writeLong(downloaded)
        parcel.writeLong(total)
        parcel.writeInt(status.value)
        parcel.writeInt(error.value)
        parcel.writeInt(networkType.value)
        parcel.writeLong(created)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<DownloadInfo> {
        override fun createFromParcel(parcel: Parcel): DownloadInfo {
            return DownloadInfo(parcel)
        }

        override fun newArray(size: Int): Array<DownloadInfo?> {
            return arrayOfNulls(size)
        }
    }

}