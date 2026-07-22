package com.bandknife.tension

import android.app.Application
import com.bandknife.tension.data.Repository

class TensionApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
    }
}
