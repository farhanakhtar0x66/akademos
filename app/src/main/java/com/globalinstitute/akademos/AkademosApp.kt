package com.globalinstitute.akademos

import android.app.Application
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.reminders.ReminderScheduler

class AkademosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Repo.init(this)
        ReminderScheduler.createChannels(this)
    }
}
