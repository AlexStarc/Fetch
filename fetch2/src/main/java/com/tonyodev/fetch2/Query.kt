package com.tonyodev.fetch2

interface Query<in T> {
    fun onResult(result: T?)
}
